package snd.contracts.dev;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A static hand-off table between /eval snippets and the host. JShell's local
 * execution shares this JVM and resolves this class through the app
 * classloader, so an object a snippet puts here is the same object the host
 * reads — the mechanism behind /wait's per-frame-evaluated conditions.
 */
public final class Bridge {
    private Bridge() {
    }

    private static final Map<String, Object> MAP = new ConcurrentHashMap<String, Object>();

    public static void put(String key, Object value) {
        MAP.put(key, value);
    }

    public static Object get(String key) {
        return MAP.get(key);
    }

    public static Object remove(String key) {
        return MAP.remove(key);
    }
}
