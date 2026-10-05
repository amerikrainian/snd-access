package snd.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class MarkedSyntaxTest {
    private static final Set<String> COLOURS = new HashSet<String>(Arrays.asList(
            "light", "grey", "red", "green", "yellow", "orange", "purple", "blue", "text"));
    private static final Predicate<String> IS_COLOUR = new Predicate<String>() {
        @Override
        public boolean test(String s) {
            return COLOURS.contains(s);
        }
    };
    private static final Predicate<String> IS_LIGHT = new Predicate<String>() {
        @Override
        public boolean test(String s) {
            return s.equals("light");
        }
    };

    private static String spoken(String doc) {
        return MarkedSyntax.spoken(doc, IS_COLOUR, IS_LIGHT);
    }

    @Test
    void literalPartsStayAndTokensAreBracketed() {
        // PipeRegexNamed.document(): each part in its colour, spacing tags between.
        assertEquals("hero.<keyword>", spoken("[grey][grey][light]hero.[p][p][q][cu][cu][red]keyword[cu]"));
        assertEquals("<modifier>.modtier.<#>",
                spoken("[grey][grey][purple]modifier[cu][cu][light].[p][p][q]modtier.[p][p][q][cu][cu][green]#[cu]"));
        assertEquals("<item>#<item>", spoken("[grey][grey][grey]item[cu][cu][light]#[cu][cu][grey]item[cu]"));
    }

    @Test
    void literalTextInsideAColouredRunIsStillLiteral() {
        // [cu] steps back through the colours remembered, so "add" stays light.
        assertEquals("add.<monster>",
                spoken("[grey][grey][green][light]add[cu][cu][cu][light].[p][p][q][cu][cu][orange]monster[cu]"));
    }

    @Test
    void adjacentTokensShareOneBracket() {
        assertEquals("party.<hero>+<hero...>",
                spoken("[grey][grey][light]party.[p][p][q][cu][cu][yellow]hero[light]+[yellow]hero[grey].[p][p][q]"
                        + ".[p][p][q].[p][p][q][cu][cu]"));
        assertEquals("<any>.hue.<+->",
                spoken("[grey][grey][yellow][yellow]a[cu][orange]n[cu][grey]y[cu][cu][cu][light].[p][p][q]hue."
                        + "[p][p][q][cu][cu][green]+-[cu]"));
    }

    @Test
    void textBeforeAnyColourAndUnknownTagsAreHandled() {
        assertEquals("<x>", spoken("[notranslate]x"));
        assertEquals("", spoken(""));
    }
}
