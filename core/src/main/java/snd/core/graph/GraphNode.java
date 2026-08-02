package snd.core.graph;

import java.util.EnumMap;
import java.util.Map;

/**
 * A control: identity, behaviors, directional transitions, and structural
 * metadata (its parent chain, tab-stop and region membership, expandability).
 */
public final class GraphNode {
    public ControlId id;
    public NodeVtable vtable;
    public final Map<GraphDir, Transition> transitions = new EnumMap<GraphDir, Transition>(GraphDir.class);

    /**
     * The node's structural parent within THIS render, or null at screen
     * level. The parent chain IS the presentation hierarchy: the announcer
     * prefix-diffs old/new chains by identity, so entering a group reads its
     * levels outermost-first and descending from a group onto its own child
     * re-announces nothing. A parent may be non-focusable pure structure (a
     * labeled panel) or a real control (a tree group header).
     */
    public GraphNode parent;

    /**
     * False for a pure-structure parent (a labeled panel): it exists only on
     * parent chains for announcements — never navigable, never in nodes/order.
     */
    public boolean focusable = true;

    /** This node is a group that can expand/collapse (a tree section header). */
    public boolean expandable;

    /** An expandable group's state AT THIS RENDER (stamped by the builder). */
    public boolean expanded;

    /**
     * The Tab-stop this node belongs to. Nodes sharing a stopKey form one
     * stop; Tab cycles stops in first-appearance order, landing on the stop's
     * remembered position.
     */
    public Object stopKey;

    /** The region (within a stop), or null. Region jumps cycle these. */
    public Object regionKey;

    /**
     * Auto-stamped sibling position (1-based) and count, from the builder:
     * menu-mode nodes grouped by (parent, stop). 0 = none (single sibling,
     * raw/grid nodes).
     */
    public int positionIndex;
    public int positionCount;

    /**
     * On a parent (context/group) node: its direct children get NO auto
     * position — for log-like streams where "37 of 200" is noise.
     */
    public boolean suppressChildPositions;
}
