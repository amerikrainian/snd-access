package snd.module;

import java.util.List;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.tann.dice.util.ui.TextInput;

import snd.core.loc.Loc;
import snd.core.speech.SpeechPipeline;

/**
 * Speaks the game's in-game text input (rename, scenario names): announces
 * the field when it takes keyboard focus (with the dialog's title and any
 * existing text) and echoes edits as they happen — typed characters, pastes,
 * deletions. The field owns the whole keyboard while focused (SndInput steps
 * aside), so this watcher is the only feedback channel.
 */
final class TextEntryWatcher {
    private final SpeechPipeline speech;
    private TextInput active;
    private String lastText = "";

    TextEntryWatcher(SpeechPipeline speech) {
        this.speech = speech;
    }

    void tick() {
        TextInput focus = focusedInput();
        if (focus == null) {
            active = null;
            return;
        }
        String text = focus.getText();
        if (focus != active) {
            active = focus;
            lastText = text != null ? text : "";
            StringBuilder sb = new StringBuilder(Loc.get("ui", "textentry.open", "title", dialogTitle()));
            sb.append(", ").append(lastText.isEmpty()
                    ? Loc.get("ui", "textentry.blank") : lastText);
            speech.speak(sb.toString(), true);
            return;
        }
        if (text == null || text.equals(lastText)) {
            return;
        }
        speakEdit(lastText, text);
        lastText = text;
    }

    private static TextInput focusedInput() {
        try {
            if (com.tann.dice.Main.stage == null) {
                return null;
            }
            Actor focus = com.tann.dice.Main.stage.getKeyboardFocus();
            return focus instanceof TextInput ? (TextInput) focus : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // The pushed text dialog's first TextWriter is its title ("rename",
    // "Store"); already-translated actor text.
    private static String dialogTitle() {
        Actor modal = GameUi.topModal();
        if (modal != null) {
            List<String> texts = GameUi.textsUnder(modal);
            if (!texts.isEmpty()) {
                return texts.get(0);
            }
        }
        return "";
    }

    // Echo the change: the inserted middle (a keystroke or a paste), or what
    // a deletion removed, via common prefix/suffix.
    private void speakEdit(String before, String after) {
        int prefix = 0;
        int max = Math.min(before.length(), after.length());
        while (prefix < max && before.charAt(prefix) == after.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < max - prefix
                && before.charAt(before.length() - 1 - suffix) == after.charAt(after.length() - 1 - suffix)) {
            suffix++;
        }
        String inserted = after.substring(prefix, after.length() - suffix);
        String removed = before.substring(prefix, before.length() - suffix);
        if (!inserted.isEmpty()) {
            speech.speak(inserted.trim().isEmpty()
                    ? Loc.get("ui", "textentry.space") : inserted, true);
        } else if (!removed.isEmpty()) {
            speech.speak(Loc.get("ui", "textentry.deleted",
                    "text", removed.trim().isEmpty() ? Loc.get("ui", "textentry.space") : removed), true);
        }
    }
}
