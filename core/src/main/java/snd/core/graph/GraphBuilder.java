package snd.core.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Builds a {@link GraphRender}. Two construction styles, freely mixable in one
 * build:
 *
 * <p><b>Menu mode</b> — rows of controls, wired automatically: left/right
 * within a row, up/down between consecutive rows (two rows sharing a non-null
 * row key get column navigation — up/down preserves the position instead of
 * snapping to the first item). Items added outside an explicit row become
 * single-item rows (a plain vertical menu).</p>
 *
 * <p><b>Raw mode</b> — {@link #addNode} + {@link #connect} for arbitrary
 * topologies.</p>
 *
 * <p>Orthogonal to both: {@link #beginStop} groups nodes into Tab-stops
 * (arrows never cross a stop; Tab cycles them), {@link #setRegion} tags nodes
 * for region jumps, and the PARENT STACK builds the presentation hierarchy:
 * {@link #pushContext} pushes a non-focusable structural level, while
 * {@link #beginGroup} pushes a focusable, EXPANDABLE group header whose
 * children only emit while it's expanded — expansion state lives in the
 * persistent set the builder is constructed with, so screens hold no tree
 * state of their own. A collapsed ancestor suppresses everything beneath it.</p>
 */
public final class GraphBuilder {
    private final Set<ControlId> expansion; // persistent expanded-group set (null = all explicit)

    public GraphBuilder() {
        this(null);
    }

    public GraphBuilder(Set<ControlId> expansion) {
        this.expansion = expansion;
    }

    private static final class Row {
        final List<GraphNode> items = new ArrayList<GraphNode>();
        Object key;
        Object stopKey;
    }

    private static final class RawEdge {
        ControlId from;
        GraphDir dir;
        ControlId to;
        String label;
    }

    // Menu mode.
    private final List<Row> rows = new ArrayList<Row>();
    private Row currentRow;

    // Raw mode.
    private final List<GraphNode> rawNodes = new ArrayList<GraphNode>();
    private final List<RawEdge> rawEdges = new ArrayList<RawEdge>();

    // Every node in DECLARATION order regardless of mode — the render's node
    // order (and so the Tab-stop cycle) must interleave menu rows and raw
    // nodes as the screen declared them.
    private final List<GraphNode> declared = new ArrayList<GraphNode>();

    // The menu row each menu-mode node belongs to (absent for raw nodes) —
    // for stitching the vertical gap where a stop mixes menu rows with raw
    // content (a sheet below filter controls).
    private final Map<GraphNode, Row> rowOf = new HashMap<GraphNode, Row>();

    // Shared.
    private final Set<ControlId> ids = new HashSet<ControlId>();
    private ControlId start;

    // Stop / region / parent state applied to nodes as they are added.
    private Object stopKey = autoStopKey(0);
    private int stopAuto = 1;
    private Object regionKey;

    // The parent stack: structural levels (pushContext) and group headers
    // (beginGroup). A frame whose group is collapsed suppresses every
    // declaration beneath it (the stack stays balanced regardless).
    private static final class ParentFrame {
        GraphNode node;     // the parent node (non-focusable context, or the group header)
        boolean suppressed; // this frame's subtree is swallowed
    }

    private final List<ParentFrame> parents = new ArrayList<ParentFrame>();

    private GraphNode currentParent() {
        return parents.isEmpty() ? null : parents.get(parents.size() - 1).node;
    }

    private boolean suppressed() {
        return !parents.isEmpty() && parents.get(parents.size() - 1).suppressed;
    }

    private static Object autoStopKey(int index) {
        return "stop#" + index;
    }

    // ---- stops / regions ----

    /**
     * Start a new Tab-stop; nodes added from here belong to it. The key must
     * be stable across rebuilds (it keys the stop's remembered position);
     * null auto-assigns by index, which is stable when the screen builds its
     * stops in a fixed order.
     */
    public GraphBuilder beginStop() {
        return beginStop(null);
    }

    public GraphBuilder beginStop(Object key) {
        if (currentRow != null) {
            throw new IllegalStateException("Cannot begin a stop inside an open row");
        }
        stopKey = key != null ? key : autoStopKey(stopAuto);
        stopAuto++;
        regionKey = null; // regions are per-stop
        return this;
    }

    /**
     * Tag nodes added from here with a region (jump target) within the
     * current stop; null clears. Region keys must be stable across rebuilds.
     */
    public GraphBuilder setRegion(Object key) {
        regionKey = key;
        return this;
    }

    // ---- the parent stack: contexts + groups ----

    /**
     * Push one NON-FOCUSABLE level of presentation hierarchy ("Difficulty
     * settings", "list") onto nodes added from here — pure structure: never
     * navigable, announced when focus enters from outside. Close with
     * {@link #popContext}.
     */
    public GraphBuilder pushContext(String label) {
        return pushContext(label, null, true);
    }

    public GraphBuilder pushContext(String label, String role) {
        return pushContext(label, role, true);
    }

    public GraphBuilder pushContext(String label, String role, boolean positions) {
        GraphNode parent = currentParent();
        List<NodeAnnouncement> anns = new ArrayList<NodeAnnouncement>();
        anns.add(NodeAnnouncement.of(label));
        if (role != null && !role.isEmpty()) {
            anns.add(NodeAnnouncement.of(role));
        }
        // Stable synthetic identity (label-pathed) so cross-render chain diffs match up.
        return pushFrame(ControlId.structural("ctx:" + (parent != null ? parent.id.structuralKey : "") + "/" + label),
                anns, positions);
    }

    /**
     * Push a CONTAINER: a context whose identity is key, not its label — the
     * unit or object a group of controls is about (a hero's row of slots, an
     * offer's sheet). Focus entering it from another container reads the
     * label, resolved live, before the landing control; moving inside it
     * reads the control alone; landing on a control whose label starts with
     * the container's skips the container's. Two containers with one label
     * stay two, and a label that changes keeps its container. Close with
     * {@link #popContext}.
     */
    public GraphBuilder pushContext(Object key, Supplier<String> label) {
        if (key == null) {
            throw new IllegalArgumentException("A container needs a key");
        }
        GraphNode parent = currentParent();
        return pushFrame(ControlId.structural(CompositeKey.of("ctx", parent != null ? parent.id.structuralKey : null, key)),
                Arrays.asList(new NodeAnnouncement(label)), true);
    }

    private GraphBuilder pushFrame(ControlId id, List<NodeAnnouncement> anns, boolean positions) {
        GraphNode node = new GraphNode();
        node.id = id;
        node.vtable = new NodeVtable();
        node.vtable.announcements = anns;
        node.parent = currentParent();
        node.focusable = false;
        node.suppressChildPositions = !positions;
        ParentFrame frame = new ParentFrame();
        frame.node = node;
        frame.suppressed = suppressed();
        parents.add(frame);
        return this;
    }

    public GraphBuilder popContext() {
        if (parents.isEmpty()) {
            throw new IllegalStateException("No context/group to pop");
        }
        parents.remove(parents.size() - 1);
        return this;
    }

    /**
     * Push a FOCUSABLE, expandable group header (a tree section): the header
     * emits as a navigable node here, and the children declared before
     * {@link #endGroup} emit only while the group is expanded. Expansion
     * state: the explicit value when given, else the persistent expansion set,
     * else defaultExpanded. The engine's tree operations expand/collapse via
     * the vtable's onExpand/onCollapse overrides when set, else by mutating
     * the persistent set.
     */
    public GraphBuilder beginGroup(ControlId id, NodeVtable vtable) {
        return beginGroup(id, vtable, null, false);
    }

    public GraphBuilder beginGroup(ControlId id, NodeVtable vtable, Boolean expanded, boolean defaultExpanded) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (currentRow != null) {
            throw new IllegalStateException("Cannot begin a group inside an open row");
        }
        boolean isExpanded = expanded != null ? expanded
                : expansion != null ? expansion.contains(id) : defaultExpanded;

        GraphNode header = null;
        if (!suppressed()) {
            header = makeNode(id, vtable);
            header.expandable = true;
            header.expanded = isExpanded;
            Row row = new Row();
            row.stopKey = stopKey;
            row.items.add(header);
            rows.add(row);
            rowOf.put(header, row);
        }
        ParentFrame frame = new ParentFrame();
        // Suppressed subtree: keep chaining from the outer parent so the stack stays coherent.
        frame.node = header != null ? header : currentParent();
        frame.suppressed = suppressed() || !isExpanded;
        parents.add(frame);
        return this;
    }

    public GraphBuilder endGroup() {
        return popContext();
    }

    /**
     * Whether a group id is expanded in the persistent set — for screens that
     * must avoid even BUILDING a collapsed group's children (a lazy hierarchy).
     */
    public boolean isExpanded(ControlId id) {
        return expansion != null && id != null && expansion.contains(id);
    }

    /** Focus starts here when the graph has no prior position (defaults to the first node). */
    public GraphBuilder setStart(ControlId id) {
        start = id;
        return this;
    }

    // ---- menu mode ----

    /**
     * Open a horizontal row. Rows sharing a non-null rowKey with the row
     * above/below get column-preserving vertical navigation.
     */
    public GraphBuilder startRow() {
        return startRow(null);
    }

    public GraphBuilder startRow(Object rowKey) {
        if (currentRow != null) {
            throw new IllegalStateException("Cannot start a row while another is open");
        }
        currentRow = new Row();
        currentRow.key = rowKey;
        currentRow.stopKey = stopKey;
        return this;
    }

    public GraphBuilder endRow() {
        if (currentRow == null) {
            throw new IllegalStateException("No row to end");
        }
        if (currentRow.items.isEmpty() && !suppressed()) {
            throw new IllegalStateException("Row cannot be empty");
        }
        if (!currentRow.items.isEmpty()) {
            rows.add(currentRow);
        }
        currentRow = null;
        return this;
    }

    /**
     * Add a control — into the open row, or as its own single-item row. A
     * no-op inside a collapsed group's subtree.
     */
    public GraphBuilder addItem(ControlId id, NodeVtable vtable) {
        if (suppressed()) {
            return this;
        }
        GraphNode node = makeNode(id, vtable);
        if (currentRow != null) {
            currentRow.items.add(node);
            rowOf.put(node, currentRow);
        } else {
            Row row = new Row();
            row.stopKey = stopKey;
            row.items.add(node);
            rows.add(row);
            rowOf.put(node, row);
        }
        return this;
    }

    /** Add a read-only line (label only; no actions). */
    public GraphBuilder addLabel(ControlId id, Supplier<String> label) {
        NodeVtable vt = new NodeVtable();
        vt.announcements = Arrays.asList(new NodeAnnouncement(label));
        return addItem(id, vt);
    }

    // ---- raw mode ----

    /**
     * Add a node with no automatic wiring (raw mode; wire with
     * {@link #connect}). A no-op inside a collapsed group's subtree.
     */
    public GraphBuilder addNode(ControlId id, NodeVtable vtable) {
        if (suppressed()) {
            return this;
        }
        rawNodes.add(makeNode(id, vtable));
        return this;
    }

    /**
     * Directed edge from → to, with an optional spoken transition line.
     * Edges to/from undeclared nodes are dropped at build.
     */
    public GraphBuilder connect(ControlId from, GraphDir dir, ControlId to) {
        return connect(from, dir, to, null);
    }

    public GraphBuilder connect(ControlId from, GraphDir dir, ControlId to, String label) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("connect endpoints must not be null");
        }
        RawEdge e = new RawEdge();
        e.from = from;
        e.dir = dir;
        e.to = to;
        e.label = label;
        rawEdges.add(e);
        return this;
    }

    private GraphNode makeNode(ControlId id, NodeVtable vtable) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (vtable == null || vtable.announcements == null || vtable.announcements.isEmpty()) {
            throw new IllegalArgumentException("A control must have at least one announcement");
        }
        if (!ids.add(id)) {
            throw new IllegalStateException("Duplicate control id: " + id);
        }
        GraphNode node = new GraphNode();
        node.id = id;
        node.vtable = vtable;
        node.parent = currentParent();
        node.stopKey = stopKey;
        node.regionKey = regionKey;
        declared.add(node);
        return node;
    }

    // ---- build ----

    /**
     * Finalize into a render, or null when nothing was declared (treat as
     * "closed"). Menu rows and raw nodes/edges may coexist in one build.
     */
    public GraphRender build() {
        if (currentRow != null) {
            throw new IllegalStateException("Unclosed row - call endRow()");
        }
        if (rawNodes.isEmpty() && rows.isEmpty()) {
            return null;
        }

        GraphRender render = new GraphRender();
        for (GraphNode node : declared) {
            render.nodes.put(node.id, node);
            render.order.add(node);
        }

        wireMenuEdges();
        for (RawEdge e : rawEdges) {
            if (render.nodes.containsKey(e.from) && render.nodes.containsKey(e.to)) {
                render.nodes.get(e.from).transitions.put(e.dir, new Transition(e.to, e.label));
            }
        }
        stitchModeBoundaries();

        render.startKey = start != null && render.nodes.containsKey(start)
                ? start
                : render.order.get(0).id;
        stampPositions();
        return render;
    }

    // Where a stop mixes MENU rows with RAW content (search/sort/filter
    // controls above a sheet), the two wiring systems don't see each other,
    // leaving a vertical gap arrows can't cross. Stitch it: at each menu→raw
    // boundary (declaration order, same stop), the menu row's cells gain Down
    // edges into the first raw node still missing an Up edge, and that node
    // gains the Up back; at raw→menu boundaries the reverse. Only MISSING
    // edges are filled — the raw content's own wiring is never overridden.
    private void stitchModeBoundaries() {
        Map<Object, List<GraphNode>> byStop = new HashMap<Object, List<GraphNode>>();
        List<Object> stops = new ArrayList<Object>();
        for (GraphNode n : declared) {
            List<GraphNode> list = byStop.get(n.stopKey);
            if (list == null) {
                list = new ArrayList<GraphNode>();
                byStop.put(n.stopKey, list);
                stops.add(n.stopKey);
            }
            list.add(n);
        }

        for (Object stop : stops) {
            List<GraphNode> nodes = byStop.get(stop);
            for (int i = 1; i < nodes.size(); i++) {
                GraphNode prev = nodes.get(i - 1);
                GraphNode cur = nodes.get(i);
                boolean prevMenu = rowOf.containsKey(prev);
                boolean curMenu = rowOf.containsKey(cur);
                if (prevMenu == curMenu) {
                    continue; // same mode — its own wiring covers it
                }

                if (prevMenu) { // menu row above raw content
                    if (cur.transitions.containsKey(GraphDir.UP)) {
                        continue;
                    }
                    Row row = rowOf.get(prev);
                    for (GraphNode cell : row.items) {
                        if (!cell.transitions.containsKey(GraphDir.DOWN)) {
                            cell.transitions.put(GraphDir.DOWN, new Transition(cur.id));
                        }
                    }
                    cur.transitions.put(GraphDir.UP, new Transition(row.items.get(0).id));
                } else { // raw content above a menu row
                    Row row = rowOf.get(cur);
                    // The raw side's bottom = the latest raw node (walking back) missing a Down.
                    GraphNode bottom = null;
                    for (int j = i - 1; j >= 0 && !rowOf.containsKey(nodes.get(j)); j--) {
                        if (!nodes.get(j).transitions.containsKey(GraphDir.DOWN)) {
                            bottom = nodes.get(j);
                            break;
                        }
                    }
                    if (bottom == null) {
                        continue;
                    }
                    bottom.transitions.put(GraphDir.DOWN, new Transition(row.items.get(0).id));
                    for (GraphNode cell : row.items) {
                        if (!cell.transitions.containsKey(GraphDir.UP)) {
                            cell.transitions.put(GraphDir.UP, new Transition(bottom.id));
                        }
                    }
                }
            }
        }
    }

    // Auto-stamp "n of m" positions: a multi-item row's members are positioned
    // within their ROW; single-item-row nodes among the siblings sharing their
    // (parent, stop) — the vertical level arrows actually traverse. Raw/grid
    // nodes get none. Announced only when m > 1.
    private void stampPositions() {
        Map<List<Object>, List<GraphNode>> groups = new HashMap<List<Object>, List<GraphNode>>();
        List<List<Object>> keys = new ArrayList<List<Object>>();
        for (Row row : rows) {
            if (row.items.size() > 1) {
                stamp(row.items);
                continue;
            }
            GraphNode node = row.items.get(0);
            if (node.parent != null && node.parent.suppressChildPositions) {
                continue;
            }
            List<Object> key = Arrays.asList(node.parent, node.stopKey);
            List<GraphNode> list = groups.get(key);
            if (list == null) {
                list = new ArrayList<GraphNode>();
                groups.put(key, list);
                keys.add(key);
            }
            list.add(node);
        }
        for (List<Object> key : keys) {
            stamp(groups.get(key));
        }
    }

    private static void stamp(List<GraphNode> siblings) {
        if (siblings.size() < 2) {
            return;
        }
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).positionIndex = i + 1;
            siblings.get(i).positionCount = siblings.size();
        }
    }

    // Left/right within a row; up/down between consecutive rows OF THE SAME
    // STOP (arrows never cross a Tab-stop). Shared non-null row keys preserve
    // the column; otherwise vertical lands on the first item.
    private void wireMenuEdges() {
        // Segment rows in DECLARATION order: within a stop, consecutive menu
        // rows chain vertically only when no raw node was declared between
        // them. Interleaved raw content BREAKS the chain — stitchModeBoundaries
        // wires the seams. Without the break, menu edges would skip straight
        // over the raw block, leaving it an unreachable island.
        List<List<Row>> segments = new ArrayList<List<Row>>();
        Map<Object, List<Row>> openSegment = new HashMap<Object, List<Row>>(); // stop → open segment
        for (GraphNode node : declared) {
            Row row = rowOf.get(node);
            if (row != null) {
                List<Row> seg = openSegment.get(node.stopKey);
                if (seg == null) {
                    seg = new ArrayList<Row>();
                    openSegment.put(node.stopKey, seg);
                    segments.add(seg);
                }
                if (seg.isEmpty() || seg.get(seg.size() - 1) != row) {
                    seg.add(row);
                }
            } else {
                openSegment.remove(node.stopKey); // raw node: close this stop's segment
            }
        }

        for (List<Row> segment : segments) {
            for (int r = 0; r < segment.size(); r++) {
                Row row = segment.get(r);
                for (int pos = 0; pos < row.items.size(); pos++) {
                    GraphNode node = row.items.get(pos);
                    if (r > 0) {
                        node.transitions.put(GraphDir.UP,
                                new Transition(verticalTarget(row, segment.get(r - 1), pos)));
                    }
                    if (r < segment.size() - 1) {
                        node.transitions.put(GraphDir.DOWN,
                                new Transition(verticalTarget(row, segment.get(r + 1), pos)));
                    }
                    if (pos > 0) {
                        node.transitions.put(GraphDir.LEFT, new Transition(row.items.get(pos - 1).id));
                    }
                    if (pos < row.items.size() - 1) {
                        node.transitions.put(GraphDir.RIGHT, new Transition(row.items.get(pos + 1).id));
                    }
                }
            }
        }
    }

    // Where vertical navigation from position pos lands in the adjacent row:
    // the same position when the rows share a non-null key (column nav) and it
    // exists there, else the first item.
    private static ControlId verticalTarget(Row from, Row to, int pos) {
        if (from.key != null && to.key != null && Objects.equals(from.key, to.key) && pos < to.items.size()) {
            return to.items.get(pos).id;
        }
        return to.items.get(0).id;
    }
}
