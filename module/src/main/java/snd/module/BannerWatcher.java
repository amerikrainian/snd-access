package snd.module;

import snd.core.Dispatcher;
import snd.core.speech.SpeechPipeline;

/**
 * Speaks the game's transient banners and error flashes — the hooked
 * showInfo/showError texts the host queues on {@link Dispatcher}. These are
 * direct responses to a player action (a vetoed lock, a blocked end turn, an
 * invalid target), so they interrupt. The game re-fires the same banner on
 * every repeated press; identical lines within a short window collapse.
 * Polled from the module tick.
 */
final class BannerWatcher {
    private final SpeechPipeline speech;
    private String lastText;
    private long lastMs;

    BannerWatcher(SpeechPipeline speech) {
        this.speech = speech;
    }

    void tick() {
        String text;
        while ((text = Dispatcher.pollTransientText()) != null) {
            long now = System.currentTimeMillis();
            if (text.equals(lastText) && now - lastMs < 1500) {
                lastMs = now;
                continue;
            }
            lastText = text;
            lastMs = now;
            speech.speak(GameText.t(text), true);
        }
    }
}
