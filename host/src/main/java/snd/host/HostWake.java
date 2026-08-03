package snd.host;

import java.lang.reflect.Method;

import snd.core.Dispatcher;
import snd.core.SndLog;

/**
 * Wakes the game's possibly-idle non-continuous render loop so posted dev
 * jobs run. Purely reflective against the game's own classloader — the host
 * must never link against game classes (the shipped shim loads the game
 * outside the system loader).
 */
public final class HostWake {
    private HostWake() {
    }

    private static volatile Method requestRendering;
    private static volatile Object graphics;
    private static boolean warned;

    public static void requestRender() {
        try {
            if (requestRendering == null) {
                ClassLoader loader = Dispatcher.gameLoader();
                if (loader == null) {
                    return; // pre-boot; the first natural frame captures it
                }
                Class<?> gdx = Class.forName("com.badlogic.gdx.Gdx", true, loader);
                graphics = gdx.getField("graphics").get(null);
                if (graphics == null) {
                    return;
                }
                requestRendering = graphics.getClass().getMethod("requestRendering");
                requestRendering.setAccessible(true);
            }
            requestRendering.invoke(graphics);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                SndLog.error("render wake failed (dev jobs may stall while idle)", t);
            }
        }
    }
}
