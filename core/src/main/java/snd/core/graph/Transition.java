package snd.core.graph;

/**
 * A directed edge to another node, with an optional spoken transition line (a
 * "lane change"). Plain data; contextual announcements are composed from node
 * metadata by the announcer, not per-edge closures (GC discipline).
 */
public final class Transition {
    public final ControlId destination;
    /** Spoken only while crossing this edge; null = silent edge. */
    public final String label;

    public Transition(ControlId destination) {
        this(destination, null);
    }

    public Transition(ControlId destination, String label) {
        this.destination = destination;
        this.label = label;
    }
}
