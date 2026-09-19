package snd.contracts;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The permanent fan-out point between instrumented game code and everything
 * else. Byte Buddy Advice inlined into game methods calls ONLY this class (it
 * lives in the app classloader, so the instrumented bytecode never references
 * the collectible module loader — no reference edge, no classloader leak).
 *
 * frame() runs on the game's render thread once per rendered frame. All work
 * that touches game state goes through {@link #post} onto that thread.
 */
public final class Dispatcher {
    private Dispatcher() {
    }

    private static volatile ModModule module;
    private static volatile Runnable frameHook; // host-owned per-frame work (dev server pump)
    private static final ConcurrentLinkedQueue<Runnable> JOBS = new ConcurrentLinkedQueue<Runnable>();
    private static final Map<String, FrameWait> WAITS = new ConcurrentHashMap<String, FrameWait>();
    private static volatile long frames;
    private static int tickFailures;

    /** A per-frame-evaluated condition registered by the dev server's /wait. */
    public interface FrameWait {
        /** @return true when satisfied; the wait is then removed. */
        boolean poll();

        /** Called (on the render thread) when poll() returned true. */
        void satisfied();
    }

    // The game's own classloader, captured from the render hook. Under the
    // dev launch this is the system loader (dice.jar on -cp); under the
    // shipped packr-style shim the game lives in the shim's own loader and
    // nothing game-typed can link against the system loader.
    private static volatile ClassLoader gameLoader;

    /** The game's classloader, or null before the first pumped frame. */
    public static ClassLoader gameLoader() {
        return gameLoader;
    }

    /** Called from the Advice hook on the game's render method. */
    public static void frame(Object gameInstance) {
        if (gameLoader == null && gameInstance != null) {
            gameLoader = gameInstance.getClass().getClassLoader();
        }
        frame();
    }

    /** Called from the Advice hook on the game's render method. */
    public static void frame() {
        frames++;
        Runnable job;
        while ((job = JOBS.poll()) != null) {
            try {
                job.run();
            } catch (Throwable t) {
                SndLog.error("dev job failed", t);
            }
        }
        if (!WAITS.isEmpty()) {
            Iterator<Map.Entry<String, FrameWait>> it = WAITS.entrySet().iterator();
            while (it.hasNext()) {
                FrameWait w = it.next().getValue();
                boolean done;
                try {
                    done = w.poll();
                } catch (Throwable t) {
                    SndLog.error("frame wait failed; removing", t);
                    it.remove();
                    continue;
                }
                if (done) {
                    it.remove();
                    w.satisfied();
                }
            }
        }
        ModModule m = module;
        if (m != null) {
            try {
                m.tick();
                tickFailures = 0;
            } catch (Throwable t) {
                // A tick that throws would throw every frame; log the first few
                // then go quiet so the log stays readable.
                if (tickFailures++ < 3) {
                    SndLog.error("module tick failed (" + tickFailures + ")", t);
                } else if (tickFailures == 4) {
                    SndLog.error("module tick still failing; suppressing further reports until it succeeds", null);
                }
            }
        }
        Runnable hook = frameHook;
        if (hook != null) {
            try {
                hook.run();
            } catch (Throwable t) {
                SndLog.error("frame hook failed", t);
            }
        }
    }

    public static void post(Runnable job) {
        JOBS.add(job);
    }

    // ---- transient text: banners and error flashes the game draws for
    // ~half a second. Instrumented game methods (showInfo, showError) feed
    // this queue from advice code — advice may reference only this class —
    // and the module drains it each tick to speak. Bounded so a runaway
    // caller can't hoard memory; overflow drops newest and logs. ----

    private static final java.util.concurrent.ConcurrentLinkedQueue<String> TRANSIENT =
            new java.util.concurrent.ConcurrentLinkedQueue<String>();
    private static final int TRANSIENT_CAP = 32;

    public static void transientText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (TRANSIENT.size() >= TRANSIENT_CAP) {
            SndLog.error("transient text queue full; dropping: " + text, null);
            return;
        }
        TRANSIENT.add(text);
    }

    /** The oldest undrained transient line, or null. */
    public static String pollTransientText() {
        return TRANSIENT.poll();
    }

    public static void addWait(String id, FrameWait wait) {
        WAITS.put(id, wait);
    }

    public static void removeWait(String id) {
        WAITS.remove(id);
    }

    public static void swap(ModModule newModule) {
        module = newModule;
    }

    public static ModModule current() {
        return module;
    }

    public static void setFrameHook(Runnable hook) {
        frameHook = hook;
    }

    public static long frameCount() {
        return frames;
    }
}
