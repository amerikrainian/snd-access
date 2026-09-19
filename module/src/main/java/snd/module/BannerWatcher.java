package snd.module;

import snd.contracts.Dispatcher;
import snd.core.buffers.EventLog;

/**
 * Speaks the game's transient banners and error flashes — the hooked
 * showInfo/showError texts the host queues on {@link Dispatcher}. These are
 * direct responses to a player action (a vetoed lock, a blocked end turn, an
 * invalid target), so they interrupt. The game re-fires the same banner on
 * every repeated press; identical lines within a short window collapse.
 * Polled from the module tick.
 */
final class BannerWatcher {
    private final EventLog events;
    private String lastText;
    private long lastMs;

    BannerWatcher(EventLog events) {
        this.events = events;
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
            events.say(GameText.t(text), true);
        }
    }
}
