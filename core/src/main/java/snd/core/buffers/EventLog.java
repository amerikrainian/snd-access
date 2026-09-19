package snd.core.buffers;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import snd.contracts.speech.SpeechPipeline;
import snd.contracts.speech.TextFilter;

/**
 * What happened, as it was spoken: the lines the game's own events produce
 * (a banner, a roll's results, a die's outcome, a phase turning over, a
 * notification) are heard once and gone, so whoever speaks one says it
 * through here and it is kept for the log buffer to review. Echoes of the
 * player's own navigation (a focus readout, "selected", a typed character)
 * are not events and go straight to the pipeline. Bounded: the oldest lines
 * fall off. A line repeated back to back (the same refusal banner on every
 * retry) is kept once.
 */
public final class EventLog {
    private static final int CAPACITY = 300;

    private final SpeechPipeline speech;
    private final LinkedList<String> lines = new LinkedList<String>();

    public EventLog(SpeechPipeline speech) {
        this.speech = speech;
    }

    /** Speak the line as {@link SpeechPipeline#speak} would, and keep it. */
    public void say(String text, boolean interrupt) {
        speech.speak(text, interrupt);
        if (TextFilter.clean(text).isEmpty() || (!lines.isEmpty() && lines.getLast().equals(text))) {
            return;
        }
        lines.add(text);
        if (lines.size() > CAPACITY) {
            lines.removeFirst();
        }
    }

    /** Oldest first; the log buffer follows the latest. */
    public List<String> lines() {
        return new ArrayList<String>(lines);
    }

    public void clear() {
        lines.clear();
    }
}
