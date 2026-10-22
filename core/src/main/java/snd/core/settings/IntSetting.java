package snd.core.settings;

import java.util.function.Supplier;

/** A number in [min, max], moved in steps of step; a saved value out of range is clamped. */
public final class IntSetting extends Setting {
    public final int min;
    public final int max;
    public final int step;
    private final int defaultValue;
    private Integer value;

    public IntSetting(String key, Supplier<String> label, int defaultValue, int min, int max, int step) {
        super(key, label);
        if (min > max || step <= 0 || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("bad range for " + key);
        }
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public int get() {
        return value != null ? value : defaultValue;
    }

    public void set(int v) {
        value = Math.max(min, Math.min(max, v));
        changed();
    }

    /** One step up (steps > 0) or down; false at the end it would pass. */
    public boolean adjust(int steps) {
        int next = Math.max(min, Math.min(max, get() + steps * step));
        if (next == get()) {
            return false;
        }
        set(next);
        return true;
    }

    @Override
    String save() {
        return value == null ? null : value.toString();
    }

    @Override
    void load(String saved) {
        try {
            value = Math.max(min, Math.min(max, Integer.parseInt(saved.trim())));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number: " + saved);
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
