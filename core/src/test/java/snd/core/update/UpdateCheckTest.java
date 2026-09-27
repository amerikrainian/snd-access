package snd.core.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The version rules behind the launch update announcement: what the release
 * payload names, and that only a strictly newer release ever counts. Ported
 * from Guildrun Access with the suite.
 */
class UpdateCheckTest {

    @Test
    void latestVersionReadsTheTagAndStripsTheV() {
        String json = "{\"url\":\"x\",\"tag_name\":\"v0.2.2\",\"name\":\"V0.2.2\"}";
        assertEquals("0.2.2", UpdateCheck.latestVersion(json));
    }

    @Test
    void latestVersionKeepsAnUnprefixedTag() {
        assertEquals("1.4", UpdateCheck.latestVersion("{\"tag_name\": \"1.4\"}"));
    }

    @Test
    void latestVersionIsNullWithoutATag() {
        assertNull(UpdateCheck.latestVersion("{\"message\":\"Not Found\"}"));
        assertNull(UpdateCheck.latestVersion("{\"tag_name\":\"v\"}"));
        assertNull(UpdateCheck.latestVersion(null));
    }

    @ParameterizedTest
    @CsvSource({
            "0.2.2, 0.2.1, true",
            "0.3, 0.2.9, true",
            "1.0.0, 0.9.9, true",
            "0.2.1.1, 0.2.1, true",
            "0.2.1, 0.2.1, false",
            "1.0, 1, false",
            "0.2.0, 0.2.1, false",
            "0.2.1, 0.3.0, false",
    })
    void isNewerComparesNumericComponents(String remote, String local, boolean newer) {
        assertEquals(newer, UpdateCheck.isNewer(remote, local));
    }

    // A suffixed component parses as zero, so a pre-release tag compares
    // conservatively (never announced over a clean local build of the same
    // line) instead of throwing.
    @Test
    void isNewerCountsNonNumericComponentsAsZero() {
        assertFalse(UpdateCheck.isNewer("0.2.1-beta", "0.2.1"));
        assertFalse(UpdateCheck.isNewer("0.2.2-beta", "0.2.1"));
        assertTrue(UpdateCheck.isNewer("0.3.0-beta", "0.2.1"));
    }
}
