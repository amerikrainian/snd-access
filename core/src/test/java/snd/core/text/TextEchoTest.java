package snd.core.text;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import snd.core.loc.Loc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// A text field's edits and caret moves, as heard.
class TextEchoTest {
    @BeforeEach
    void installWording() {
        Map<String, String> ui = new HashMap<String, String>();
        ui.put("text.blank", "blank");
        ui.put("text.space", "space");
        ui.put("text.capital", "cap {letter}");
        ui.put("text.selected", "{text} selected");
        ui.put("text.unselected", "{text} unselected");
        Map<String, Map<String, String>> tables = new HashMap<String, Map<String, String>>();
        tables.put("ui", ui);
        Loc.installFallback(tables);
        Loc.install(Loc.FALLBACK_LANGUAGE, Collections.<String, Map<String, String>>emptyMap());
    }

    private static TextEcho seated(String text, int caret) {
        TextEcho echo = new TextEcho();
        assertNull(echo.observe(text, caret, -1, CaretMove.NONE));
        return echo;
    }

    @Test
    void theFirstLookOnlyTakesTheFieldIn() {
        TextEcho echo = new TextEcho();
        assertNull(echo.observe("Bob", 3, -1, CaretMove.NONE));
        assertNull(echo.observe("Bob", 3, -1, CaretMove.NONE));
    }

    @Test
    void typedAndDeletedCharactersEchoBare() {
        TextEcho echo = seated("", 0);
        assertEquals("b", echo.observe("b", 1, -1, CaretMove.NONE));
        assertEquals("o", echo.observe("bo", 2, -1, CaretMove.NONE));
        assertEquals("o", echo.observe("b", 1, -1, CaretMove.NONE)); // Backspace
        assertEquals("space", echo.observe("b ", 2, -1, CaretMove.NONE));
    }

    @Test
    void aLoneCapitalSaysSo() {
        TextEcho echo = seated("", 0);
        assertEquals("cap B", echo.observe("B", 1, -1, CaretMove.NONE));
        assertEquals("ob", echo.observe("Bob", 3, -1, CaretMove.NONE)); // a paste reads as it is
    }

    @Test
    void midStringEditsEchoJustTheChangedCharacters() {
        TextEcho echo = seated("aaa", 1);
        assertEquals("b", echo.observe("abaa", 2, -1, CaretMove.NONE));
        assertEquals("a", echo.observe("aba", 2, -1, CaretMove.NONE)); // Delete after the caret
    }

    @Test
    void typingOverASelectionReadsWhatWasTyped() {
        TextEcho echo = seated("hello", 5);
        assertEquals("j", echo.observe("hej", 3, -1, CaretMove.NONE));
    }

    @Test
    void caretStepsReadTheCharacterLandedOn() {
        TextEcho echo = seated("Bo b", 4);
        assertEquals("blank", echo.observe("Bo b", 4, -1, CaretMove.CHAR)); // Right at the end: no move
        assertEquals("b", echo.observe("Bo b", 3, -1, CaretMove.CHAR));
        assertEquals("space", echo.observe("Bo b", 2, -1, CaretMove.CHAR));
        assertEquals("cap B", echo.observe("Bo b", 0, -1, CaretMove.CHAR)); // Home
        assertEquals("cap B", echo.observe("Bo b", 0, -1, CaretMove.CHAR)); // Left at the start re-reads
        assertEquals("blank", echo.observe("Bo b", 4, -1, CaretMove.CHAR)); // End
    }

    @Test
    void wordJumpsReadTheWordLandedOn() {
        TextEcho echo = seated("big hammer", 0);
        assertEquals("space", echo.observe("big hammer", 3, -1, CaretMove.WORD));
        assertEquals("hammer", echo.observe("big hammer", 4, -1, CaretMove.WORD));
        assertEquals("big", echo.observe("big hammer", 0, -1, CaretMove.WORD));
    }

    @Test
    void aSelectionReadsTheSpanThatChanged() {
        TextEcho echo = seated("hello", 5);
        assertEquals("o selected", echo.observe("hello", 4, 5, CaretMove.CHAR));
        assertEquals("l selected", echo.observe("hello", 3, 5, CaretMove.CHAR));
        assertEquals("l unselected", echo.observe("hello", 4, 5, CaretMove.CHAR));
        assertEquals("hell selected", echo.observe("hello", 0, 5, CaretMove.CHAR)); // Shift+Home
    }

    @Test
    void jumpingOverTheAnchorReadsTheNewSelection() {
        TextEcho echo = seated("hello", 2);
        assertEquals("l selected", echo.observe("hello", 3, 2, CaretMove.CHAR));
        assertEquals("he selected", echo.observe("hello", 0, 2, CaretMove.CHAR));
    }

    @Test
    void aMoveWithoutShiftDropsTheSelectionAndReadsTheCharacter() {
        TextEcho echo = seated("hello", 5);
        echo.observe("hello", 4, 5, CaretMove.CHAR);
        assertEquals("l", echo.observe("hello", 3, -1, CaretMove.CHAR));
    }

    @Test
    void aClickSeatsTheAnchorWhereItLandsAndReadsTheCharacter() {
        TextEcho echo = seated("hello", 5);
        assertEquals("e", echo.observe("hello", 1, 1, CaretMove.NONE));
    }

    @Test
    void nothingChangedAndNoCaretKeySaysNothing() {
        TextEcho echo = seated("hello", 2);
        assertNull(echo.observe("hello", 2, -1, CaretMove.NONE));
    }

    @Test
    void resetTakesTheNextLookInSilently() {
        TextEcho echo = seated("a", 1);
        echo.reset();
        assertNull(echo.observe("something else", 0, -1, CaretMove.NONE));
    }

    @Test
    void aLineKeyReadsTheWholeText() {
        TextEcho echo = seated("", 0);
        assertEquals("blank", echo.observe("", 0, -1, CaretMove.LINE));
        echo.observe("Big Bob", 7, -1, CaretMove.NONE);
        assertEquals("Big Bob", echo.observe("Big Bob", 7, -1, CaretMove.LINE));
    }
}
