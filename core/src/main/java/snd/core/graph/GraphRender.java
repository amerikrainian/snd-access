package snd.core.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One built snapshot of a graph: the nodes (keyed by structural identity),
 * their order of declaration, and where focus starts when there is no prior
 * position. Rebuilt per operation and thrown away — live state belongs in the
 * node callbacks, not here.
 */
public final class GraphRender {
    public ControlId startKey;
    public final Map<ControlId, GraphNode> nodes = new HashMap<ControlId, GraphNode>();

    /** Declaration order — drives stop/region cycling and type-ahead scan order. */
    public final List<GraphNode> order = new ArrayList<GraphNode>();

    public GraphNode nodeAt(ControlId key) {
        return key == null ? null : nodes.get(key);
    }
}
