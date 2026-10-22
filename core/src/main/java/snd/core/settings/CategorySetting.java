package snd.core.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * A group of settings: a page of the menu. One made with includeInPath false
 * groups the menu only, and its children save as if they sat in its parent.
 */
public final class CategorySetting extends Setting {
    final boolean includeInPath;
    private final List<Setting> children = new ArrayList<Setting>();
    Settings owner;

    /**
     * A page whose children are the parts of one readout, spoken in the
     * order the player arranges them: each child is a category with its
     * own settings, and the page offers moving them and a reset.
     */
    public PartOrder partOrder;

    public CategorySetting(String key, Supplier<String> label) {
        this(key, label, true);
    }

    public CategorySetting(String key, Supplier<String> label, boolean includeInPath) {
        super(key, label);
        this.includeInPath = includeInPath;
    }

    public <T extends Setting> T add(T child) {
        if (child.parent != null) {
            throw new IllegalStateException(child.key + " already sits in " + child.parent.key);
        }
        for (Setting s : children) {
            if (s.key.equals(child.key)) {
                throw new IllegalArgumentException(key + " already has " + child.key);
            }
        }
        child.parent = this;
        children.add(child);
        return child;
    }

    public List<Setting> children() {
        return Collections.unmodifiableList(children);
    }

    /** The child with this key, or null. */
    public Setting child(String childKey) {
        for (Setting s : children) {
            if (s.key.equals(childKey)) {
                return s;
            }
        }
        return null;
    }

    /** The child category with this key, created (labelled label) when missing. */
    public CategorySetting category(String childKey, Supplier<String> label) {
        Setting s = child(childKey);
        if (s instanceof CategorySetting) {
            return (CategorySetting) s;
        }
        if (s != null) {
            throw new IllegalStateException(childKey + " in " + key + " is not a category");
        }
        return add(new CategorySetting(childKey, label));
    }

    /** The children the menu shows, in its order: by sort priority, then label. */
    public List<Setting> shown() {
        List<Setting> shown = new ArrayList<Setting>();
        for (Setting s : children) {
            if (!s.hidden) {
                shown.add(s);
            }
        }
        if (partOrder != null) {
            final List<String> order = partOrder.order();
            Collections.sort(shown, new Comparator<Setting>() {
                @Override
                public int compare(Setting a, Setting b) {
                    return Integer.compare(rank(order, a.key), rank(order, b.key));
                }
            });
            return shown;
        }
        Collections.sort(shown, new Comparator<Setting>() {
            @Override
            public int compare(Setting a, Setting b) {
                int c = Integer.compare(a.sortPriority, b.sortPriority);
                return c != 0 ? c : a.label().compareToIgnoreCase(b.label());
            }
        });
        return shown;
    }

    // A page's own settings (an event's "announce") lead its parts.
    private static int rank(List<String> order, String k) {
        return order.indexOf(k);
    }

    /** Every setting below this one back to its default (a part order is one of them). */
    @Override
    public void reset() {
        for (Setting s : children) {
            s.reset();
        }
    }

    @Override
    public boolean isSet() {
        for (Setting s : children) {
            if (s.isSet()) {
                return true;
            }
        }
        return false;
    }

    @Override
    String save() {
        return null;
    }

    @Override
    void load(String saved) {
        throw new IllegalArgumentException("a category holds no value");
    }
}
