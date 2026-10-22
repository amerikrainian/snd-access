package snd.core.graph;

/**
 * The well-known announcement-part kinds. A part's kind is its identity for
 * readout ordering, node-over-type overriding, and the player's per-part
 * announcement settings; any other string names a part of a readout the
 * module declares (a combatant's "hp").
 */
public final class AnnouncementKinds {
    private AnnouncementKinds() {
    }

    /**
     * A what's-happening flag spoken BEFORE the label ("used", "valid
     * target", "defeated"): scrolling a list, the state arrives first so a
     * non-candidate can be skipped without hearing the rest.
     */
    public static final String STATE = "state";
    public static final String LABEL = "label";
    public static final String ROLE = "role";
    public static final String VALUE = "value";
    public static final String SELECTED = "selected";
    public static final String ENABLED = "enabled";
    public static final String TOOLTIP = "tooltip";
    /** An expandable group's expanded/collapsed word, supplied by the engine. */
    public static final String EXPANDED = "expanded";
    /** "3 of 6": supplied by the engine from the sibling count, unless a node declares its own. */
    public static final String POSITION = "position";
}
