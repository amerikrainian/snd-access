package snd.core.buffers;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import snd.core.speech.SpeechPipeline;

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

    @Test
    void anEventIsSpokenAsGivenAndKept() {
        EventLog log = new EventLog(pipeline);
        log.say("Your roll", false);
        log.say("[red]Not enough mana[cu]", true);
        log.say("2 damage, on Bandit 1", false);
        assertEquals(Arrays.asList("Your roll", "!Not enough mana", "2 damage, on Bandit 1"), spoken);
        // Kept as given: the pipeline cleans markup at speak time, on review as on the first hearing.
        assertEquals(Arrays.asList("Your roll", "[red]Not enough mana[cu]", "2 damage, on Bandit 1"), log.lines());
    }

    @Test
    void aBackToBackRepeatIsSpokenEachTimeAndKeptOnce() {
        EventLog log = new EventLog(pipeline);
        log.say("Not enough mana", true);
        log.say("Not enough mana", true);
        log.say("Undo", true);
        log.say("Not enough mana", true);
        assertEquals(4, spoken.size());
        assertEquals(Arrays.asList("Not enough mana", "Undo", "Not enough mana"), log.lines());
    }

    @Test
    void nothingBlankIsKeptAndTheOldestFallOff() {
        EventLog log = new EventLog(pipeline);
        log.say(null, false);
        log.say("[cu]", false);
        assertTrue(log.lines().isEmpty());
        for (int i = 0; i < 350; i++) {
            log.say("event " + i, false);
        }
        List<String> lines = log.lines();
        assertEquals(300, lines.size());
        assertEquals("event 50", lines.get(0));
        assertEquals("event 349", lines.get(lines.size() - 1));
        log.clear();
        assertTrue(log.lines().isEmpty());
    }
}
