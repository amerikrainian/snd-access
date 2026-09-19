package snd.contracts;

import org.junit.jupiter.api.Test;
import snd.contracts.speech.SpeechPipeline;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechPipelineTest {
    static class FakeBackend implements SpeechPipeline.Backend {
        final List<String> spoken = new ArrayList<>();

        @Override
        public boolean speak(String text, boolean interrupt) {
            spoken.add((interrupt ? "!" : "") + text);
            return true;
        }

        @Override
        public void stop() {
        }
    }

    @Test
    void cleansBeforeSpeaking() {
        SpeechPipeline p = new SpeechPipeline();
        FakeBackend b = new FakeBackend();
        p.setBackend(b);
        p.speak("[green]Classic[cu] mode", false);
        assertEquals("Classic mode", b.spoken.get(0));
    }

    @Test
    void emptyAfterCleaningIsDropped() {
        SpeechPipeline p = new SpeechPipeline();
        FakeBackend b = new FakeBackend();
        p.setBackend(b);
        p.speak("[green][cu]", false);
        p.speak(null, false);
        assertTrue(b.spoken.isEmpty());
    }

    @Test
    void tapFiresEvenWhenMuted() {
        SpeechPipeline p = new SpeechPipeline();
        FakeBackend b = new FakeBackend();
        final List<String> tapped = new ArrayList<>();
        p.setBackend(b);
        p.setMuted(true);
        p.setTap(new SpeechPipeline.Tap() {
            @Override
            public void spoken(String cleanText, boolean interrupt, String source) {
                tapped.add(cleanText + "/" + source);
            }
        });
        p.speak("hello", true);
        assertTrue(b.spoken.isEmpty());
        assertEquals(1, tapped.size());
        // attributed to this test class, not the pipeline
        assertTrue(tapped.get(0).endsWith("/SpeechPipelineTest"));
    }
}
