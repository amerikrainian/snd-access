package snd.host;

import net.bytebuddy.asm.Advice;
import snd.core.Dispatcher;

/**
 * Inlined into {@code EntPanelCombat.addMessage} (combat state text the game
 * writes over a panel — "dodged", "immune", "petrified") and
 * {@code addSpeechBubble} (hero/monster chatter) — both transient visuals
 * with no other trace. The owning panel's entity names the speaker. The
 * copied bytecode references only {@link Dispatcher} and game types (app
 * classloader), never module types — the no-classloader-leak invariant.
 */
public final class EntTextAdvice {
    private EntTextAdvice() {
    }

    // The signature must not name EntPanelCombat: resolving the advice's
    // parameter types would load the class WHILE it is being defined for
    // instrumentation (recursive definition → LinkageError). The cast lives
    // in the body, which is inlined into EntPanelCombat itself where
    // self-references are legal.
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(@Advice.This Object panel, @Advice.Argument(0) String text) {
        Dispatcher.transientText(
                ((com.tann.dice.screens.dungeon.panels.entPanel.EntPanelCombat) panel)
                        .ent.getName(true) + ": " + text);
    }
}
