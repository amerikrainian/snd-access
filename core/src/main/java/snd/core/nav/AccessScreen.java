package snd.core.nav;

import snd.core.graph.GraphBuilder;

/**
 * One accessibility screen: a poll-activated, immediate-mode graph
 * declaration. {@link #isActive()} is polled every frame against live game
 * state; {@link #build} is called on every render and must declare controls
 * fresh from live game state each call (no retained tree, no rebuild
 * bookkeeping) — focus persists by ControlId identity.
 */
public abstract class AccessScreen {
    /** Stable identity for stack diffing and logs. */
    public abstract String key();

    /** Polled every frame. Exceptions are treated as inactive (and logged). */
    public abstract boolean isActive();

    /** Declare the screen's graph. Immediate mode: fresh per render. */
    public abstract void build(GraphBuilder b);

    /** Spoken when the screen gains the navigator. Null = silent. */
    public String screenName() {
        return null;
    }

    /** Stack order among simultaneously-active screens; highest wins. */
    public int layer() {
        return 0;
    }

    public boolean allowsTypeahead() {
        return true;
    }

    /** Tab wraps last→first stop. */
    public boolean wrap() {
        return false;
    }

    /** The stop initial focus should land in, or null for the start node. */
    public Object initialFocusStop() {
        return null;
    }
}
