package snd.core.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The order the player arranged a readout's parts in, kept as a hidden
 * comma-separated setting ("order") of the readout's page. What is spoken is
 * that order merged with the readout's default ({@link #merge}): a part added
 * in an update takes its intended place in an order saved before it existed.
 */
public final class PartOrder {
    private final StringSetting saved;
    private final List<String> defaults;

    private PartOrder(StringSetting saved, List<String> defaults) {
        this.saved = saved;
        this.defaults = Collections.unmodifiableList(new ArrayList<String>(defaults));
    }

    /** Give page an arranged order of these parts, default first to last. */
    public static PartOrder attach(CategorySetting page, List<String> defaults) {
        StringSetting saved = page.add(new StringSetting("order", Setting.loc("settings.order"), null));
        saved.hidden = true;
        PartOrder order = new PartOrder(saved, defaults);
        page.partOrder = order;
        return order;
    }

    public List<String> defaults() {
        return defaults;
    }

    /** The parts in speaking order. */
    public List<String> order() {
        String csv = saved.get();
        if (csv == null || csv.isEmpty()) {
            return defaults;
        }
        return merge(Arrays.asList(csv.split(",")), defaults);
    }

    /**
     * Move part one place earlier (by -1) or later (by 1). False when it is
     * already first or last, or is not a part of this readout.
     */
    public boolean move(String part, int by) {
        List<String> order = new ArrayList<String>(order());
        int at = order.indexOf(part);
        int to = at + by;
        if (at < 0 || to < 0 || to >= order.size()) {
            return false;
        }
        Collections.swap(order, at, to);
        StringBuilder sb = new StringBuilder();
        for (String p : order) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(p);
        }
        saved.set(sb.toString());
        return true;
    }

    /**
     * A saved order merged with the current default one: the saved parts that
     * still exist, in their saved order, and each default part the save lacks
     * placed right after its nearest earlier default neighbour already
     * placed, or first when none is.
     */
    public static List<String> merge(List<String> saved, List<String> defaults) {
        List<String> result = new ArrayList<String>();
        for (String raw : saved) {
            String p = raw.trim();
            if (defaults.contains(p) && !result.contains(p)) {
                result.add(p);
            }
        }
        for (int i = 0; i < defaults.size(); i++) {
            String p = defaults.get(i);
            if (result.contains(p)) {
                continue;
            }
            int at = 0;
            for (int j = i - 1; j >= 0; j--) {
                int prev = result.indexOf(defaults.get(j));
                if (prev >= 0) {
                    at = prev + 1;
                    break;
                }
            }
            result.add(at, p);
        }
        return result;
    }
}
