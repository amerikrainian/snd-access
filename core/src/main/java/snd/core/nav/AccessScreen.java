package snd.core.nav;

import java.util.Collections;
import java.util.List;

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

    /** The stop initial focus should land in, or null for the start node. */
    public Object initialFocusStop() {
        return null;
    }

    /**
     * The keys that act here right now beyond the navigator's own (the game's
     * hotkeys for the current state), read fresh from live game state each
     * call. The key help lists them first.
     */
    public List<KeyOffer> keys() {
        return Collections.emptyList();
    }

    /**
     * Escape with no search to cancel. True = the screen took it; false lets
     * it fall through to the game.
     */
    public boolean onCancel() {
        return false;
    }

    /**
     * While attached, keys the navigator leaves unconsumed stay with the mod
     * instead of falling through to the game (an overlay of the mod's own,
     * with the game's screen still live underneath).
     */
    public boolean exclusive() {
        return false;
    }
}
