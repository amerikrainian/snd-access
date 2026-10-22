package snd.core.settings;

import java.util.function.Supplier;

/**
 * An on/off setting. One made with {@link #inheriting} has no default of its
 * own: until the player sets it, it reads its fallback (a readout's "speak hp"
 * follows the global "speak hp").
 */
public final class BoolSetting extends Setting {
    private final boolean defaultValue;
    private final BoolSetting fallback;
    private Boolean value;

    public BoolSetting(String key, Supplier<String> label, boolean defaultValue) {
        super(key, label);
        this.defaultValue = defaultValue;
        this.fallback = null;
    }

    private BoolSetting(String key, Supplier<String> label, BoolSetting fallback) {
        super(key, label);
        this.defaultValue = false;
        this.fallback = fallback;
    }

    public static BoolSetting inheriting(String key, Supplier<String> label, BoolSetting fallback) {
        if (fallback == null) {
            throw new IllegalArgumentException("inheriting setting " + key + " needs a fallback");
        }
        return new BoolSetting(key, label, fallback);
    }

    public boolean get() {
        if (value != null) {
            return value;
        }
        return fallback != null ? fallback.get() : defaultValue;
    }

    public void set(boolean v) {
        value = v;
        changed();
    }

    /**
     * The menu's toggle: flip the value. An inheriting setting flipped back to
     * what its fallback says inherits again, so a later change to the fallback
     * still reaches it.
     */
    public void toggle() {
        boolean next = !get();
        if (fallback != null && next == fallback.get()) {
            value = null;
            changed();
        } else {
            set(next);
        }
    }

    public boolean inherits() {
        return fallback != null && value == null;
    }

    public BoolSetting fallback() {
        return fallback;
    }

    @Override
    String save() {
        return value == null ? null : value.toString();
    }

    @Override
    void load(String saved) {
        if ("true".equals(saved)) {
            value = Boolean.TRUE;
        } else if ("false".equals(saved)) {
            value = Boolean.FALSE;
        } else {
            throw new IllegalArgumentException("not true or false: " + saved);
        }
    }

    @Override
    public void reset() {
        value = null;
        changed();
    }

    @Override
    public boolean isSet() {
        return value != null;
    }
}
