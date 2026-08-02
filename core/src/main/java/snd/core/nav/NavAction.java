package snd.core.nav;

/** The navigator's logical input vocabulary (keycode mapping lives in the module). */
public enum NavAction {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    NEXT_STOP,
    PREV_STOP,
    HOME,
    END,
    REGION_PREV,
    REGION_NEXT,
    ACTIVATE,
    SECONDARY,
    TOOLTIP,
    /** Escape: consumed only when it has something of ours to cancel (a live search). */
    CANCEL
}
