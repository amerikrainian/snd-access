package snd.core.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import snd.core.util.TextUtil;

/**
 * Type-ahead search engine — ported from wotr-access, itself from OniAccess;
 * pure matching/result state (speech and key routing live in the navigator,
 * which feeds typed characters and arrows in).
 *
 * <p>Builds a filtered results list over a flat item list with TIERED
 * matching: start-of-string whole word, start-of-string prefix, mid-string
 * whole word, mid-string word prefix, substring anywhere, then space-delimited
 * word-prefix abbreviation ("ga pi" matches "gas pipe"). Within a tier, items
 * keep their LIST ORDER, and matches in the item's NAME (before the first
 * comma) rank ahead of matches in its appended metadata. Diacritics are
 * ignored. Typing the same letter repeatedly cycles ALL of that letter's
 * matches in list order.</p>
 */
public final class TypeAheadSearch {
    private final StringBuilder buffer = new StringBuilder(32);

    private boolean searchActive;
    private List<Integer> resultIndices = new ArrayList<Integer>();
    private List<String> resultNames = new ArrayList<String>();
    private int resultCursor;

    private static final int TIER_COUNT = 6;
    private final List<List<Integer>> tierIndices = new ArrayList<List<Integer>>();
    private final List<List<String>> tierNames = new ArrayList<List<String>>();
    private final List<List<Integer>> tierInSegment = new ArrayList<List<Integer>>();
    private List<Integer> workIndices = new ArrayList<Integer>();
    private List<String> workNames = new ArrayList<String>();

    /** Announce-and-move callback (called with the ORIGINAL item index). */
    private Consumer<Integer> announceResult;
    /** Spoken when the buffer matches nothing (gets the buffer text). */
    public Consumer<String> onNoMatch;

    public TypeAheadSearch() {
        for (int t = 0; t < TIER_COUNT; t++) {
            tierIndices.add(new ArrayList<Integer>());
            tierNames.add(new ArrayList<String>());
            tierInSegment.add(new ArrayList<Integer>());
        }
    }

    public String buffer() {
        return buffer.toString();
    }

    public boolean hasBuffer() {
        return buffer.length() > 0;
    }

    public boolean isSearchActive() {
        return searchActive;
    }

    public int resultCount() {
        return resultIndices.size();
    }

    public void addChar(char c) {
        buffer.append(c);
    }

    public boolean removeChar() {
        if (buffer.length() == 0) {
            return false;
        }
        buffer.setLength(buffer.length() - 1);
        return true;
    }

    public void clear() {
        buffer.setLength(0);
        searchActive = false;
        resultIndices.clear();
        resultNames.clear();
        resultCursor = 0;
        announceResult = null;
    }

    /** Run the tiered search over the items and move/announce the best result. */
    public void search(int itemCount, IntFunction<String> nameByIndex, Consumer<Integer> announceResult) {
        // Repeat single-letter: typing the same letter again cycles ALL its
        // matches in list order, wrapping.
        String bufferStr = buffer.toString();
        if (searchActive && !resultIndices.isEmpty() && buffer.length() > 1 && isAllSameChar(bufferStr)) {
            buffer.setLength(1);
            if (announceResult != null) {
                this.announceResult = announceResult;
            }
            navigateResults(1);
            return;
        }

        if (announceResult != null) {
            this.announceResult = announceResult;
        }

        String trimmed = trimEnd(bufferStr);
        if (!hasBuffer() || itemCount == 0 || trimmed.isEmpty()) {
            resultIndices.clear();
            resultNames.clear();
            resultCursor = 0;
            searchActive = true;
            if (onNoMatch != null) {
                onNoMatch.accept(bufferStr);
            }
            return;
        }

        for (int t = 0; t < TIER_COUNT; t++) {
            tierIndices.get(t).clear();
            tierNames.get(t).clear();
            tierInSegment.get(t).clear();
        }
        String lowerBuffer = trimmed.toLowerCase();

        int[] pos = new int[1];
        for (int i = 0; i < itemCount; i++) {
            String name = nameByIndex.apply(i);
            if (name == null || name.isEmpty()) {
                continue;
            }
            int tier = matchTier(name.toLowerCase(), lowerBuffer, pos);
            if (tier >= 0) {
                tierIndices.get(tier).add(i);
                tierNames.get(tier).add(name);
                // Matches inside the name (before the first comma) rank ahead
                // of matches inside the appended metadata.
                int comma = name.indexOf(',');
                int nameLen = comma >= 0 ? comma : name.length();
                tierInSegment.get(tier).add(pos[0] < nameLen ? 0 : 1);
            }
        }

        // Within a tier, entries stay in ITEM order. Merge: name (pre-comma)
        // matches across all tiers before metadata (post-comma) matches.
        workIndices.clear();
        workNames.clear();
        for (int inSeg = 0; inSeg <= 1; inSeg++) {
            for (int t = 0; t < TIER_COUNT; t++) {
                for (int i = 0; i < tierIndices.get(t).size(); i++) {
                    if (tierInSegment.get(t).get(i) == inSeg) {
                        workIndices.add(tierIndices.get(t).get(i));
                        workNames.add(tierNames.get(t).get(i));
                    }
                }
            }
        }

        if (workIndices.isEmpty()) {
            resultIndices.clear();
            resultNames.clear();
            resultCursor = 0;
            searchActive = true;
            if (onNoMatch != null) {
                onNoMatch.accept(bufferStr);
            }
        } else {
            List<Integer> tempIndices = resultIndices;
            List<String> tempNames = resultNames;
            resultIndices = workIndices;
            resultNames = workNames;
            workIndices = tempIndices;
            workNames = tempNames;
            resultCursor = 0;
            searchActive = true;
            announceCurrentResult();
        }
    }

