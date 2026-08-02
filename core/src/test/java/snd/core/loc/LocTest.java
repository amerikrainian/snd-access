package snd.core.loc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocTest {

    private static Map<String, Map<String, String>> tables(String table, String... kv) {
        Map<String, String> t = new HashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            t.put(kv[i], kv[i + 1]);
        }
        Map<String, Map<String, String>> m = new HashMap<String, Map<String, String>>();
        m.put(table, t);
        return m;
    }

    @BeforeEach
    void reset() {
        Loc.installFallback(tables("ui",
                "greeting", "hello",
                "position", "{index} of {count}",
                "role.button", "button",
                "en_only", "english only"));
        Loc.install(Loc.FALLBACK_LANGUAGE, Collections.<String, Map<String, String>>emptyMap());
    }

    @Test
    void resolvesFromFallbackInEnglish() {
        assertEquals("hello", Loc.get("ui", "greeting"));
        assertEquals("button", Loc.get("ui", "role.button"));
    }

    @Test
    void substitutesNamedVariables() {
        assertEquals("2 of 7", Loc.get("ui", "position", "index", 2, "count", 7));
    }

    @Test
    void leavesUnmatchedSlotsVisible() {
        assertEquals("2 of {count}", Loc.get("ui", "position", "index", 2));
    }

    @Test
    void currentLanguageWinsOverFallback() {
        Loc.install("de", tables("ui", "greeting", "hallo"));
        assertEquals("de", Loc.language());
        assertEquals("hallo", Loc.get("ui", "greeting"));
    }

    @Test
    void untranslatedKeyReadsInEnglishNotAsAKey() {
        Loc.install("de", tables("ui", "greeting", "hallo"));
        assertEquals("english only", Loc.get("ui", "en_only"));
    }

    @Test
    void missingKeyResolvesToTheKeyItself() {
        assertEquals("nope.missing", Loc.get("ui", "nope.missing"));
        assertEquals("no table", Loc.get("ghost", "no table"));
    }

    @Test
    void getOrDefaultIsQuietAndFormats() {
        assertEquals("Dungeon Screen",
                Loc.getOrDefault("ui", "screen.DungeonScreen", "Dungeon Screen"));
        Loc.installFallback(tables("ui", "screen.X", "The {thing}"));
        assertEquals("The die", Loc.getOrDefault("ui", "screen.X", "x", "thing", "die"));
    }

    @Test
    void switchingBackToEnglishDropsTheOverride() {
        Loc.install("de", tables("ui", "greeting", "hallo"));
        Loc.install(Loc.FALLBACK_LANGUAGE, Collections.<String, Map<String, String>>emptyMap());
        assertEquals("hello", Loc.get("ui", "greeting"));
    }
}
