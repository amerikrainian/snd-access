package snd.core.loc;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import snd.contracts.SndLog;

/**
 * The mod's own strings — role words, glue text, structural phrases — loaded
 * from flat JSON tables and resolved as table.key → template with {var}
 * substitution (the wotr-access LocalizationManager model). English is always
 * installed as the fallback, so a missing or untranslated key reads in English
 * rather than as a raw key; the module follows the game's live language and
 * reinstalls on change.
 *
 * <p>Lives in core so permanent classes (ControlTypes, GraphDefaults) resolve
 * wording without touching the module loader: installed tables hold only plain
 * Strings, so a module reload can never pin its old classloader through here.
 *
 * <p>Game CONTENT (mode names, item and side text) is not this layer's job —
 * that is English source the game itself translates, spoken through the game's
 * own Translator (Main.t) at read time.</p>
 */
public final class Loc {
    private Loc() {
    }

    public static final String FALLBACK_LANGUAGE = "en";

    private static final Pattern VARIABLE = Pattern.compile("\\{(\\w+)\\}");

    private static volatile String language = FALLBACK_LANGUAGE;
    // table → (key → template). current = the game's language; fallback = English.
    private static volatile Map<String, Map<String, String>> current = Collections.emptyMap();
    private static volatile Map<String, Map<String, String>> fallback = Collections.emptyMap();
    // Missing keys are a dev error, logged once each — not once per frame.
    private static final Set<String> missingWarned =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /** The English tables, always resolvable. Installed once per module load. */
    public static void installFallback(Map<String, Map<String, String>> tables) {
        fallback = tables != null ? tables : Collections.<String, Map<String, String>>emptyMap();
        missingWarned.clear();
    }

    /**
     * The active language and its tables (empty for English — the fallback
     * already covers it). Reinstalled whenever the game's language changes.
     */
    public static void install(String lang, Map<String, Map<String, String>> tables) {
        language = lang != null ? lang : FALLBACK_LANGUAGE;
        current = tables != null ? tables : Collections.<String, Map<String, String>>emptyMap();
        missingWarned.clear();
    }

    public static String language() {
        return language;
    }

    /**
     * The resolved string: current language, else English, else the key itself
     * (logged once — visible, never silent). Varargs are name/value pairs
     * substituted into {name} slots.
     */
    public static String get(String table, String key, Object... vars) {
        String template = lookup(table, key);
        if (template == null) {
            String id = table + "." + key;
            if (missingWarned.add(id)) {
                SndLog.error("missing locale string: " + id, null);
            }
            template = key;
        }
        return substitute(template, vars);
    }

    /**
     * Like {@link #get} but quiet, resolving to the supplied fallback on a
     * miss — for callers that legitimately carry a default (derived wording
     * like camel-split screen names).
     */
    public static String getOrDefault(String table, String key, String def, Object... vars) {
        String template = lookup(table, key);
        return substitute(template != null ? template : def, vars);
    }

    private static String lookup(String table, String key) {
        if (!FALLBACK_LANGUAGE.equals(language)) {
            Map<String, String> t = current.get(table);
            if (t != null) {
                String v = t.get(key);
                if (v != null) {
                    return v;
                }
            }
        }
        Map<String, String> ft = fallback.get(table);
        return ft != null ? ft.get(key) : null;
    }

    private static String substitute(String template, Object[] vars) {
        if (vars == null || vars.length == 0 || template.indexOf('{') < 0) {
            return template;
        }
        if (vars.length % 2 != 0) {
            SndLog.error("locale vars must be name/value pairs (got " + vars.length
                    + " for \"" + template + "\")", null);
        }
        Matcher m = VARIABLE.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            String value = null;
            for (int i = 0; i + 1 < vars.length; i += 2) {
                if (name.equals(vars[i])) {
                    value = String.valueOf(vars[i + 1]);
                    break;
                }
            }
            // An unmatched {slot} stays visible — a wrong template names itself.
            m.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
