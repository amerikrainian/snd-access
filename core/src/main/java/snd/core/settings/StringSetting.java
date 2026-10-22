package snd.core.settings;

import java.util.function.Supplier;

/** A free-form value, saved as written: a saved order of parts. */
public final class StringSetting extends Setting {
    private final String defaultValue;
    private String value;

    public StringSetting(String key, Supplier<String> label, String defaultValue) {
        super(key, label);
        this.defaultValue = defaultValue;
    }

    public String get() {
        return value != null ? value : defaultValue;
    }

    public void set(String v) {
        value = v;
        changed();
    }

    @Override
    String save() {
        return value;
    }

    @Override
    void load(String saved) {
        value = saved;
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
