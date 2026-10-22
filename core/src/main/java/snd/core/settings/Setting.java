package snd.core.settings;

import java.util.function.Supplier;

import snd.core.loc.Loc;

/**
 * One node of the settings tree: a value the player can change, or a
 * {@link CategorySetting} grouping others. Its saved name is its
 * {@link #path()}, the keys of its ancestors and itself joined with dots
 * ("announcements.hp.enabled"); a category that only groups the menu stays
 * out of the path. A value is saved only once the player sets it, so a
 * default can change in an update and reach everyone who never touched it.
 */
public abstract class Setting {
    /** This node's part of the saved path. */
    public final String key;

    private final Supplier<String> label;
    CategorySetting parent;

    /** Menu order among siblings; ties fall back to the label. */
    public int sortPriority;

    /** Kept in the tree (and the file) but not shown in the menu. */
    public boolean hidden;

    protected Setting(String key, Supplier<String> label) {
        if (key == null || key.isEmpty() || key.indexOf('.') >= 0) {
            throw new IllegalArgumentException("bad setting key: " + key);
        }
        this.key = key;
        this.label = label;
    }

    /** A label read from the mod's "ui" locale table at speak time. */
    public static Supplier<String> loc(final String uiKey) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", uiKey);
            }
        };
    }

    public String label() {
        return label.get();
    }

    public CategorySetting parent() {
        return parent;
    }

    public String path() {
        StringBuilder sb = new StringBuilder(key);
        for (CategorySetting c = parent; c != null; c = c.parent) {
            if (c.includeInPath && c.parent != null) {
                sb.insert(0, '.').insert(0, c.key);
            }
        }
        return sb.toString();
    }

    /** The saved form of an explicitly set value, or null when the value is the default (or inherited). */
    abstract String save();

    /** Restore a saved value; throws IllegalArgumentException on a value this setting cannot hold. */
    abstract void load(String saved);

    /** Back to the default, or to inheriting. Saves. */
    public abstract void reset();

    /** Whether the player set this value (it is saved). */
    public abstract boolean isSet();

    protected final void changed() {
        for (CategorySetting c = parent; c != null; c = c.parent) {
            if (c.owner != null) {
                c.owner.changed(this);
                return;
            }
        }
    }
}
