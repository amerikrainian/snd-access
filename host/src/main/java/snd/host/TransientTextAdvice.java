package snd.host;

import net.bytebuddy.asm.Advice;
import snd.contracts.Dispatcher;

/**
 * Inlined into the game's transient-text methods — the error/notice banner
 * ({@code AbilityHolder.showInfo}) and the targeting red-flash
 * ({@code TargetingManager.showError}), both ~half-second visuals with no
 * other trace. The first String argument is the display text. The copied
 * bytecode references only {@link Dispatcher} (app classloader), never module
 * types — the no-classloader-leak invariant.
 */
public final class TransientTextAdvice {
    private TransientTextAdvice() {
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(@Advice.Argument(0) String text) {
        Dispatcher.transientText(text);
    }
}