    /** Step within the filtered results (wrapping). +1 next, -1 previous. */
    public void navigateResults(int direction) {
        if (resultIndices.isEmpty()) {
            return;
        }
        int count = resultIndices.size();
        resultCursor = ((resultCursor + direction) % count + count) % count;
        announceCurrentResult();
    }

    public void jumpToFirstResult() {
        if (resultIndices.isEmpty()) {
            return;
        }
        resultCursor = 0;
        announceCurrentResult();
    }

    public void jumpToLastResult() {
        if (resultIndices.isEmpty()) {
            return;
        }
        resultCursor = resultIndices.size() - 1;
        announceCurrentResult();
    }

    private void announceCurrentResult() {
        if (resultIndices.isEmpty() || announceResult == null) {
            return;
        }
        announceResult.accept(resultIndices.get(resultCursor));
    }

    private static boolean isAllSameChar(String s) {
        char first = s.charAt(0);
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    private static String trimEnd(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }

    /**
     * Match tier for a prefix against a name (both lowercase), or -1. 0 =
     * start whole word, 1 = start prefix, 2 = mid whole word, 3 = mid word
     * prefix, 4 = substring anywhere, 5 = space-delimited word-prefix
     * abbreviation. position[0] receives the match position.
     */
    static int matchTier(String lowerName, String lowerPrefix, int[] position) {
        position[0] = -1;
        lowerName = TextUtil.removeDiacritics(lowerName);
        lowerPrefix = TextUtil.removeDiacritics(lowerPrefix);
        int prefixLen = lowerPrefix.length();
        if (prefixLen > lowerName.length()) {
            return -1;
        }

        if (lowerName.regionMatches(0, lowerPrefix, 0, prefixLen)) {
            position[0] = 0;
            boolean wholeWord = lowerName.length() == prefixLen
                    || lowerName.charAt(prefixLen) == ' ' || lowerName.charAt(prefixLen) == ',';
            return wholeWord ? 0 : 1;
        }

        for (int i = 1; i < lowerName.length(); i++) {
            char prev = lowerName.charAt(i - 1);
            if (prev != ' ' && prev != ',') {
                continue;
            }
            if (lowerName.charAt(i) == ' ') {
                continue;
            }
            if (lowerName.length() - i < prefixLen) {
                break;
            }
            if (lowerName.regionMatches(i, lowerPrefix, 0, prefixLen)) {
                int afterMatch = i + prefixLen;
                boolean wholeWord = afterMatch >= lowerName.length()
                        || lowerName.charAt(afterMatch) == ' ' || lowerName.charAt(afterMatch) == ',';
                position[0] = i;
                return wholeWord ? 2 : 3;
            }
        }

        int idx = lowerName.indexOf(lowerPrefix);
        if (idx >= 0) {
            position[0] = idx;
            return 4;
        }

        if (lowerPrefix.indexOf(' ') >= 0) {
            int abbrevPos = matchWordPrefixTokens(lowerName, lowerPrefix);
            if (abbrevPos >= 0) {
                position[0] = abbrevPos;
                return 5;
            }
        }

        return -1;
    }

    // Position of the first matched word if every space-delimited token in the
    // prefix is a prefix of a distinct word in the name, consumed in order,
    // all within one comma-delimited segment.
    private static int matchWordPrefixTokens(String lowerName, String lowerPrefix) {
        String[] rawTokens = lowerPrefix.split(" ");
        int tokenCount = 0;
        for (String rawToken : rawTokens) {
            if (!rawToken.isEmpty()) {
                rawTokens[tokenCount++] = rawToken;
            }
        }
        if (tokenCount == 0) {
            return -1;
        }

        int tokenIdx = 0;
        int firstPos = -1;
        int i = 0;
        while (i < lowerName.length()) {
            char c = lowerName.charAt(i);
            if (c == ',') {
                tokenIdx = 0;
                firstPos = -1;
                i++;
                continue;
            }
            if (c == ' ') {
                i++;
                continue;
            }

            if (tokenIdx < tokenCount) {
                String token = rawTokens[tokenIdx];
                boolean fits = i + token.length() <= lowerName.length()
                        && lowerName.regionMatches(i, token, 0, token.length());
                if (fits) {
                    if (tokenIdx == 0) {
                        firstPos = i;
                    }
                    tokenIdx++;
                    if (tokenIdx == tokenCount) {
                        return firstPos;
                    }
                    i += token.length();
                }
            }
            while (i < lowerName.length() && lowerName.charAt(i) != ' ' && lowerName.charAt(i) != ',') {
                i++;
            }
        }

        return -1;
    }
}
