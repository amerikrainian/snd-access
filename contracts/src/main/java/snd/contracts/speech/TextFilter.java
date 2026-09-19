package snd.contracts.speech;

/**
 * Cleans Slice &amp; Dice's inline markup out of text before it is spoken. The
 * game's TextWriter/TannFont markup uses square-bracket tags: colour tags
 * ([green], [cu], [text], [light], hex-ish colour triples), layout tags ([n] =
 * newline, [b] = border), image tags ([hp], [tinyDice], [reroll]...), and
 * [notranslate]. A synthesizer must hear none of them.
 */
public final class TextFilter {
    private TextFilter() {
    }

    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        int i = 0;
        int n = raw.length();
        while (i < n) {
            char c = raw.charAt(i);
            if (c == '[') {
                int close = raw.indexOf(']', i + 1);
                // Only treat it as a tag when it closes quickly; a real '['
                // in prose (rare) survives.
                if (close > i && close - i <= 24) {
                    String tag = raw.substring(i + 1, close);
                    if (tag.equals("n")) {
                        out.append('\n');
                    }
                    // every other tag (colours, images, [b], [notranslate], ...) is dropped
                    i = close + 1;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return collapseWhitespace(stripEmptyParens(out.toString()));
    }

    // Image-tag runs that lived inside parentheses ("([hp][p][hp][p])" —
    // status magnitudes draw as pip icons) leave empty shells once the tags
    // are stripped; drop the shells.
    private static String stripEmptyParens(String s) {
        return s.indexOf('(') < 0 ? s : s.replaceAll("\\(\\s*\\)", "");
    }

    private static String collapseWhitespace(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean pendingSpace = false;
        boolean pendingNewline = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                pendingNewline = true;
            } else if (Character.isWhitespace(c)) {
                pendingSpace = true;
            } else {
                if (out.length() > 0) {
                    if (pendingNewline) {
                        out.append('\n');
                    } else if (pendingSpace) {
                        out.append(' ');
                    }
                }
                pendingSpace = false;
                pendingNewline = false;
                out.append(c);
            }
        }
        return out.toString();
    }
}
