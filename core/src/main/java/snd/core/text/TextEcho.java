package snd.core.text;

import snd.core.loc.Loc;

/**
 * What a text field's change since the last look sounds like. An edit
 * echoes the text it inserted, or what it removed (a typed character, a
 * paste, a Backspace), bare. A caret move reads the character it lands on,
 * or the word after a word jump. A caret key that moved nothing (Left at the
 * start) re-reads where the caret stands. A selection that grows or shrinks
 * reads the span that changed, as selected or unselected. The first look at
 * a field only takes its state in: the field's own announcement says what it
 * holds. A line key reads the whole text.
 */
public final class TextEcho {
    private boolean seated;
    private String text = "";
    private int caret;
    private int anchor = -1;

    /** Forget the field: the next look takes its state in silently. */
    public void reset() {
        seated = false;
    }

    /**
     * The words for what changed since the last look, or null for nothing to
     * say. {@code move} is the caret key pressed since the last look.
     */
    public String observe(String newText, int newCaret, int newAnchor, CaretMove move) {
        if (newText == null) {
            newText = "";
        }
        newCaret = Math.max(0, Math.min(newText.length(), newCaret));
        String oldText = text;
        int oldCaret = caret;
        int oldAnchor = anchor;
        boolean wasSeated = seated;
        seated = true;
        text = newText;
        caret = newCaret;
        anchor = newAnchor;
        if (!wasSeated) {
            return null;
        }
        if (!oldText.equals(newText)) {
            return edit(oldText, newText);
        }
        if (newCaret != oldCaret) {
            // Extending: the anchor stood still, or was just dropped where
            // the caret was. A click seats the anchor at the NEW caret.
            boolean extending = newAnchor >= 0 && (newAnchor == oldAnchor || newAnchor == oldCaret);
            return extending ? selection(newText, oldCaret, newCaret, newAnchor) : at(newText, newCaret, move);
        }
        return move != CaretMove.NONE ? at(newText, newCaret, move) : null;
    }

    // The inserted middle (a keystroke, a paste) or the removed one, by common
    // prefix and suffix; typing over a selection reads what was typed.
    private static String edit(String before, String after) {
        int max = Math.min(before.length(), after.length());
        int prefix = 0;
        while (prefix < max && before.charAt(prefix) == after.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < max - prefix
                && before.charAt(before.length() - 1 - suffix) == after.charAt(after.length() - 1 - suffix)) {
            suffix++;
        }
        String inserted = after.substring(prefix, after.length() - suffix);
        String removed = before.substring(prefix, before.length() - suffix);
        return echo(inserted.isEmpty() ? removed : inserted);
    }

    private static String selection(String text, int from, int to, int anchor) {
        boolean crossed = (long) (from - anchor) * (to - anchor) < 0;
        if (crossed) {
            // Jumped over the anchor: the old span is dropped, the new one is all there is.
            return Loc.get("ui", "text.selected", "text", echo(span(text, anchor, to)));
        }
        boolean grew = Math.abs(to - anchor) > Math.abs(from - anchor);
        return Loc.get("ui", grew ? "text.selected" : "text.unselected", "text", echo(span(text, from, to)));
    }

    private static String span(String text, int a, int b) {
        return text.substring(Math.min(a, b), Math.max(a, b));
    }

    private static String at(String text, int caret, CaretMove move) {
        if (move == CaretMove.LINE) {
            return text.isEmpty() ? Loc.get("ui", "text.blank") : text;
        }
        return move == CaretMove.WORD ? wordAt(text, caret) : charAt(text, caret);
    }

    /** The character at the caret; past the last one, the field's end is blank. */
    public static String charAt(String text, int caret) {
        if (caret >= text.length()) {
            return Loc.get("ui", "text.blank");
        }
        return echo(String.valueOf(text.charAt(caret)));
    }

    /** The run of letters and digits starting at the caret, or the one character there. */
    public static String wordAt(String text, int caret) {
        if (caret >= text.length() || !Character.isLetterOrDigit(text.charAt(caret))) {
            return charAt(text, caret);
        }
        int end = caret;
        while (end < text.length() && Character.isLetterOrDigit(text.charAt(end))) {
            end++;
        }
        return text.substring(caret, end);
    }

    /** Text as heard: whitespace says "space", a lone capital says so. */
    public static String echo(String s) {
        if (s.trim().isEmpty()) {
            return Loc.get("ui", "text.space");
        }
        if (s.length() == 1 && Character.isUpperCase(s.charAt(0))) {
            return Loc.get("ui", "text.capital", "letter", s);
        }
        return s;
    }
}
