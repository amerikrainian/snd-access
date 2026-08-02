package snd.core.util;

import java.text.Normalizer;

/** Text helpers shared by search and speech. */
public final class TextUtil {
    private TextUtil() {
    }

    /** "é" → "e": NFD-decompose and drop the combining marks. */
    public static String removeDiacritics(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
