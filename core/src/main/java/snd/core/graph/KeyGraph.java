package snd.core.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The navigation engine: a directed graph of controls rebuilt from a render
 * callback on each operation, with focus persisting in an external
 * {@link GraphState}. Ported from wotr-access / Tanglebeep, itself from
 * Factorio Access's key-graph.lua. Two invariants carry over:
 *
 * <p><b>Down-right total order</b> ({@link #computeOrder}): from the start
 * node, go right until stuck, queueing each down — visits a planar UI in
 * reading order. Nodes down-right can't reach (later Tab-stops) are appended
 * in declaration order, keeping the order total.</p>
 *
 * <p><b>Focus recovery on rebuild</b> ({@link #reconcile}): if the focused
 * control vanished, land on the nearest survivor rather than jumping to the
 * start — following the backing object that moved (tier 1) or the logical
 * control whose backing object was rebuilt (tier 2) first.</p>
 *
 * <p>The core never speaks — it returns what happened.</p>
 */
public final class KeyGraph {

    /** The outcome of a navigation operation, for the caller to announce. */
    public static final class MoveResult {
        public boolean moved;         // focus actually changed nodes
        public GraphNode from;        // node before the operation (null on first landing)
        public GraphNode to;          // node after (== from when at an edge; null when empty)
        public String transitionLabel; // the crossed edge's spoken line, when it had one
    }

    /** What a tree side-step did (the caller composes the speech). */
    public enum TreeMove {
        NONE,        // not applicable here — caller decides consume/bubble
        EXPANDED,    // the focused group expanded (focus unchanged; speak its new state)
        COLLAPSED,   // the focused group collapsed (focus unchanged; speak its new state)
        EMPTY_GROUP, // expanding found no children — auto-recollapsed
        DESCENDED,   // moved to the group's first child (announce as a move)
        ASCENDED,    // moved to the nearest focusable ancestor (announce as a move)
        LEAF         // Right on a non-group inside a tree — consumed
    }

    public static final class TreeResult {
        public TreeMove kind = TreeMove.NONE;
        public final MoveResult move = new MoveResult(); // valid for DESCENDED/ASCENDED
    }

    private final Supplier<GraphRender> renderCallback;
    private final GraphState state;
    private GraphRender current;

    public KeyGraph(Supplier<GraphRender> renderCallback, GraphState state) {
        this.renderCallback = renderCallback;
        this.state = state;
    }

    public GraphState state() {
        return state;
    }

    /** The most recently built render, or null if not yet rendered / empty. */
    public GraphRender current() {
        return current;
    }

    /** The focused node in the current render, or null. */
    public GraphNode currentNode() {
        return current != null ? current.nodeAt(state.curKey) : null;
    }

    /**
     * Rebuild the render and reconcile focus into it. False when the callback
     * produced nothing (the caller should treat the graph as closed/empty).
     */
    public boolean rerender() {
        current = renderCallback.get();
        if (current == null || current.nodes.isEmpty()) {
            current = null;
            return false;
        }
        reconcile(current, state);
        return true;
    }

    /**
     * Move focus from the cached curKey to a valid control in the render, then
     * recompute the traversal order.
     */
    public static void reconcile(GraphRender render, GraphState state) {
        // Honor a pending suggested move first, if its target still exists.
        if (state.nextSuggestedMove != null) {
            GraphNode suggested = render.nodes.get(state.nextSuggestedMove);
            if (suggested != null) {
                state.curKey = suggested.id;
            }
            state.nextSuggestedMove = null;
        }

        ControlId old = state.curKey;
        ControlId resolved = null;

        if (old != null) {
            // Tier 1: the same backing object, even if its structural key changed (it moved).
            if (old.reference != null) {
                for (Map.Entry<ControlId, GraphNode> kv : render.nodes.entrySet()) {
                    if (kv.getValue().id.referenceMatches(old.reference)) {
                        resolved = kv.getValue().id;
                        break;
                    }
                }
            }

            // Tier 2: the same structural key, even if the backing object was rebuilt.
            if (resolved == null) {
                GraphNode structural = render.nodes.get(old);
                if (structural != null) {
                    resolved = structural.id;
                }
            }

            // Fallback: nearest survivor walking the previous order backward. The order
            // interleaves row cells, so when a whole row vanished the walk lands on the
            // previous row's LAST cell — a different column. Slide along that row to the
            // column focus was on, so acting on rows in sequence keeps your place.
            if (resolved == null && state.keyOrder != null) {
                int oldIndex = indexOf(state.keyOrder, old);
                if (oldIndex >= 0) {
                    for (int i = oldIndex; i >= 0; i--) {
                        GraphNode survivor = render.nodes.get(state.keyOrder.get(i));
                        if (survivor != null) {
                            // A survivor in another stop means nothing before the old node
                            // is left in its own: a page that rebuilt every control (a
                            // filter button re-laying the list it filters) or a list that
                            // lost its first row. The stop is still there, so stay in it,
                            // at the place focus held, rather than fall out to the stop
                            // before it.
                            GraphNode held = Objects.equals(survivor.stopKey, state.lastStopKey) ? null
                                    : nodeInStopAt(render, state.lastStopKey, state.lastStopIndex);
                            resolved = held != null ? held.id
                                    : slideToColumn(render, survivor.id, state.lastColumn);
                            break;
                        }
                    }
                }
            }
            // Every earlier node is gone too (the old one led the screen): its stop first.
            if (resolved == null) {
                GraphNode held = nodeInStopAt(render, state.lastStopKey, state.lastStopIndex);
                resolved = held != null ? held.id : null;
            }
        }

        // Nothing matched (or first render): the start node — but prefer the SELECTED
        // member of its stop (initial focus lands on the checked radio, not the top).
        if (resolved == null) {
            GraphNode startNode = render.nodes.get(render.startKey);
            GraphNode sel = startNode != null ? selectedNodeInStop(render, startNode.stopKey) : null;
            resolved = sel != null ? sel.id : startNode != null ? startNode.id : render.startKey;
        }

        state.curKey = resolved;
        rememberStop(render, state, resolved);
        rememberColumn(render, state, resolved);
        state.keyOrder = computeOrder(render);
    }

    // Track the tabular column focus sits on (nodes outside tables leave it
    // untouched, so a detour through another stop doesn't forget the column).
    private static void rememberColumn(GraphRender render, GraphState state, ControlId key) {
        GraphNode node = render.nodeAt(key);
        if (node != null && node.vtable != null && node.vtable.column >= 0) {
            state.lastColumn = node.vtable.column;
        }
    }

    /**
     * From start, walk its row's Left/Right edges to the cell at prefCol — or
     * the nearest cell BELOW it in a sparse row. Returns start unchanged when
     * either side isn't tabular or the column already matches. Horizontal
     * edges never leave a row, so the walk can't escape it.
     */
    static ControlId slideToColumn(GraphRender render, ControlId start, int prefCol) {
        GraphNode node = render.nodeAt(start);
        if (node == null || prefCol < 0) {
            return start;
        }
        int col = node.vtable != null ? node.vtable.column : -1;
        if (col < 0 || col == prefCol) {
            return start;
        }

        GraphDir dir = col < prefCol ? GraphDir.RIGHT : GraphDir.LEFT;
        GraphNode cur = node;
        while (cur.vtable.column != prefCol) {
            Transition t = cur.transitions.get(dir);
            if (t == null) {
                break;
            }
            GraphNode next = render.nodeAt(t.destination);
            if (next == null || next.vtable == null || next.vtable.column < 0) {
                break;
            }
            if (dir == GraphDir.RIGHT && next.vtable.column > prefCol) {
                break; // sparse: stop below pref
            }
            if (dir == GraphDir.LEFT && next.vtable.column < prefCol) {
                cur = next; // overshot: nearest below
                break;
            }
            cur = next;
        }
        return cur.id;
    }

    /**
     * The down-right total order: go right until stuck (recording each node),
     * queue every down for a later pass, repeat — then append any node the
     * walk never reached (later Tab-stops) in declaration order.
     */
    public static List<ControlId> computeOrder(GraphRender render) {
        List<ControlId> order = new ArrayList<ControlId>();
        Set<ControlId> seen = new HashSet<ControlId>();
        List<ControlId> downFringe = new ArrayList<ControlId>();
        downFringe.add(render.startKey);

        int i = 0;
        while (i < downFringe.size()) {
            ControlId k = downFringe.get(i);
            while (!seen.contains(k)) {
                seen.add(k);
                order.add(k);

                GraphNode n = render.nodes.get(k);
                if (n == null) {
                    break;
                }
                Transition d = n.transitions.get(GraphDir.DOWN);
                if (d != null) {
                    downFringe.add(d.destination);
                }
                Transition t = n.transitions.get(GraphDir.RIGHT);
                if (t == null) {
                    break;
                }
                k = t.destination;
            }
            i++;
        }

        for (GraphNode node : render.order) {
            if (seen.add(node.id)) {
                order.add(node.id);
            }
        }
        return order;
    }

    private static int indexOf(List<ControlId> order, ControlId key) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static void rememberStop(GraphRender render, GraphState state, ControlId key) {
        GraphNode node = render.nodeAt(key);
        if (node != null && node.stopKey != null) {
            state.stopMemory.put(node.stopKey, key);
        }
        state.lastStopKey = node != null ? node.stopKey : null;
        state.lastStopIndex = -1;
        if (node != null && node.stopKey != null) {
            int index = 0;
            for (GraphNode n : render.order) {
                if (n == node) {
                    state.lastStopIndex = index;
                    break;
                }
                if (Objects.equals(n.stopKey, node.stopKey)) {
                    index++;
                }
            }
        }
    }

    // The node at a place within a stop, clamped to the stop's last; null when the
    // stop is not in the render (or was never recorded).
    private static GraphNode nodeInStopAt(GraphRender render, Object stopKey, int index) {
        if (stopKey == null || index < 0) {
            return null;
        }
        GraphNode last = null;
        int seen = 0;
        for (GraphNode n : render.order) {
            if (!Objects.equals(n.stopKey, stopKey)) {
                continue;
            }
            if (seen == index) {
                return n;
            }
            last = n;
            seen++;
        }
        return last;
    }

    private void setCurrent(GraphNode node) {
        state.curKey = node.id;
        rememberStop(current, state, node.id);
        if (node.vtable != null && node.vtable.column >= 0) {
            state.lastColumn = node.vtable.column;
        }
    }

    /**
     * Focus id slid to prefCol within its row (the type-ahead landing: match
     * the row, land on the column you were working in).
     */
    public boolean focusAtColumn(ControlId id, int prefCol) {
        if (id == null || !rerender()) {
            return false;
        }
        ControlId target = slideToColumn(current, id, prefCol);
        GraphNode node = current.nodeAt(target);
        if (node == null) {
            return false;
        }
        setCurrent(node);
        return true;
    }

    // ---- navigation operations ----

    /** One step in dir. Not moved (at an edge / empty) → to == from. */
    public MoveResult move(GraphDir dir) {
        MoveResult result = new MoveResult();
        if (!rerender()) {
            return result;
        }

        GraphNode node = currentNode();
        result.from = node;
        result.to = node;
        if (node == null) {
            return result;
        }

        Transition t = node.transitions.get(dir);
        GraphNode dest = t != null ? current.nodeAt(t.destination) : null;
        if (dest == null || dest == node) {
            return result;
        }

        setCurrent(dest);
        result.to = dest;
        result.moved = true;
        result.transitionLabel = t.label;
        return result;
    }

    /** As far as possible in dir (Home/End within a row or column). */
    public MoveResult moveToEdge(GraphDir dir) {
        MoveResult result = new MoveResult();
        if (!rerender()) {
            return result;
        }

        GraphNode node = currentNode();
        result.from = node;
        result.to = node;
        if (node == null) {
            return result;
        }

        GraphNode cur = node;
        while (true) {
            Transition t = cur.transitions.get(dir);
            if (t == null) {
                break;
            }
            GraphNode next = current.nodeAt(t.destination);
            if (next == null || next == cur) {
                break;
            }
            cur = next;
        }

        if (cur != node) {
            setCurrent(cur);
            result.to = cur;
            result.moved = true;
        }
        return result;
    }

    /**
     * Cycle to the next/previous Tab-stop (declaration order), landing on the
     * stop's remembered position (else its first node). wrap continues past
     * the ends; without it, at the last/first stop the result is not-moved.
     */
    public MoveResult moveStop(int dir, boolean wrap) {
        MoveResult result = new MoveResult();
        if (!rerender()) {
            return result;
        }

        GraphNode node = currentNode();
        result.from = node;
        result.to = node;
        if (node == null) {
            return result;
        }

        List<Object> stops = stopOrder();
        if (stops.size() <= 1) {
            return result;
        }

        int idx = stops.indexOf(node.stopKey);
        if (idx < 0) {
            return result;
        }
        int ni = idx + dir;
        if (wrap) {
            ni = ((ni % stops.size()) + stops.size()) % stops.size();
        }
        if (ni < 0 || ni >= stops.size() || ni == idx) {
            return result;
        }

        GraphNode dest = stopLanding(stops.get(ni));
        if (dest == null) {
            return result;
        }

        setCurrent(dest);
        result.to = dest;
        result.moved = true;
        return result;
    }

    /**
     * The region a node jumps by: the one its screen tagged it with, else
     * its innermost container (a titled group, a unit's row). A collapsible
     * group's header is no container here — a tree is one region. The nodes
     * in no container are the stop's own region.
     */
    public static Object regionOf(GraphNode node) {
        if (node.regionKey != null) {
            return node.regionKey;
        }
        for (GraphNode p = node.parent; p != null; p = p.parent) {
            if (!p.focusable) {
                return p.id;
            }
        }
        return CompositeKey.of("stop-region", node.stopKey);
    }

    // The current stop's regions, in declaration order.
    private List<Object> regionsOfStop(GraphNode node) {
        List<Object> regions = new ArrayList<Object>();
        for (GraphNode n : current.order) {
            if (Objects.equals(n.stopKey, node.stopKey)) {
                Object region = regionOf(n);
                if (region != null && !regions.contains(region)) {
                    regions.add(region);
                }
            }
        }
        return regions;
    }

    /** Whether the focused node's stop has regions to jump between. */
    public boolean hasRegions() {
        if (!rerender()) {
            return false;
        }
        GraphNode node = currentNode();
        return node != null && regionsOfStop(node).size() > 1;
    }

    /**
     * Jump to the next/previous region within the current stop (declaration
     * order), landing on the region's first node.
     */
    public MoveResult moveRegion(int dir) {
        MoveResult result = new MoveResult();
        if (!rerender()) {
            return result;
        }

        GraphNode node = currentNode();
        result.from = node;
        result.to = node;
        if (node == null) {
            return result;
        }

        List<Object> regions = regionsOfStop(node);
        int idx = regions.indexOf(regionOf(node));
        int ni = idx + dir;
        if (idx < 0 || ni < 0 || ni >= regions.size()) {
            return result;
        }

        for (GraphNode n : current.order) {
            if (Objects.equals(n.stopKey, node.stopKey) && Objects.equals(regionOf(n), regions.get(ni))) {
                setCurrent(n);
                result.to = n;
                result.moved = true;
                return result;
            }
        }
        return result;
    }

    /**
     * Move focus to a specific control (a node just revealed, a screen's
     * chosen landing). False when it isn't in the render.
     */
    public boolean focus(ControlId id) {
        if (id == null || !rerender()) {
            return false;
        }
        GraphNode node = current.nodeAt(id);
        if (node == null) {
            return false;
        }
        setCurrent(node);
        return true;
    }

    /**
     * Tier-1 focus sync from the game: if a node's backing object is
     * reference, move focus there. True if focus changed nodes.
     */
    public boolean focusByReference(Object reference) {
        if (reference == null || current == null) {
            return false;
        }
        for (Map.Entry<ControlId, GraphNode> kv : current.nodes.entrySet()) {
            if (kv.getValue().id.referenceMatches(reference)) {
                boolean changed = state.curKey == null || !state.curKey.equals(kv.getValue().id);
                setCurrent(kv.getValue());
                return changed;
            }
        }
        return false;
    }

    private List<Object> stopOrder() {
        List<Object> stops = new ArrayList<Object>();
        for (GraphNode n : current.order) {
            if (n.stopKey != null && !stops.contains(n.stopKey)) {
                stops.add(n.stopKey);
            }
        }
        return stops;
    }

    /**
     * Where focus lands when entering a stop with no active cursor: the
     * remembered position, else the SELECTED member, else the stop's first
     * node.
     */
    public GraphNode stopLanding(Object stopKey) {
        return stopLanding(current, state, stopKey);
    }

    public static GraphNode stopLanding(GraphRender render, GraphState state, Object stopKey) {
        ControlId remembered = state.stopMemory.get(stopKey);
        if (remembered != null) {
            GraphNode node = render.nodeAt(remembered);
            if (node != null && Objects.equals(node.stopKey, stopKey)) {
                return node;
            }
        }
        GraphNode selected = selectedNodeInStop(render, stopKey);
        if (selected != null) {
            return selected;
        }
        for (GraphNode n : render.order) {
            if (Objects.equals(n.stopKey, stopKey)) {
                return n;
            }
        }
        return null;
    }

    /** The first node in a stop that {@link #isSelected is selected}, or null. */
    public static GraphNode selectedNodeInStop(GraphRender render, Object stopKey) {
        for (GraphNode n : render.order) {
            if (Objects.equals(n.stopKey, stopKey) && isSelected(n)) {
                return n;
            }
        }
        return null;
    }

    /**
     * Whether a node is the selected member of its stop: it says so silently
     * ({@link NodeVtable#selected}) or aloud (a non-empty selected-kind
     * announcement part). A check that throws reads as unselected.
     */
    public static boolean isSelected(GraphNode n) {
        if (n.vtable == null) {
            return false;
        }
        try {
            if (n.vtable.selected != null && n.vtable.selected.getAsBoolean()) {
                return true;
            }
            List<NodeAnnouncement> anns = n.vtable.announcements;
            if (anns != null) {
                for (NodeAnnouncement a : anns) {
                    if (a != null && AnnouncementKinds.SELECTED.equals(a.kind) && a.text != null) {
                        String t = a.text.get();
                        if (t != null && !t.isEmpty()) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // reads as unselected
        }
        return false;
    }

    // ---- tree operations (Right/Left semantics for expandable groups) ----

    /**
     * Is this node part of an expandable structure (itself a group, or under
     * one)? The navigator uses this to decide whether Left/Right get tree
     * semantics.
     */
    public static boolean inTree(GraphNode node) {
        for (GraphNode n = node; n != null; n = n.parent) {
            if (n.expandable) {
                return true;
            }
        }
        return false;
    }

    /**
     * Right on a group: expand (auto-recollapse when it turns out empty), or
     * descend into an expanded one. Right elsewhere in a tree: LEAF (consume).
     */
    public TreeResult treeRight() {
        TreeResult result = new TreeResult();
        if (!rerender()) {
            return result;
        }
        GraphNode node = currentNode();
        if (node == null) {
            return result;
        }

        if (node.expandable && !node.expanded) {
            setExpanded(node, true);
            if (!rerender()) {
                return result;
            }
            GraphNode header = current.nodeAt(node.id);
            if (header == null) {
                return result;
            }
            if (firstChildOf(header) == null) {
                // A lazy drill-in that resolved to nothing: don't leave a
                // silent empty-expanded node.
                setExpanded(header, false);
                rerender();
                result.kind = TreeMove.EMPTY_GROUP;
                return result;
            }
            result.kind = TreeMove.EXPANDED;
            return result;
        }

        if (node.expandable && node.expanded) {
            GraphNode child = firstChildOf(node);
            if (child == null) {
                result.kind = TreeMove.LEAF;
                return result;
            }
            result.move.from = node;
            setCurrent(child);
            result.move.to = child;
            result.move.moved = true;
            result.kind = TreeMove.DESCENDED;
            return result;
        }

        result.kind = inTree(node) ? TreeMove.LEAF : TreeMove.NONE;
        return result;
    }

    /**
     * Left on an expanded group: collapse. Left elsewhere in a tree: ascend to
     * the nearest focusable ancestor.
     */
    public TreeResult treeLeft() {
        TreeResult result = new TreeResult();
        if (!rerender()) {
            return result;
        }
        GraphNode node = currentNode();
        if (node == null) {
            return result;
        }

        if (node.expandable && node.expanded) {
            setExpanded(node, false);
            rerender(); // focus stays on the header by identity
            result.kind = TreeMove.COLLAPSED;
            return result;
        }

        for (GraphNode p = node.parent; p != null; p = p.parent) {
            if (!p.focusable || !current.nodes.containsKey(p.id)) {
                continue;
            }
            result.move.from = node;
            GraphNode target = current.nodeAt(p.id);
            setCurrent(target);
            result.move.to = target;
            result.move.moved = true;
            result.kind = TreeMove.ASCENDED;
            return result;
        }

        result.kind = inTree(node) ? TreeMove.LEAF : TreeMove.NONE;
        return result;
    }

    /**
     * Home/End inside a tree: the first/last node sharing the focused node's
     * parent (its siblings at the current depth).
     */
    public MoveResult moveToSiblingEdge(boolean first) {
        MoveResult result = new MoveResult();
        if (!rerender()) {
            return result;
        }
        GraphNode node = currentNode();
        result.from = node;
        result.to = node;
        if (node == null) {
            return result;
        }

        GraphNode target = null;
        for (GraphNode n : current.order) {
            if (n.parent != node.parent) {
                continue;
            }
            if (first) {
                target = n;
                break;
            }
            target = n; // last match wins
        }
        if (target == null || target == node) {
            return result;
        }
        setCurrent(target);
        result.to = target;
        result.moved = true;
        return result;
    }

    // Change a group's expansion: through its vtable override when declared,
    // else the persistent set.
    private void setExpanded(GraphNode group, boolean expanded) {
        if (expanded && group.vtable.onExpand != null) {
            group.vtable.onExpand.run();
            return;
        }
        if (!expanded && group.vtable.onCollapse != null) {
            group.vtable.onCollapse.run();
            return;
        }
        if (expanded) {
            state.expanded.add(group.id);
        } else {
            state.expanded.remove(group.id);
        }
    }

    private GraphNode firstChildOf(GraphNode group) {
        for (GraphNode n : current.order) {
            if (n.parent == group) {
                return n;
            }
        }
        return null;
    }

    // ---- behavior invokers (the caller announces fallbacks / state) ----

    /** Run the focused control's primary activation. False = it has none. */
    public boolean activate() {
        if (!rerender()) {
            return false;
        }
        GraphNode node = currentNode();
        if (node == null || node.vtable.onActivate == null) {
            return false;
        }
        node.vtable.onActivate.run();
        return true;
    }

    /** Run the focused control's secondary activation. False = it has none. */
    public boolean secondary() {
        if (!rerender()) {
            return false;
        }
        GraphNode node = currentNode();
        if (node == null || node.vtable.onSecondary == null) {
            return false;
        }
        node.vtable.onSecondary.run();
        return true;
    }

    /** Run the focused control's drag behavior. False = it has none. */
    public boolean drag() {
        if (!rerender()) {
            return false;
        }
        GraphNode node = currentNode();
        if (node == null || node.vtable.onDrag == null) {
            return false;
        }
        node.vtable.onDrag.run();
        return true;
    }

    /**
     * If the focused control adjusts horizontally (a slider), adjust and
     * return true; false = the caller should navigate instead.
     */
    public boolean tryAdjust(int sign, boolean large) {
        if (!rerender()) {
            return false;
        }
        GraphNode node = currentNode();
        if (node == null || node.vtable.onAdjust == null) {
            return false;
        }
        node.vtable.onAdjust.adjust(sign, large);
        return true;
    }
}
