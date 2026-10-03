package snd.core.text;

/** The kind of caret key last pressed in a text field: what a caret landing reads. */
public enum CaretMove {
    /** No caret key: a caret change reads the character it lands on. */
    NONE,
    /** One character, or the field's start or end: the character at the caret. */
    CHAR,
    /** A word jump: the word at the caret. */
    WORD,
    /** A line key (a single-line field's Up and Down): the whole text. */
    LINE
}
