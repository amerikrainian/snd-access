package snd.module;

import java.util.List;
import java.util.WeakHashMap;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;

import snd.core.speech.SpeechPipeline;

/**
 * Speaks the game's transient top-right popups (achievement toasts, stat
 * deltas) as they appear. The holder is the per-Screen PopupHolder, named
 * "alwaysontop" on the stage; popups slide in, sit ~5s, and vanish — visual
 * users can glance, so each new popup is spoken once, queued. Polled from the
 * module tick; seen-tracking is weak so removed popups don't accumulate.
 */
final class PopupWatcher {
    private final SpeechPipeline speech;
    private final WeakHashMap<Actor, Boolean> seen = new WeakHashMap<Actor, Boolean>();

    PopupWatcher(SpeechPipeline speech) {
        this.speech = speech;
    }

    void tick() {
        if (com.tann.dice.Main.stage == null) {
            return;
        }
        Actor holder = com.tann.dice.Main.stage.getRoot().findActor("alwaysontop");
        if (!(holder instanceof Group)) {
            return;
        }
        for (Actor popup : ((Group) holder).getChildren()) {
            if (seen.put(popup, Boolean.TRUE) != null) {
                continue;
            }
            List<String> texts = GameUi.textsUnder(popup);
            if (texts.isEmpty()) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < texts.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(texts.get(i));
            }
            speech.speak(snd.core.loc.Loc.get("ui", "notification", "text", sb), false);
        }
    }
}
