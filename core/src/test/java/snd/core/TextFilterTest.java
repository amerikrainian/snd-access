package snd.core;

import org.junit.jupiter.api.Test;
import snd.core.speech.TextFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextFilterTest {
    @Test
    void stripsColourTags() {
        assertEquals("Warning, this will overwrite your in-progress game.",
                TextFilter.clean("[red]Warning[cu], this will overwrite your in-progress game."));
    }

    @Test
    void newlineTagBecomesLineBreak() {
        assertEquals("Flee?\n(counts as a loss)", TextFilter.clean("Flee?[n][red](counts as a loss)"));
    }

    @Test
    void dropsImageAndStyleTags() {
        assertEquals("size", TextFilter.clean("[tinyDice] size"));
        assertEquals("bypassed", TextFilter.clean("[b][purple]bypassed[b]"));
    }

    @Test
    void keepsPlainText() {
        assertEquals("full 20-fight dungeon", TextFilter.clean("full 20-fight dungeon"));
    }

    @Test
    void collapsesWhitespaceLeftByTags() {
        assertEquals("Bl ursed", TextFilter.clean("[green]Bl [purple]ursed"));
        assertEquals("Start", TextFilter.clean("  [yellow] Start  "));
    }

    @Test
    void dropsParensEmptiedByImageTags() {
        // A status magnitude drawn as pip icons: "Poisoned 2 (♥♥)".
        assertEquals("Poisoned 2", TextFilter.clean("[notranslate][green]Poisoned[cu] 2 ([green][hp][p][hp][p][cu])"));
        assertEquals("keep (this)", TextFilter.clean("keep (this)"));
    }

    @Test
    void nullAndEmptySafe() {
        assertEquals("", TextFilter.clean(null));
        assertEquals("", TextFilter.clean("[green][cu]"));
    }

    @Test
    void unclosedBracketSurvives() {
        assertEquals("a [ b", TextFilter.clean("a [ b"));
    }
}
