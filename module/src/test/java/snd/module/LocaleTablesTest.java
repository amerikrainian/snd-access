package snd.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import snd.core.loc.FlatJson;

/**
 * Every translated table carries exactly English's keys, and each string the
 * same {slots}: a missing key falls back to English mid-sentence, and a
 * missing or misspelt slot speaks its braces.
 */
class LocaleTablesTest {

    private static final File LOCALE = new File("src/main/resources/locale");
    private static final Pattern SLOT = Pattern.compile("\\{(\\w+)\\}");

    @Test
    void everyLanguageMatchesEnglish() throws IOException {
        File[] english = new File(LOCALE, "en").listFiles();
        assertTrue(english != null && english.length > 0, "no English tables under " + LOCALE);
        File[] languages = LOCALE.listFiles(File::isDirectory);
        assertTrue(languages.length > 1, "no translations under " + LOCALE);

        List<String> problems = new ArrayList<String>();
        for (File lang : languages) {
            if (lang.getName().equals("en")) {
                continue;
            }
            for (File enTable : english) {
                File table = new File(lang, enTable.getName());
                if (!table.isFile()) {
                    problems.add(table + ": missing");
                    continue;
                }
                Map<String, String> en = read(enTable);
                Map<String, String> tr = read(table);
                for (String key : en.keySet()) {
                    if (!tr.containsKey(key)) {
                        problems.add(table + ": missing key " + key);
                    } else if (!slots(en.get(key)).equals(slots(tr.get(key)))) {
                        problems.add(table + ": " + key + " has slots " + slots(tr.get(key))
                                + ", English has " + slots(en.get(key)));
                    }
                }
                for (String key : tr.keySet()) {
                    if (!en.containsKey(key)) {
                        problems.add(table + ": key not in English " + key);
                    }
                }
            }
        }
        assertEquals(new ArrayList<String>(), problems);
    }

    private static Map<String, String> read(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        // Locales decodes plain UTF-8; a BOM would become part of the first key.
        assertFalse(bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF, file + " starts with a BOM");
        return FlatJson.parse(new String(bytes, StandardCharsets.UTF_8));
    }

    private static TreeSet<String> slots(String template) {
        TreeSet<String> names = new TreeSet<String>();
        Matcher m = SLOT.matcher(template);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }
}
