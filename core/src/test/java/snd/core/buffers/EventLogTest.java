package snd.core.buffers;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import snd.contracts.speech.SpeechPipeline;
import snd.core.settings.Announcements;
import snd.core.settings.BoolSetting;
import snd.core.settings.CategorySetting;
import snd.core.settings.Settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLogTest {
    private final SpeechPipeline pipeline = new SpeechPipeline();
    private final List<String> spoken = new ArrayList<String>();

    EventLogTest() {
        pipeline.setTap(new SpeechPipeline.Tap() {
            @Override
            public void spoken(String cleanText, boolean interrupt, String source) {
                spoken.add((interrupt ? "!" : "") + cleanText);
            }
        });
    }

    private Settings settings;

    private EventLog log() {
        CategorySetting root = new CategorySetting("root", CategorySetting.loc("x"));
        Announcements announcements = new Announcements(root);
        announcements.event("e", java.util.Collections.<String>emptyList());
        settings = new Settings(root);
        return new EventLog(pipeline, announcements);
    }

    @Test
    void anEventsSettingsSayWhetherItIsSpokenAndWhetherItIsKept() {
        EventLog log = log();
        ((BoolSetting) settings.find("events.e.announce")).set(false);
        log.say("e", "quiet", false);
        ((BoolSetting) settings.find("events.e.announce")).set(true);
        ((BoolSetting) settings.find("events.e.log")).set(false);
        log.say("e", "unkept", false);
        assertEquals(Arrays.asList("unkept"), spoken);
        assertEquals(Arrays.asList("quiet"), log.lines());
    }

    @Test
    void anEventIsSpokenAsGivenAndKept() {
        EventLog log = log();
        log.say("e", "Your roll", false);
        log.say("e", "[red]Not enough mana[cu]", true);
        log.say("e", "2 damage, on Bandit 1", false);
        assertEquals(Arrays.asList("Your roll", "!Not enough mana", "2 damage, on Bandit 1"), spoken);
        // Kept as given: the pipeline cleans markup at speak time, on review as on the first hearing.
        assertEquals(Arrays.asList("Your roll", "[red]Not enough mana[cu]", "2 damage, on Bandit 1"), log.lines());
    }

    @Test
    void aBackToBackRepeatIsSpokenEachTimeAndKeptOnce() {
        EventLog log = log();
        log.say("e", "Not enough mana", true);
        log.say("e", "Not enough mana", true);
        log.say("e", "Undo", true);
        log.say("e", "Not enough mana", true);
        assertEquals(4, spoken.size());
        assertEquals(Arrays.asList("Not enough mana", "Undo", "Not enough mana"), log.lines());
    }

    @Test
    void nothingBlankIsKeptAndTheOldestFallOff() {
        EventLog log = log();
        log.say("e", null, false);
        log.say("e", "[cu]", false);
        assertTrue(log.lines().isEmpty());
        for (int i = 0; i < 350; i++) {
            log.say("e", "event " + i, false);
        }
        List<String> lines = log.lines();
        assertEquals(300, lines.size());
        assertEquals("event 50", lines.get(0));
        assertEquals("event 349", lines.get(lines.size() - 1));
        log.clear();
        assertTrue(log.lines().isEmpty());
    }
}
