package snd.core.text;

import java.util.function.Predicate;

/**
 * A TextMod syntax line as text to hear: the game draws the parts typed
 * exactly in one colour and the tokens to replace in others, so the line
 * reads with each token in angle brackets: "hero.&lt;keyword&gt;". Adjacent
 * tokens, whatever their colours, share one pair of brackets.
 *
 * <p>Markup is the game's: {@code [name]} sets a colour when the name is one,
 * {@code [cu]} steps back one colour through the two the writer remembers
 * (TextWriter keeps the current colour and two before it), and any other tag
 * (spacing, bold, translation marks) draws nothing.</p>
 */
public final class MarkedSyntax {
    private MarkedSyntax() {
    }

    /**
     * The line with its tokens bracketed. {@code isColour} says whether a tag
     * name sets a colour; {@code isLiteral} whether that colour is the one the
     * game draws typed-as-is text in. Text before any colour is a token.
     */
    public static String spoken(String doc, Predicate<String> isColour, Predicate<String> isLiteral) {
        StringBuilder out = new StringBuilder();
        String current = null;
        String previous = null;
        String beforeThat = null;
        boolean inToken = false;
        int i = 0;
        while (i < doc.length()) {
            char c = doc.charAt(i);
            int close = c == '[' ? doc.indexOf(']', i) : -1;
            if (close > i) {
                String tag = doc.substring(i + 1, close);
                if (tag.equals("cu")) {
                    current = previous;
                    previous = beforeThat;
                } else if (isColour.test(tag)) {
                    beforeThat = previous;
                    previous = current;
                    current = tag;
                }
                i = close + 1;
                continue;
            }
            boolean token = current == null || !isLiteral.test(current);
            if (token != inToken) {
                out.append(token ? '<' : '>');
                inToken = token;
            }
            out.append(c);
            i++;
        }
        if (inToken) {
            out.append('>');
        }
        return out.toString();
    }
}
