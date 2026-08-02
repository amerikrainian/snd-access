package snd.core.loc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parser for the locale table format: one flat JSON object of string keys to
 * string values (the wotr-access/SayTheSpire2 layout). Deliberately minimal —
 * nested objects, arrays, numbers and booleans are format errors, reported
 * with their offset so a broken table names its own problem. Pure, so tables
 * parse identically in unit tests and in the game.
 */
public final class FlatJson {
    private FlatJson() {
    }

    public static Map<String, String> parse(String json) {
        Parser p = new Parser(json);
        Map<String, String> result = p.object();
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw p.fail("trailing content after the closing brace");
        }
        return result;
    }

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        Map<String, String> object() {
            Map<String, String> map = new LinkedHashMap<String, String>();
            skipWhitespace();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                String value = string();
                if (map.put(key, value) != null) {
                    throw fail("duplicate key \"" + key + "\"");
                }
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw fail("expected ',' or '}'");
                }
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > src.length()) {
                            throw fail("truncated \\u escape");
                        }
                        try {
                            sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException nfe) {
                            throw fail("bad \\u escape");
                        }
                        pos += 4;
                        break;
                    default:
                        throw fail("unknown escape '\\" + e + "'");
                }
            }
        }

        void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        boolean atEnd() {
            return pos >= src.length();
        }

        char peek() {
            if (atEnd()) {
                throw fail("unexpected end of input");
            }
            return src.charAt(pos);
        }

        char next() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char c) {
            char got = next();
            if (got != c) {
                throw fail("expected '" + c + "' but found '" + got + "'");
            }
        }

        IllegalArgumentException fail(String why) {
            return new IllegalArgumentException(why + " at offset " + pos);
        }
    }
}
