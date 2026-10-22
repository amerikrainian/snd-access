package snd.module;

import snd.module.screens.EventParts;
import snd.contracts.Dispatcher;
import snd.contracts.SndLog;
import snd.contracts.TransientText;
import snd.core.buffers.EventLog;
import snd.core.graph.AnnouncementKinds;

/**
 * Speaks the game's transient text the host's hooks queue on
 * {@link Dispatcher}, each kind its own event by the game method that showed
 * it: the notice banner (showInfo), the targeting refusal (showError), the
 * ability bar's mana gains and discards (addWisp), the words floating over a
 * unit (addMessage) and its chatter (addSpeechBubble). Many are direct
 * responses to a player action (a vetoed lock, a blocked end turn, an
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
        TransientText t;
        while ((t = Dispatcher.pollTransientText()) != null) {
            String key = t.speaker + ": " + t.text;
            long now = System.currentTimeMillis();
            if (key.equals(lastText) && now - lastMs < 1500) {
                lastMs = now;
                continue;
            }
            lastText = key;
            lastMs = now;
            String event = eventOf(t.method);
            if (t.speaker == null) {
                events.say(event, GameText.t(t.text), true);
                continue;
            }
            java.util.Map<String, String> parts = new java.util.HashMap<String, String>();
            parts.put(AnnouncementKinds.LABEL, GameText.t(t.speaker));
            parts.put(EventParts.MESSAGE, GameText.t(t.text));
            events.say(event, EventParts.line(event, parts), true);
        }
    }

    private static String eventOf(String method) {
        if ("showError".equals(method)) {
            return EventParts.REFUSAL;
        }
        if ("addWisp".equals(method)) {
            return EventParts.WISP;
        }
        if ("addMessage".equals(method)) {
            return EventParts.FLOATER;
        }
        if ("addSpeechBubble".equals(method)) {
            return EventParts.CHATTER;
        }
        if (!"showInfo".equals(method)) {
            SndLog.error("transient text from an unknown hook " + method + "; spoken as a banner", null);
        }
        return EventParts.BANNER;
    }
}
