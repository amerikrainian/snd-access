package snd.core.loc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class FlatJsonTest {

    @Test
    void parsesFlatObject() {
        Map<String, String> m = FlatJson.parse("{\"a\": \"one\", \"b\": \"two\"}");
        assertEquals(2, m.size());
        assertEquals("one", m.get("a"));
        assertEquals("two", m.get("b"));
    }

    @Test
    void parsesEmptyObjectAndWhitespace() {
        assertTrue(FlatJson.parse("  { }  ").isEmpty());
        Map<String, String> m = FlatJson.parse("\n{\n  \"k\" : \"v\"\n}\n");
        assertEquals("v", m.get("k"));
    }

    @Test
    void preservesDeclarationOrder() {
        Map<String, String> m = FlatJson.parse("{\"z\": \"1\", \"a\": \"2\", \"m\": \"3\"}");
        assertEquals("[z, a, m]", m.keySet().toString());
    }

    @Test
    void decodesEscapes() {
        Map<String, String> m = FlatJson.parse(
                "{\"k\": \"quote \\\" slash \\\\ solidus \\/ tab \\t newline \\n unicode \\u00e9\"}");
        assertEquals("quote \" slash \\ solidus / tab \t newline \n unicode \u00e9", m.get("k"));
    }

    @Test
    void allowsBracesAndVariablesInValues() {
        Map<String, String> m = FlatJson.parse("{\"position\": \"{index} of {count}\"}");
        assertEquals("{index} of {count}", m.get("position"));
    }

    @Test
    void rejectsNonStringValues() {
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                FlatJson.parse("{\"k\": 5}");
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                FlatJson.parse("{\"k\": {\"nested\": \"no\"}}");
            }
        });
    }

    @Test
    void rejectsDuplicateKeysTrailingContentAndTruncation() {
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                FlatJson.parse("{\"k\": \"a\", \"k\": \"b\"}");
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                FlatJson.parse("{} extra");
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                FlatJson.parse("{\"k\": \"unterminated");
            }
        });
    }

    @Test
    void reportsOffsetInErrors() {
        try {
            FlatJson.parse("{\"k\": 5}");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("offset"), e.getMessage());
        }
    }
}
