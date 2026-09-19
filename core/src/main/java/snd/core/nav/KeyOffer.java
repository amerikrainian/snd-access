package snd.core.nav;

/**
 * A key the attached screen offers right now beyond the navigator's own: a
 * game hotkey that works in this state, labelled by what it does HERE. Screens
 * return these from live game state ({@link AccessScreen#keys()}), so the key
 * help lists a key only where pressing it would act.
 */
public final class KeyOffer {
    /** Stable within the screen; the help row's identity. */
    public final String id;

    /** The key as spoken ("R", "1 to 5", "Shift+1 to 5"). */
    public final String keys;

    /** What the key does here. */
    public final String label;

    /**
     * Performs the key's action as the press would. Null for a key range
     * (the digits): listed, with nothing single to run from the help.
     */
    public final Runnable run;

    public KeyOffer(String id, String keys, String label, Runnable run) {
        this.id = id;
        this.keys = keys;
        this.label = label;
        this.run = run;
    }
}
