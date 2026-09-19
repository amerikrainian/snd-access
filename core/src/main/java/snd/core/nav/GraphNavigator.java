package snd.core.nav;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import snd.contracts.SndLog;
import snd.core.graph.ControlId;
import snd.core.graph.GraphAnnouncer;
import snd.core.graph.GraphBuilder;
import snd.core.graph.GraphNode;
import snd.core.graph.GraphRender;
import snd.core.graph.GraphState;
import snd.core.graph.KeyGraph;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.GraphDir;
import snd.core.graph.Transition;
import snd.core.search.TypeAheadSearch;
import snd.contracts.speech.SpeechPipeline;
import snd.contracts.speech.TextFilter;

/**
 * The graph navigator: pull-based diffing over an immediate-mode graph. The
 * graph is rebuilt per operation and per frame, focus is reconciled by
 * identity, and a focus change is announced exactly once no matter what
 * caused it (input, a screen moving focus, a content rebuild, or the game
 * yanking state). One {@link GraphState} per LIVE screen, so a covered screen
 * restores exactly where it was; a closed screen's state is dropped.
 */
public final class GraphNavigator {
    private final SpeechPipeline speech;

    private final Map<AccessScreen, GraphState> states = new HashMap<AccessScreen, GraphState>();
    private GraphState state = new GraphState();
    private KeyGraph graph;
    private AccessScreen screen;

    // The differ's memory: the node identity (and render node) last spoken.
    private ControlId lastSpokenKey;
    private GraphNode lastSpokenNode;

    // A focus request whose target isn't in the render yet (lazy content).
    private ControlId pendingFocus;
    private boolean pendingAnnounce;
    private Object pendingStop;

    public GraphNavigator(SpeechPipeline speech) {
        this.speech = speech;
        search.onNoMatch = new Consumer<String>() {
            @Override
            public void accept(String text) {
                speak(snd.core.loc.Loc.get("ui", "nav.no_match", "text", text), true);
            }
        };
    }

    private void speak(String text, boolean interrupt) {
        speech.speak(text, interrupt);
    }

    public boolean hasFocus() {
        return graph != null && graph.currentNode() != null;
    }

    public AccessScreen screen() {
        return screen;
    }

