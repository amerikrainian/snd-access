package snd.core.graph;

import java.util.List;
import java.util.function.Supplier;

/**
 * The behaviors of a control, as data. {@link #announcements} is required (its
 * parts compose the spoken focus readout; the first part is the control's
 * label for search/dedupe purposes); the rest are optional — a null slot means
 * the control doesn't have that behavior and the navigator speaks its "nothing
 * there" feedback instead.
 */
public final class NodeVtable {
    /** Horizontal value adjust (a slider). */
    public interface Adjust {
        /** sign is -1 (decrease) / +1 (increase); large = coarse step. */
        void adjust(int sign, boolean large);
    }

    /**
     * Required, at least one part. Parts marked live re-speak on change while
     * focused. When {@link #controlType} is set, the type's common parts merge
     * in and the type's kind order applies; otherwise parts speak in
     * declaration order.
     */
    public List<NodeAnnouncement> announcements;

    /** The control's type (registry value). Null = an untyped one-off. */
    public ControlType controlType;

    /** Optional. Primary activation — the left-click equivalent (Enter). */
    public Runnable onActivate;

    /** Optional. Secondary activation — right-click equivalent (Backspace). */
    public Runnable onSecondary;

    /**
     * Optional. Read/open the control's tooltip. The action owns the whole
     * behavior, so the core stays game-agnostic.
     */
    public Runnable onTooltip;

    /** Optional. Drag/drop participation; the action owns the state machine. */
    public Runnable onDrag;

    /** Optional. When set, Left/Right adjust instead of navigating. */
    public Adjust onAdjust;

    /**
     * Optional. The control's state line, spoken IMMEDIATELY (interrupting)
     * after an activation/adjust — the synchronous feedback path. Async
     * game-driven changes ride the live announcement watch instead.
     */
    public Supplier<String> stateText;

    /** Optional. Type-ahead match text; null = the first announcement part. */
    public Supplier<String> searchText;

    /** If true, type-ahead never matches this control. */
    public boolean excludeFromSearch;

    /**
     * Optional (expandable groups): override HOW expansion state changes. When
     * null the engine mutates the persistent expansion set
     * ({@link GraphState#expanded}).
     */
    public Runnable onExpand;
    public Runnable onCollapse;

    /**
     * Set when a group's own announcements already include its
     * expanded/collapsed state, so the announcer doesn't append it again.
     */
    public boolean speaksOwnExpansion;

    /**
     * Set when the node's announcements already include its list position, so
     * the announcer doesn't append the auto-stamped one.
     */
    public boolean speaksOwnPosition;

    /**
     * The node's LOGICAL COLUMN in a tabular row (0 = the row's primary), or
     * -1 when not tabular. Stamped by GraphSheet; the engine uses it to
     * preserve the column when focus jumps non-directionally (reconcile
     * fallback after a row vanishes, type-ahead landings).
     */
    public int column = -1;
}
