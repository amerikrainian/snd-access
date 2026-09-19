package snd.core.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The mod's own keys, in registration order (which is the key help's order
 * within a group). A key press resolves to the action whose chord matches
 * with the most required modifiers: Ctrl+Up is the region jump, Up and
 * Shift+Up the plain move. No match = not ours; the press falls through to
 * the game.
 */
public final class InputRegistry {
    private final List<InputAction> actions = new ArrayList<InputAction>();

    public InputAction register(InputAction action) {
        for (InputAction existing : actions) {
            if (existing.id.equals(action.id)) {
                throw new IllegalArgumentException("input action registered twice: " + action.id);
            }
            for (KeyChord mine : action.allChords()) {
                for (KeyChord theirs : existing.allChords()) {
                    if (mine.sameChord(theirs)) {
                        throw new IllegalArgumentException(
                                "chord of " + action.id + " is already bound to " + existing.id);
                    }
                }
            }
        }
        actions.add(action);
        return action;
    }

    public List<InputAction> actions() {
        return Collections.unmodifiableList(actions);
    }

    /** digit: the pressed key's digit index (0 for "1"), or -1 for a non-digit key. */
    public InputAction match(int keycode, int digit, boolean shift, boolean ctrl) {
        InputAction best = null;
        int bestScore = -1;
        for (InputAction action : actions) {
            for (KeyChord chord : action.allChords()) {
                if (chord.matches(keycode, digit, shift, ctrl) && chord.specificity() > bestScore) {
                    best = action;
                    bestScore = chord.specificity();
                }
            }
        }
        return best;
    }
}
