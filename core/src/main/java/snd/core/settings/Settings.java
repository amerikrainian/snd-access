package snd.core.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import snd.contracts.SndLog;

/**
 * The settings tree and its saved form: one flat map of paths to values, the
 * explicitly set values only. Entries the tree has no setting for (a setting
 * an older or newer version knew) are kept and written back, so moving
 * between versions loses nothing. Every change is saved at once through the
 * writer the module installs.
 */
public final class Settings {
    public final CategorySetting root;
    private final Map<String, String> unknown = new LinkedHashMap<String, String>();
    private Consumer<Map<String, String>> writer;

    public Settings(CategorySetting root) {
        this.root = root;
        root.owner = this;
    }

    /** Where every change goes, as the whole saved map. Null = nowhere (tests). */
    public void setWriter(Consumer<Map<String, String>> writer) {
        this.writer = writer;
    }

    /** Restore saved values into the built tree. A value a setting cannot hold is logged and left at its default. */
    public void load(Map<String, String> saved) {
        unknown.clear();
        for (Map.Entry<String, String> e : saved.entrySet()) {
            Setting s = find(e.getKey());
            if (s == null || s instanceof CategorySetting) {
                unknown.put(e.getKey(), e.getValue());
                continue;
            }
            try {
                s.load(e.getValue());
            } catch (IllegalArgumentException ex) {
                SndLog.error("settings: " + e.getKey() + " = \"" + e.getValue() + "\" ignored: "
                        + ex.getMessage(), null);
            }
        }
    }

    /** The saved form: every set value in tree order, then the entries the tree does not know. */
    public Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<String, String>();
        collect(root, out);
        for (Map.Entry<String, String> e : unknown.entrySet()) {
            if (!out.containsKey(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static void collect(CategorySetting c, Map<String, String> out) {
        for (Setting s : c.children()) {
            if (s instanceof CategorySetting) {
                collect((CategorySetting) s, out);
            } else {
                String v = s.save();
                if (v != null) {
                    out.put(s.path(), v);
                }
            }
        }
    }

    /** The setting saved under path, or null. */
    public Setting find(String path) {
        return find(root, path);
    }

    private static Setting find(CategorySetting c, String path) {
        for (Setting s : c.children()) {
            String p = s.path();
            if (p.equals(path) && !(s instanceof CategorySetting && !((CategorySetting) s).includeInPath)) {
                return s;
            }
            if (s instanceof CategorySetting) {
                CategorySetting sub = (CategorySetting) s;
                if (!sub.includeInPath || path.startsWith(p + ".")) {
                    Setting found = find(sub, path);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    void changed(Setting s) {
        if (writer == null) {
            return;
        }
        writer.accept(snapshot());
    }
}
