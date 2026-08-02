package snd.core.search;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeAheadSearchTest {
    private static int tier(String name, String prefix) {
        int[] pos = new int[1];
        return TypeAheadSearch.matchTier(name, prefix, pos);
    }

    @Test
    void matchTiersRankAsSpecified() {
        assertEquals(0, tier("load game", "load"));      // start whole word
        assertEquals(1, tier("load game", "loa"));       // start prefix
        assertEquals(2, tier("gas pipe", "pipe"));       // mid whole word
        assertEquals(3, tier("gas pipe", "pip"));        // mid word prefix
        assertEquals(4, tier("blathering", "lather"));   // substring anywhere
        assertEquals(5, tier("gas pipe", "ga pi"));      // abbreviation
        assertEquals(-1, tier("dice", "zzz"));
    }

    @Test
    void diacriticsAreIgnored() {
        assertEquals(0, tier("séance", "seance"));
        assertEquals(1, tier("séance", "se"));
    }

    private static List<Integer> run(TypeAheadSearch s, final List<String> items, String typed) {
        final List<Integer> landed = new ArrayList<Integer>();
        for (char c : typed.toCharArray()) {
            s.addChar(c);
            s.search(items.size(), new IntFunction<String>() {
                @Override
                public String apply(int i) {
                    return items.get(i);
                }
            }, new Consumer<Integer>() {
                @Override
                public void accept(Integer index) {
                    landed.add(index);
                }
            });
        }
        return landed;
    }

    @Test
    void resultsKeepListOrderWithinTier() {
        // "l" must land on Load Game by menu position, not License by length.
        TypeAheadSearch s = new TypeAheadSearch();
        List<Integer> landed = run(s, Arrays.asList("Continue", "Load Game", "License", "DLC"), "l");
        assertEquals(Arrays.asList(1), landed);
    }

    @Test
    void repeatLetterCyclesMatches() {
        TypeAheadSearch s = new TypeAheadSearch();
        List<Integer> landed = run(s, Arrays.asList("Continue", "Load Game", "License", "DLC"), "lll");
        // l → Load Game (starts-with), l → License (starts-with), l → DLC (substring)
        assertEquals(Arrays.asList(1, 2, 3), landed);
    }

    @Test
    void nameMatchesRankAheadOfMetadata() {
        TypeAheadSearch s = new TypeAheadSearch();
        // "shield" in the metadata of item 0, in the name of item 1.
        List<Integer> landed = run(s,
                Arrays.asList("Sword, grants shield", "Shield of Valor, sturdy"), "shield");
        assertEquals(Integer.valueOf(1), landed.get(landed.size() - 1));
    }

    @Test
    void resultNavigationWraps() {
        TypeAheadSearch s = new TypeAheadSearch();
        final List<String> items = Arrays.asList("alpha", "amber", "boop");
        final List<Integer> landed = new ArrayList<Integer>();
        s.addChar('a');
        s.search(items.size(), new IntFunction<String>() {
            @Override
            public String apply(int i) {
                return items.get(i);
            }
        }, new Consumer<Integer>() {
            @Override
            public void accept(Integer index) {
                landed.add(index);
            }
        });
        s.navigateResults(1);
        s.navigateResults(1); // wraps back to the first
        assertEquals(Arrays.asList(0, 1, 0), landed);
    }

    @Test
    void noMatchFiresCallback() {
        TypeAheadSearch s = new TypeAheadSearch();
        final List<String> misses = new ArrayList<String>();
        s.onNoMatch = new Consumer<String>() {
            @Override
            public void accept(String text) {
                misses.add(text);
            }
        };
        run(s, Arrays.asList("alpha"), "zz");
        assertEquals(2, misses.size());
    }

    @Test
    void clearResetsEverything() {
        TypeAheadSearch s = new TypeAheadSearch();
        run(s, Arrays.asList("alpha"), "a");
        s.clear();
        assertEquals("", s.buffer());
        assertEquals(0, s.resultCount());
    }
}
