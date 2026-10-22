package snd.module.settings;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import snd.contracts.SndLog;
import snd.core.graph.GraphAnnouncer;
import snd.core.loc.FlatJson;
import snd.core.settings.Announcements;
import snd.core.settings.BoolSetting;
import snd.core.settings.CategorySetting;
import snd.core.settings.Setting;
import snd.core.settings.Settings;

/**
 * The mod's settings: the tree the menu shows, kept in
 * {@code <game>\mods\snd-access\settings.json} (the game's folder is the
 * working directory of a dev launch and a player's alike, so both read one
 * file). Built fresh by every module generation and re-read from the file, so
 * a reload keeps what the player set. Every change is written at once.
 */
public final class ModSettings {
    static final File FILE = new File("mods" + File.separator + "snd-access", "settings.json");

    private static ModSettings current;

    public final Settings settings;
    public final Announcements announcements;
    public final BoolSetting checkUpdates;

    private ModSettings() {
        CategorySetting root = new CategorySetting("settings", Setting.loc("menu.settings"));
        CategorySetting general = root.add(new CategorySetting("general", Setting.loc("settings.general")));
        general.sortPriority = -1;
        checkUpdates = general.add(new BoolSetting("check_updates", Setting.loc("settings.check_updates"), true));
        announcements = new Announcements(root);
        Readouts.declare(announcements);
        settings = new Settings(root);
    }

    public static ModSettings get() {
        return current;
    }

    /** Build this generation's settings, restore the saved values, and hand the navigator the part settings. */
    public static ModSettings load() {
        ModSettings s = new ModSettings();
        s.settings.load(read());
        s.settings.setWriter(new Consumer<Map<String, String>>() {
            @Override
            public void accept(Map<String, String> saved) {
                write(saved);
            }
        });
        current = s;
        GraphAnnouncer.partPolicy = s.announcements;
        return s;
    }

    private static Map<String, String> read() {
        if (!FILE.isFile()) {
            return new LinkedHashMap<String, String>();
        }
        String text;
        try {
            text = new String(Files.readAllBytes(FILE.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SndLog.error("settings: reading " + FILE.getAbsolutePath() + " failed; starting from defaults", e);
            return new LinkedHashMap<String, String>();
        }
        try {
            return FlatJson.parse(text);
        } catch (IllegalArgumentException e) {
            // Kept aside, not overwritten by the next change.
            File aside = new File(FILE.getParentFile(), "settings.broken.json");
            SndLog.error("settings: " + FILE.getAbsolutePath() + " is not valid (" + e.getMessage()
                    + "); moved to " + aside.getName() + ", starting from defaults", null);
            try {
                Files.move(FILE.toPath(), aside.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException io) {
                SndLog.error("settings: moving the broken file aside failed", io);
            }
            return new LinkedHashMap<String, String>();
        }
    }

    // Written whole to a temporary file, then moved over the old one, so a
    // crash mid-write never leaves half a file.
    private static void write(Map<String, String> saved) {
        File dir = FILE.getParentFile();
        File tmp = new File(dir, "settings.json.tmp");
        try {
            Files.createDirectories(dir.toPath());
            Files.write(tmp.toPath(), FlatJson.write(saved).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            SndLog.error("settings: writing " + FILE.getAbsolutePath() + " failed", e);
        }
    }
}