    public void attach(final AccessScreen newScreen) {
        boolean same = newScreen == screen;
        screen = newScreen;
        clearSearch();
        if (!same) {
            // Swap to this screen's own state (creating it on first attach).
            // The differ memory resets so the restored landing announces
            // itself on return.
            if (newScreen != null) {
                GraphState existing = states.get(newScreen);
                if (existing == null) {
                    existing = new GraphState();
                    states.put(newScreen, existing);
                }
                state = existing;
            } else {
                state = new GraphState();
            }
            lastSpokenKey = null;
            lastSpokenNode = null;
            pendingFocus = null;
            pendingStop = null;
            liveKey = null;
        }
        graph = newScreen != null ? new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                GraphBuilder b = new GraphBuilder(state.expanded);
                newScreen.build(b);
                return b.build();
            }
        }, state) : null;
    }

    /** Drop a popped screen's cursor state (reopening starts fresh). */
    public void screenClosed(AccessScreen closed) {
        if (closed != null) {
            states.remove(closed);
        }
    }

    /** Request focus on a node that may not be in the render yet. */
    public void focusNode(ControlId id, boolean announce) {
        if (id == null) {
            return;
        }
        pendingFocus = id;
        pendingAnnounce = announce;
    }

    /** Request a landing on a stop (resolved to its landing node at apply time). */
    public void focusStop(Object stopKey) {
        pendingStop = stopKey;
    }

    public void blur() {
        state.curKey = null;
        lastSpokenKey = null;
        lastSpokenNode = null;
        pendingFocus = null;
        liveKey = null;
    }

    /**
     * The per-frame pull: rebuild + reconcile, establish initial focus when
     * content appears, apply pending focus requests, and announce any
     * focus-identity change exactly once.
     */
    public void ensureFocus() {
        if (screen == null || graph == null) {
            return;
        }

        if (state.curKey == null && pendingFocus == null) {
            if (!graph.rerender()) {
                return; // no content yet — reconcile seats the start node once there is
            }
            // Declared initial landing: seat the stop's landing node BEFORE
            // the differ announces below.
            Object stop = screen.initialFocusStop();
            if (stop != null) {
                GraphNode land = KeyGraph.stopLanding(graph.current(), graph.state(), stop);
                if (land != null) {
                    graph.focus(land.id);
                }
            }
        } else {
            if (!graph.rerender()) {
                return; // nothing focusable this frame — retry
            }
            if (pendingFocus != null) {
                // One retry frame for a target focused mid-build; a target
                // that still isn't in the render was removed — drop it.
                if (graph.current().nodes.containsKey(pendingFocus)) {
                    graph.focus(pendingFocus);
                    if (!pendingAnnounce) {
                        lastSpokenKey = pendingFocus;
                        lastSpokenNode = graph.currentNode();
                    }
                }
                pendingFocus = null;
            }
            if (pendingStop != null) {
                GraphNode land = KeyGraph.stopLanding(graph.current(), graph.state(), pendingStop);
                if (land != null) {
                    graph.focus(land.id);
                }
                pendingStop = null; // announce rides the normal differ below
            }
        }

        GraphNode node = graph.currentNode();
        if (node == null) {
            return;
        }

        if (lastSpokenKey == null || !lastSpokenKey.equals(node.id)) {
            // Queued (not interrupting): landings follow the screen name /
            // preceding feedback.
            if (!takeQuietLanding()) {
                speak(GraphAnnouncer.compose(lastSpokenNode, node), false);
            }
            lastSpokenKey = node.id;
            lastSpokenNode = node;
        }

        watchLive(node);
    }

    // ---- live announcements: watch the FOCUSED node's live parts and speak
    // a part when its value changes. Baselines silently whenever focus lands
    // on a new identity (the focus announcement already spoke the state).
    private ControlId liveKey;
    private final List<String> liveValues = new ArrayList<String>();

    private void watchLive(GraphNode node) {
        List<NodeAnnouncement> anns = GraphAnnouncer.effectiveAnnouncements(node);
        if (anns.isEmpty()) {
            return;
        }
        boolean baseline = liveKey == null || !liveKey.equals(node.id) || liveValues.size() != anns.size();
        if (baseline) {
            liveKey = node.id;
            liveValues.clear();
        }

        for (int i = 0; i < anns.size(); i++) {
            NodeAnnouncement a = anns.get(i);
            if (a == null || !a.live) {
                if (baseline) {
                    liveValues.add(null);
                }
                continue;
            }
            String v = null;
            try {
                v = a.text != null ? a.text.get() : null;
            } catch (Throwable t) {
                SndLog.error("live part threw", t);
            }
            if (baseline) {
                liveValues.add(v);
                continue;
            }
            String old = liveValues.get(i);
            if (old == null ? v != null : !old.equals(v)) {
                liveValues.set(i, v);
                if (v != null && !v.isEmpty()) {
                    speak(v, false);
                }
            }
        }
    }

    /** Re-announce the focused node in full (screen re-entry). */
    public void announceCurrent() {
        if (graph == null || !graph.rerender()) {
            return;
        }
        GraphNode node = graph.currentNode();
        if (node == null) {
            return;
        }
        speak(GraphAnnouncer.composeFull(node), false);
        lastSpokenKey = node.id;
        lastSpokenNode = node;
    }

    // ---- input ----

    /** Handle a logical action. False = not consumed (falls through to the game). */
    public boolean onAction(NavAction action) {
        if (search.isSearchActive()) {
            if (searchFocusId != null && (graph == null || graph.currentNode() == null
                    || !searchFocusId.equals(graph.currentNode().id))) {
                clearSearch(); // focus moved under us → results are stale
            } else if (action == NavAction.UP && search.resultCount() > 0) {
                search.navigateResults(-1);
                return true;
            } else if (action == NavAction.DOWN && search.resultCount() > 0) {
                search.navigateResults(1);
                return true;
            } else if (action == NavAction.HOME && search.resultCount() > 0) {
                search.jumpToFirstResult();
                return true;
            } else if (action == NavAction.END && search.resultCount() > 0) {
                search.jumpToLastResult();
                return true;
            } else if (action == NavAction.CANCEL) {
                clearSearch();
                speak(snd.core.loc.Loc.get("ui", "nav.search_cleared"), true);
                return true;
            } else {
                clearSearch();
            }
        }

        switch (action) {
            case UP:
                return arrow(GraphDir.UP);
            case DOWN:
                return arrow(GraphDir.DOWN);
            case LEFT:
                return arrow(GraphDir.LEFT);
            case RIGHT:
                return arrow(GraphDir.RIGHT);
            case NEXT_STOP:
                return tab(1);
            case PREV_STOP:
                return tab(-1);
            case HOME:
                return jumpEdge(true);
            case END:
                return jumpEdge(false);
            case REGION_PREV:
                return graph != null && graph.currentNode() != null
                        && graph.currentNode().regionKey != null && regionJump(-1);
            case REGION_NEXT:
                return graph != null && graph.currentNode() != null
                        && graph.currentNode().regionKey != null && regionJump(1);
            case ACTIVATE: {
                if (graph == null || graph.currentNode() == null) {
                    return false;
                }
                vtableActivate();
                return true;
            }
            case SECONDARY: {
                GraphNode node = graph != null ? graph.currentNode() : null;
                if (node == null) {
                    return false;
                }
                if (node.vtable.onSecondary != null) {
                    graph.secondary();
                }
                // Consumed even with nothing to do: the game reads this
                // physical key as dialog decline/OK, which must never fire
                // from a press meant for the navigator.
                return true;
            }
            case CANCEL:
                return screen != null && screen.onCancel();
            default:
                return false;
        }
    }

    // ---- the dry run ----

    /**
     * The same decisions as {@link #onAction}, made without acting: an arrow
     * where the focused node has a way that way (an edge, a value to adjust,
     * a group to open or leave), Tab where there is another stop to go round
     * to, Home/End where the node has siblings, Enter and Backspace where the
     * node has the behavior. The key help lists exactly these.
     */
    public boolean wouldHandle(NavAction action) {
        GraphNode node = graph != null && graph.rerender() ? graph.currentNode() : null;
        if (node == null) {
            return false;
        }
        switch (action) {
            case UP:
                return hasWay(node, GraphDir.UP);
            case DOWN:
                return hasWay(node, GraphDir.DOWN);
            case LEFT:
                return node.vtable.onAdjust != null || hasWay(node, GraphDir.LEFT)
                        || (node.expandable && node.expanded) || hasFocusableAncestor(node);
            case RIGHT:
                return node.vtable.onAdjust != null || hasWay(node, GraphDir.RIGHT) || node.expandable;
            case NEXT_STOP:
            case PREV_STOP:
                return hasOtherStop(node);
            case HOME:
                return KeyGraph.inTree(node) ? siblingEdge(node, true) != node : hasWay(node, GraphDir.UP);
            case END:
                return KeyGraph.inTree(node) ? siblingEdge(node, false) != node : hasWay(node, GraphDir.DOWN);
            case REGION_PREV:
            case REGION_NEXT:
                return node.regionKey != null;
            case ACTIVATE:
                return node.vtable.onActivate != null;
            case SECONDARY:
                return node.vtable.onSecondary != null;
            case CANCEL:
                return search.isSearchActive();
            default:
                return false;
        }
    }

    private boolean hasWay(GraphNode node, GraphDir dir) {
        Transition t = node.transitions.get(dir);
        GraphNode dest = t != null ? graph.current().nodeAt(t.destination) : null;
        return dest != null && dest != node;
    }

    private boolean hasFocusableAncestor(GraphNode node) {
        for (GraphNode p = node.parent; p != null; p = p.parent) {
            if (p.focusable && graph.current().nodes.containsKey(p.id)) {
                return true;
            }
        }
        return false;
    }

    // Tab's own test: it goes round, so another stop to land on is all it
    // needs; from outside every stop, any stop at all.
    private boolean hasOtherStop(GraphNode node) {
        List<Object> stops = stops();
        return stops.contains(node.stopKey) ? stops.size() > 1 : !stops.isEmpty();
    }

    // The render's Tab-stops, in first-appearance order.
    private List<Object> stops() {
        List<Object> stops = new ArrayList<Object>();
        for (GraphNode n : graph.current().order) {
            if (n.stopKey != null && !stops.contains(n.stopKey)) {
                stops.add(n.stopKey);
            }
        }
        return stops;
    }

    // Where Home/End land inside a tree: the first/last node sharing the
    // focused node's parent.
    private GraphNode siblingEdge(GraphNode node, boolean first) {
        GraphNode target = node;
        for (GraphNode n : graph.current().order) {
            if (n.parent != node.parent) {
                continue;
            }
            target = n;
            if (first) {
                break;
            }
        }
        return target;
    }

    // ---- the quiet landing: an overlay of the mod's own that closes to run
    // an action for the player brings the covered screen back; its name and
    // landing stay unspoken, the focus being read after the action instead.
    // Good for a short while only: a request nothing consumed (the covered
    // screen went away meanwhile) must not swallow some later landing.

    /** Frame counter; tests substitute their own. */
    public interface FrameClock {
        long frame();
    }

    private static final int QUIET_LANDING_FRAMES = 90;
    private long quietLandingUntil = Long.MIN_VALUE;
    private FrameClock frames = new FrameClock() {
        @Override
        public long frame() {
            return snd.contracts.Dispatcher.frameCount();
        }
    };

    public void setFrameClock(FrameClock clock) {
        frames = clock;
    }

    public void quietNextLanding() {
        quietLandingUntil = frames.frame() + QUIET_LANDING_FRAMES;
    }

    public boolean quietLandingPending() {
        return frames.frame() <= quietLandingUntil;
    }

    private boolean takeQuietLanding() {
        boolean quiet = quietLandingPending();
        quietLandingUntil = Long.MIN_VALUE;
        return quiet;
    }

    // ---- reading the focus back ----

    /** The focused node, or null. */
    public GraphNode focusedNode() {
        return graph != null ? graph.currentNode() : null;
    }

    /** What the focused control concerns ({@link snd.core.graph.NodeVtable#subject}), or null. */
    public Object focusedSubject() {
        GraphNode node = focusedNode();
        return node != null ? node.vtable.subject : null;
    }

    /** The focused node's identity, or null. */
    public ControlId focusedId() {
        GraphNode node = graph != null ? graph.currentNode() : null;
        return node != null ? node.id : null;
    }

    /** The focused node's label (its first announcement part), or null. */
    public String focusedLabel() {
        GraphNode node = graph != null ? graph.currentNode() : null;
        return node != null ? GraphAnnouncer.firstPartText(node) : null;
    }

    /** Speak the focused node's own readout, queued, without its context. */
    public void readFocus() {
        if (graph == null || !graph.rerender()) {
            return;
        }
        GraphNode node = graph.currentNode();
        if (node == null) {
            return;
        }
        speak(GraphAnnouncer.leafText(node), false);
        lastSpokenKey = node.id;
        lastSpokenNode = node;
        liveKey = null;
    }

    private boolean arrow(GraphDir dir) {
        GraphNode focusNode = graph != null ? graph.currentNode() : null;
        if (focusNode == null) {
            return false;
        }

        // A focused slider adjusts on Left/Right (priority over navigation).
        if (dir == GraphDir.LEFT || dir == GraphDir.RIGHT) {
            if (vtableAdjust(dir == GraphDir.RIGHT ? 1 : -1)) {
                return true;
            }
        }

        // Edge-wired movement first.
        KeyGraph.MoveResult move = graph.move(dir);
        if (move.moved) {
            announceMove(move);
            return true;
        }

        // At an edge. Left/Right get tree semantics.
        if (dir == GraphDir.LEFT || dir == GraphDir.RIGHT) {
            KeyGraph.TreeResult tr = dir == GraphDir.RIGHT ? graph.treeRight() : graph.treeLeft();
            switch (tr.kind) {
                case EXPANDED:
                case COLLAPSED:
                    speakFocusedState();
                    return true;
                case EMPTY_GROUP:
                    speak(snd.core.loc.Loc.get("ui", "nav.no_details"), true);
                    return true;
                case DESCENDED:
                case ASCENDED:
                    announceMove(tr.move);
                    return true;
                case LEAF:
                    return true; // inside a tree; nothing that way — consume
                default:
                    break;
            }
        }

        // Nothing moved: consume edges inside trees; bubble from plain lists.
        return KeyGraph.inTree(focusNode);
    }

    // Speak the focused group's post-toggle state and rebaseline the differ +
    // live watch so the toggle isn't re-announced.
    private void speakFocusedState() {
        GraphNode node = graph.currentNode();
        if (node == null) {
            return;
        }
        speak(GraphAnnouncer.leafText(node), true);
        lastSpokenKey = node.id;
        lastSpokenNode = node;
        liveKey = null;
    }

    private boolean tab(int step) {
        if (graph == null || !graph.rerender()) {
            return false;
        }

        List<Object> stops = stops();
        if (stops.isEmpty()) {
            return false;
        }

        GraphNode curNode = graph.currentNode();
        int idx = curNode != null ? stops.indexOf(curNode.stopKey) : -1;

        if (idx < 0) {
            return landOnStop(stops.get(step >= 0 ? 0 : stops.size() - 1));
        }
        if (stops.size() == 1) {
            return true; // the only stop: nowhere to go; consume without re-reading the focus
        }
        // Tab goes round: past the last stop is the first, before the first the last.
        int ni = (((idx + step) % stops.size()) + stops.size()) % stops.size();
        return landOnStop(stops.get(ni));
    }

    private boolean landOnStop(Object stopKey) {
        // Remembered position → SELECTED member → first node.
        GraphNode land = KeyGraph.stopLanding(graph.current(), graph.state(), stopKey);
        if (land == null || !graph.focus(land.id)) {
            return true;
        }

        GraphNode node = graph.currentNode();
        speak(GraphAnnouncer.compose(lastSpokenNode, node), true);
        lastSpokenKey = node.id;
        lastSpokenNode = node;
        return true;
    }

    private boolean jumpEdge(boolean first) {
        GraphNode focusNode = graph != null ? graph.currentNode() : null;
        if (focusNode == null) {
            return false;
        }

        // In a tree: first/last sibling at the current depth.
        if (KeyGraph.inTree(focusNode)) {
            KeyGraph.MoveResult sib = graph.moveToSiblingEdge(first);
            if (sib.moved) {
                announceMove(sib);
            }
            return true;
        }

        KeyGraph.MoveResult move = graph.moveToEdge(first ? GraphDir.UP : GraphDir.DOWN);
        if (move.moved) {
            announceMove(move);
        }
        return true;
    }

    private boolean regionJump(int dir) {
        KeyGraph.MoveResult result = graph.moveRegion(dir);
        if (!result.moved) {
            return true; // no region that way → consume
        }
        announceMove(result);
        return true;
    }

    private void announceMove(KeyGraph.MoveResult result) {
        GraphNode node = result.to;
        if (node == null) {
            return;
        }
        speak(GraphAnnouncer.compose(result.from, node, result.transitionLabel), true);
        lastSpokenKey = node.id;
        lastSpokenNode = node;
    }

    // Run the focused node's activation; speak its stateText as immediate
    // feedback when it declares one, and rebaseline the live watch so the
    // same change isn't spoken twice.
    private void vtableActivate() {
        GraphNode node = graph.currentNode();
        if (node == null || node.vtable.onActivate == null) {
            return;
        }
        graph.activate();
        node = graph.currentNode();
        Supplier<String> st = node != null ? node.vtable.stateText : null;
        if (st != null) {
            speak(st.get(), true);
            liveKey = null; // rebaseline: the change was just spoken synchronously
        }
    }

    private boolean vtableAdjust(int sign) {
        GraphNode node = graph.currentNode();
        if (node == null || node.vtable.onAdjust == null) {
            return false;
        }
        graph.tryAdjust(sign, false);
        node = graph.currentNode();
        Supplier<String> st = node != null ? node.vtable.stateText : null;
        if (st != null) {
            speak(st.get(), true);
            liveKey = null;
        }
        return true;
    }

    // ---- type-ahead (character feed comes from the input processor) ----

    private final TypeAheadSearch search = new TypeAheadSearch();
    private final List<GraphNode> searchNodes = new ArrayList<GraphNode>();
    private ControlId searchFocusId; // where the last result landed (staleness check)
    private int searchColumn = -1;

    /** Feed one typed character (letters and buffer-extending spaces). */
    public void typeChar(char c) {
        if (screen == null || !screen.allowsTypeahead() || graph == null || graph.currentNode() == null) {
            return;
        }
        if (!Character.isLetter(c) && !(c == ' ' && search.hasBuffer())) {
            return;
        }
        // A fresh search remembers the column you're on — every result lands
        // there. Captured before the first result moves focus to a primary.
        if (!search.hasBuffer()) {
            GraphNode node = graph.currentNode();
            searchColumn = node.vtable != null ? node.vtable.column : -1;
        }
        rebuildSearchScope();
        if (!searchNodes.isEmpty()) {
            search.addChar(c);
            search.search(searchNodes.size(), new IntFunction<String>() {
                @Override
                public String apply(int i) {
                    return searchTextOf(searchNodes.get(i));
                }
            }, new Consumer<Integer>() {
                @Override
                public void accept(Integer index) {
                    searchFocusNodeResult(index);
                }
            });
        }
    }

    public boolean searchActive() {
        return search.isSearchActive();
    }

    // The node's type-ahead text, stripped of the game's [tag] markup —
    // matching raw markup would demote tagged titles out of the starts-with
    // tier.
    private static String searchTextOf(GraphNode n) {
        String t;
        if (n.vtable.searchText != null) {
            t = n.vtable.searchText.get();
        } else {
            NodeAnnouncement first = n.vtable.announcements != null && !n.vtable.announcements.isEmpty()
                    ? n.vtable.announcements.get(0) : null;
            t = first != null && first.text != null ? first.text.get() : null;
        }
        return t == null ? null : TextFilter.clean(t);
    }

    private void rebuildSearchScope() {
        searchNodes.clear();
        // The searchable scope is the focused node's Tab-stop. Tabular rows
        // contribute ONE result — the primary — so result-arrows step through
        // rows, not cells.
        GraphNode node = graph != null ? graph.currentNode() : null;
        if (node == null || graph.current() == null) {
            return;
        }
        for (GraphNode n : graph.current().order) {
            if (java.util.Objects.equals(n.stopKey, node.stopKey)
                    && !n.vtable.excludeFromSearch && n.vtable.column <= 0) {
                searchNodes.add(n);
            }
        }
    }

    private void searchFocusNodeResult(int index) {
        if (index < 0 || index >= searchNodes.size()) {
            return;
        }
        if (!graph.focusAtColumn(searchNodes.get(index).id, searchColumn)) {
            return;
        }
        GraphNode node = graph.currentNode();
        speak(GraphAnnouncer.compose(lastSpokenNode, node), true);
        lastSpokenKey = node.id;
        lastSpokenNode = node;
        searchFocusId = node.id; // staleness check clears results if focus moves off
    }

    private void clearSearch() {
        search.clear();
        searchNodes.clear();
        searchFocusId = null;
        searchColumn = -1;
    }

    // ---- dev inspection (the /gui dump) ----

    public String devDump() {
        if (screen == null) {
            return "no access screen attached";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("screen: ").append(screen.key());
        if (screen.screenName() != null) {
            sb.append(" | ").append(screen.screenName());
        }
        sb.append('\n');
        if (graph == null || !graph.rerender()) {
            return sb.append("(empty graph)").toString();
        }
        ControlId focused = graph.currentNode() != null ? graph.currentNode().id : null;
        Object lastStop = new Object();
        Object lastRegion = new Object();
        for (GraphNode n : graph.current().order) {
            if (!java.util.Objects.equals(n.stopKey, lastStop)) {
                lastStop = n.stopKey;
                sb.append("-- stop: ").append(n.stopKey).append('\n');
                lastRegion = new Object();
            }
            if (!java.util.Objects.equals(n.regionKey, lastRegion)) {
                lastRegion = n.regionKey;
                if (n.regionKey != null) {
                    sb.append("-- region: ").append(n.regionKey).append('\n');
                }
            }
            sb.append(n.id.equals(focused) ? "> " : "  ");
            String text;
            try {
                text = GraphAnnouncer.leafText(n);
            } catch (Throwable t) {
                text = "<err: " + t + ">";
            }
            sb.append(text).append("  [").append(n.id).append("]\n");
        }
        return sb.toString();
    }
}
