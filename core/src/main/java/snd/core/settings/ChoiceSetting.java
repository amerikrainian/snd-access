package snd.core.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** One of a fixed list of options, saved by the option's key. */
public final class ChoiceSetting extends Setting {
    /** One option: its saved key and its spoken label. */
    public static final class Option {
        public final String key;
        private final Supplier<String> label;

        public Option(String key, Supplier<String> label) {
            this.key = key;
            this.label = label;
        }

        public String label() {
            return label.get();
        }
    }

    private final List<Option> options;
    private final String defaultKey;
    private String value;

    public ChoiceSetting(String key, Supplier<String> label, List<Option> options, String defaultKey) {
        super(key, label);
        this.options = Collections.unmodifiableList(new ArrayList<Option>(options));
        if (indexOf(defaultKey) < 0) {
            throw new IllegalArgumentException(key + ": default " + defaultKey + " is not an option");
        }
        this.defaultKey = defaultKey;
    }

    public List<Option> options() {
        return options;
    }

    public String get() {
        return value != null ? value : defaultKey;
    }

    public int index() {
        return indexOf(get());
    }

    public Option chosen() {
        return options.get(index());
    }

    public void set(String optionKey) {
        if (indexOf(optionKey) < 0) {
            throw new IllegalArgumentException(key + ": " + optionKey + " is not an option");
        }
        value = optionKey;
        changed();
    }

    private int indexOf(String optionKey) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).key.equals(optionKey)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    String save() {
        return value;
    }

    @Override
    void load(String saved) {
        if (indexOf(saved) < 0) {
            throw new IllegalArgumentException("not an option: " + saved);
        }
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
