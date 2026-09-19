package snd.module;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import snd.contracts.SndLog;
import snd.core.loc.FlatJson;
import snd.core.loc.Loc;

/**
 * Loads the mod's locale tables from module-jar resources
 * (locale/&lt;lang&gt;/&lt;table&gt;.json) into core's {@link Loc}: English
 * always as the fallback, plus the game's current language when one exists.
 * The game's language is a live option, so {@link #tick()} polls it every
 * frame and reinstalls on change — a mid-session swap must apply immediately,
 * the same reason the game rebuilds its whole stage for one (and the polling
 * lesson wotr-access carries from SayTheSpire2). Language codes are the
 * game's own ("en", "de", ...), matching the game's lang/ file names.
 */
final class Locales {
    private Locales() {
    }

    private static final String[] TABLES = {"ui", "combat"};

    /** Called once from module load, before anything speaks. */
    static void load() {
        Map<String, Map<String, String>> en = read(Loc.FALLBACK_LANGUAGE);
        if (en.isEmpty()) {
            SndLog.error("no English locale tables in the module jar — mod strings will read as keys", null);
        }
        Loc.installFallback(en);
        install(gameLanguage());
    }

    /** Per-frame poll: a settings read and a string compare. */
    static void tick() {
        String lang = gameLanguage();
        if (!lang.equals(Loc.language())) {
            install(lang);
        }
    }

    private static void install(String lang) {
        if (Loc.FALLBACK_LANGUAGE.equals(lang)) {
            Loc.install(lang, Collections.<String, Map<String, String>>emptyMap());
        } else {
            Map<String, Map<String, String>> tables = read(lang);
            Loc.install(lang, tables);
            if (tables.isEmpty()) {
                SndLog.info("no mod locale tables for '" + lang + "'; mod strings fall back to English");
            }
        }
        SndLog.info("locale installed: " + lang);
    }

    // The translator's resolved code (it normalizes unknown languages to
    // "en"), or English while the game is still booting.
    private static String gameLanguage() {
        try {
            com.tann.dice.Main main = com.tann.dice.Main.self();
            if (main == null || main.translator == null) {
                return Loc.FALLBACK_LANGUAGE;
            }
            return main.translator.getLanguageCode();
        } catch (Throwable t) {
            return Loc.FALLBACK_LANGUAGE;
        }
    }

    private static Map<String, Map<String, String>> read(String lang) {
        Map<String, Map<String, String>> tables = new HashMap<String, Map<String, String>>();
        for (String table : TABLES) {
            String path = "locale/" + lang + "/" + table + ".json";
            InputStream in = Locales.class.getClassLoader().getResourceAsStream(path);
            if (in == null) {
                continue; // an untranslated language; load() flags a missing English set
            }
            try {
                tables.put(table, FlatJson.parse(readAll(in)));
            } catch (Exception e) {
                SndLog.error("failed to load locale table " + path, e);
            }
        }
        return tables;
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
