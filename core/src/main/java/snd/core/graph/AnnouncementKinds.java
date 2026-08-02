package snd.core.graph;

/**
 * The well-known announcement-part kinds. A part's kind is its identity for
 * control-type ordering, node-over-type overriding, and the user's per-kind
 * announcement settings.
 */
public final class AnnouncementKinds {
    private AnnouncementKinds() {
    }

    public static final String LABEL = "label";
    public static final String ROLE = "role";
    public static final String VALUE = "value";
    public static final String SELECTED = "selected";
    public static final String ENABLED = "enabled";
    public static final String TOOLTIP = "tooltip";
    public static final String POSITION = "position";
}
