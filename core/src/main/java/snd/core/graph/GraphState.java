package snd.core.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The persistent cursor for a graph — the only thing that survives between
 * renders. Holds where focus is, the last computed traversal order (for
 * closest-survivor recovery), per-stop remembered positions, and a one-shot
 * move request.
 */
public final class GraphState {
    /** The focused control's id (carries its reference for tier-1 recovery). */
    public ControlId curKey;

    /** The down-right total order from the previous render. Null on first render. */
    public List<ControlId> keyOrder;

    /** If set, focus jumps here on the next render when present (consumed either way). */
    public ControlId nextSuggestedMove;

    /**
     * The logical column focus last sat on in a tabular row, or -1. The
     * reconcile fallback slides to this column when the focused row vanished.
     */
    public int lastColumn = -1;

    /**
     * The Tab-stop focus last sat in and its place among that stop's nodes.
     * When the focused node is gone but its stop is not, the reconcile
     * fallback stays in the stop at this place rather than leaving it.
     */
    public Object lastStopKey;
    public int lastStopIndex = -1;

    /** Remembered position per Tab-stop: where Tab lands when cycling back in. */
    public final Map<Object, ControlId> stopMemory = new HashMap<Object, ControlId>();

    /**
     * The expanded groups (by id). The builder consults this for groups
     * declared without an explicit state; the engine's expand/collapse
     * operations mutate it. Screens hold NO expansion state of their own.
     */
    public final Set<ControlId> expanded = new HashSet<ControlId>();
}
