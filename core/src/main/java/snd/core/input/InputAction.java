package snd.core.input;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import snd.contracts.SndLog;
import snd.core.nav.NavAction;

/**
 * One key of the mod's own: what it stands for, the chords bound to it, and
 * either the navigator action it sends or its own handler. The single source
 * for the input processor (chord to action), the key help (label and spoken
 * chord) and the dev driver. The game's hotkeys are not here: they are the
 * game's, reached by fall-through, and screens offer them to the key help
 * from live state ({@code AccessScreen.keys()}).
 */
public final class InputAction {
    public final String id;

    /** The action's label in the "ui" table: what the key does. */
    public final String labelKey;

    /** The navigator action this key sends, or null for a handler action. */
    public final NavAction nav;

    private final List<KeyChord> chords = new ArrayList<KeyChord>();
    private final List<KeyChord> aliases = new ArrayList<KeyChord>();
    private IntConsumer handler;
    private BooleanSupplier available;
    private IntSupplier digitCount;
    private boolean overOverlay;
    private boolean unlisted;

    private InputAction(String id, String labelKey, NavAction nav) {
        this.id = id;
        this.labelKey = labelKey;
        this.nav = nav;
    }

    public static InputAction nav(NavAction nav, String labelKey) {
        return new InputAction("nav." + nav.name(), labelKey, nav);
    }

    public static InputAction of(String id, String labelKey) {
        return new InputAction(id, labelKey, null);
    }

    public InputAction bind(KeyChord chord) {
        chords.add(chord);
        return this;
    }

    /** A second key for the same action that the key help leaves unsaid (Numpad Enter). */
    public InputAction alias(KeyChord chord) {
        aliases.add(chord);
        return this;
    }

    /** The handler; its argument is the digit's index for a digits chord, else -1. */
    public InputAction handle(IntConsumer h) {
        handler = h;
        return this;
    }

    /**
     * Where the key applies, asked at the key press and when the key help
     * opens: a glance is live only where its fact is on screen. Absent =
     * always.
     */
    public InputAction when(BooleanSupplier a) {
        available = a;
        return this;
    }

    /** For a digits chord: how many digits answer right now ("1 to 5"). */
    public InputAction digitCount(IntSupplier count) {
        digitCount = count;
        return this;
    }

    /** Still answers while an overlay of the mod's own holds every other key. */
    public InputAction overOverlay() {
        overOverlay = true;
        return this;
    }

    /** Left out of the key help (the help's own key). */
    public InputAction unlisted() {
        unlisted = true;
        return this;
    }

    public boolean worksOverOverlay() {
        return overOverlay;
    }

    public boolean isUnlisted() {
        return unlisted;
    }

    public boolean hasCondition() {
        return available != null;
    }

    /** A digits family has no single action to run from the key help. */
    public boolean isDigitFamily() {
        for (KeyChord c : chords) {
            if (c.isDigits()) {
                return true;
            }
        }
        return false;
    }

    public boolean isAvailable() {
        if (available == null) {
            return true;
        }
        try {
            return available.getAsBoolean();
        } catch (Throwable t) {
            SndLog.error("input action " + id + ": availability check failed", t);
            return false;
        }
    }

    public void perform(int digit) {
        handler.accept(digit);
    }

    List<KeyChord> allChords() {
        List<KeyChord> all = new ArrayList<KeyChord>(chords);
        all.addAll(aliases);
        return all;
    }

    /** The bound chords as spoken, aliases left out. */
    public String keysDisplay() {
        int count = digitCount != null ? digitCount.getAsInt() : 9;
        StringBuilder sb = new StringBuilder();
        for (KeyChord c : chords) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(c.display(count));
        }
        return sb.toString();
    }
}
