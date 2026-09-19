package snd.core.input;

import snd.core.loc.Loc;

/**
 * A key with the modifiers it requires. Keycodes are the engine's (the module
 * registers them); the spoken name is a Loc key, so the key help says the
 * chord the table actually binds. A {@link #digits()} chord is a family: any
 * digit key matches, and the digit's index (0 for "1") reaches the handler.
 */
public final class KeyChord {
    private static final int DIGITS = Integer.MIN_VALUE;

    public final int keycode;
    public final boolean shift;
    public final boolean ctrl;
    private final String nameKey;

    private KeyChord(int keycode, boolean shift, boolean ctrl, String nameKey) {
        this.keycode = keycode;
        this.shift = shift;
        this.ctrl = ctrl;
        this.nameKey = nameKey;
    }

    /** nameKey: the key's spoken name in the "ui" table ("key.up"). */
    public static KeyChord of(int keycode, String nameKey) {
        return new KeyChord(keycode, false, false, nameKey);
    }

    public static KeyChord digits() {
        return new KeyChord(DIGITS, false, false, null);
    }

    public KeyChord shift() {
        return new KeyChord(keycode, true, ctrl, nameKey);
    }

    public KeyChord ctrl() {
        return new KeyChord(keycode, shift, true, nameKey);
    }

    public boolean isDigits() {
        return keycode == DIGITS;
    }

    /**
     * The more modifiers a chord requires, the more specific; at the same
     * modifiers a named key beats the digits family (Ctrl+1 bound by itself
     * wins over a Ctrl+digits chord).
     */
    int specificity() {
        return 2 * ((shift ? 1 : 0) + (ctrl ? 1 : 0)) + (isDigits() ? 0 : 1);
    }

    /**
     * Held modifiers the chord does not ask for are ignored (Shift+Up still
     * moves up); the registry prefers the most specific chord that matches.
     */
    boolean matches(int pressedKeycode, int pressedDigit, boolean shiftHeld, boolean ctrlHeld) {
        if (shift && !shiftHeld || ctrl && !ctrlHeld) {
            return false;
        }
        return isDigits() ? pressedDigit >= 0 : keycode == pressedKeycode;
    }

    boolean sameChord(KeyChord other) {
        return keycode == other.keycode && shift == other.shift && ctrl == other.ctrl;
    }

    /** "1 to n", capped at the nine digit keys; "1" for a single one. */
    public static String digitsName(int n) {
        n = Math.min(n, 9);
        return n == 1 ? "1" : Loc.get("ui", "key.digits", "n", n);
    }

    /** The chord as spoken: "Ctrl+Shift+1 to 5", "Shift+Tab". */
    public String display(int digitCount) {
        String name = isDigits() ? digitsName(digitCount) : Loc.get("ui", nameKey);
        if (shift) {
            name = Loc.get("ui", "key.shift", "key", name);
        }
        if (ctrl) {
            name = Loc.get("ui", "key.ctrl", "key", name);
        }
        return name;
    }
}
