package snd.core.graph;

import java.util.List;
import java.util.function.Supplier;

/**
 * A CONTROL TYPE — "button", "toggle", "slider" — as a registry value rather
 * than a class. A type owns the speak ORDER of its announcement kinds and the
 * parts COMMON to every control of the type (the role word); nodes contribute
 * their specific parts, overriding a common part of the same kind. The user's
 * per-type announcement settings key off {@link #key}.
 */
public final class ControlType {
    /** Stable settings/registry key ("button", "toggle", "slider"). */
    public final String key;

    /**
     * The announcement kinds in speak order; parts with unknown/absent kinds
     * append after, in declaration order.
     */
    public final String[] order;

    /**
     * The parts every control of this type shares (the role word), resolved
     * per compose. Null = none.
     */
    public final Supplier<List<NodeAnnouncement>> common;

    public ControlType(String key, String[] order, Supplier<List<NodeAnnouncement>> common) {
        this.key = key;
        this.order = order;
        this.common = common;
    }
}
