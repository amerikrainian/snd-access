package snd.host;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

import snd.core.Dispatcher;
import snd.core.HostServices;
import snd.core.ModModule;
import snd.core.SndLog;

/**
 * Loads snd.module.SndModule from the jar named by -Dsnd.module into a fresh
 * child URLClassLoader per (re)load. The jar is copied to a temp file first so
 * the original never locks on Windows and `gradle build` can always overwrite
 * it. Ordering is load-new-then-dispose-old: a broken build leaves the running
 * module untouched. Must be called on the render thread.
 */
public final class ModuleLoader {
    private final HostServices services;
    private final Path moduleJar;
    private URLClassLoader loader;
    private ModModule module;
    private Path tempCopy;
    private int generation;
    private WeakReference<ClassLoader> lastUnloaded;
    private String lastStatus = "never loaded";

    ModuleLoader(HostServices services) {
        this.services = services;
        String prop = System.getProperty("snd.module");
        this.moduleJar = prop != null ? Paths.get(prop) : null;
    }

    public synchronized String reload() {
        if (moduleJar == null) {
            return lastStatus = "no module: -Dsnd.module not set";
        }
        if (!Files.isRegularFile(moduleJar)) {
            return lastStatus = "no module: " + moduleJar + " does not exist";
        }
        URLClassLoader newLoader = null;
        Path newTemp = null;
        try {
            newTemp = Files.createTempFile("snd-module-gen" + (generation + 1) + "-", ".jar");
            Files.copy(moduleJar, newTemp, StandardCopyOption.REPLACE_EXISTING);
            // Parent through the game's loader so module code links against
            // the game wherever the shim put it; host/core still resolve.
            newLoader = new URLClassLoader(new URL[]{newTemp.toUri().toURL()},
                    new BridgeLoader(Dispatcher.gameLoader(), Host.class.getClassLoader()));
            Class<?> cls = Class.forName("snd.module.SndModule", true, newLoader);
            if (cls.getClassLoader() != newLoader) {
                throw new IllegalStateException("snd.module.SndModule resolved from the parent loader ("
                        + cls.getClassLoader() + ") — is the module jar on the app classpath? It must not be.");
            }
            ModModule newModule = (ModModule) cls.getDeclaredConstructor().newInstance();
            generation++; // load() must see its own generation; undone if load throws
            try {
                newModule.load(services);
            } catch (Throwable t) {
                generation--;
                throw t;
            }

            ModModule oldModule = module;
            URLClassLoader oldLoader = loader;
            Path oldTemp = tempCopy;
            Dispatcher.swap(newModule);
            module = newModule;
            loader = newLoader;
            tempCopy = newTemp;

            String canary = "first load";
            if (oldModule != null) {
                try {
                    oldModule.dispose();
                } catch (Throwable t) {
                    SndLog.error("old module dispose failed", t);
                }
                try {
                    oldLoader.close();
                } catch (Throwable t) {
                    SndLog.error("old module loader close failed", t);
                }
                if (oldTemp != null) {
                    oldTemp.toFile().deleteOnExit();
                    // best-effort now; deleteOnExit covers a lingering lock
                    oldTemp.toFile().delete();
                }
                lastUnloaded = new WeakReference<ClassLoader>(oldLoader);
                // Don't GC-probe here: the collector rarely runs within the
                // same call and a false "still reachable" alarms. /module
                // re-probes and reports truthfully.
                canary = "pending (query /module)";
            }
            lastStatus = "generation " + generation + " loaded from " + moduleJar
                    + " (jar " + mtime(moduleJar) + "), old loader: " + canary;
            SndLog.info(lastStatus);
            return lastStatus;
        } catch (Throwable t) {
            SndLog.error("module reload failed; keeping previous module", t);
            if (newLoader != null) {
                try {
                    newLoader.close();
                } catch (Throwable ignored) {
                    // already failing; the temp copy is cleaned below
                }
            }
            if (newTemp != null) {
                newTemp.toFile().delete();
            }
            return lastStatus = "reload FAILED: " + t + " (previous module still live)";
        }
    }

    /** GC-probes whether the previously unloaded classloader was collected. */
    synchronized String canaryStatus() {
        if (lastUnloaded == null) {
            return "n/a";
        }
        System.gc();
        return lastUnloaded.get() == null ? "collected" : "STILL REACHABLE (leak?)";
    }

    public synchronized String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("module jar: ").append(moduleJar).append('\n');
        if (moduleJar != null && Files.isRegularFile(moduleJar)) {
            sb.append("jar mtime: ").append(mtime(moduleJar)).append('\n');
        }
        sb.append("generation: ").append(generation).append('\n');
        sb.append("module: ").append(module != null ? module.getClass().getName() : "none").append('\n');
        sb.append("last status: ").append(lastStatus).append('\n');
        sb.append("old loader canary: ").append(canaryStatus()).append('\n');
        sb.append("frames pumped: ").append(Dispatcher.frameCount()).append('\n');
        return sb.toString();
    }

    public synchronized int generation() {
        return generation;
    }

    private static String mtime(Path p) {
        try {
            File f = p.toFile();
            long age = (System.currentTimeMillis() - f.lastModified()) / 1000;
            return new SimpleDateFormat("HH:mm:ss").format(new Date(f.lastModified())) + " (" + age + "s ago)";
        } catch (Throwable t) {
            return "?";
        }
    }
}
