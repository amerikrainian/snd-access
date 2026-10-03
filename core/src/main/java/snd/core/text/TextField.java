package snd.core.text;

/**
 * A text field's editing state, read from the game's own widget whenever the
 * navigator watches it (every frame while the field is focused).
 */
public interface TextField {
    String text();

    int caret();

    /** The selection's fixed end, or -1 when nothing is selected. */
    int anchor();
}
