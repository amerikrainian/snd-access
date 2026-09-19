package snd.host;

import net.bytebuddy.asm.Advice;
import snd.contracts.Dispatcher;

/**
 * Inlined into {@code com.tann.dice.Main.render()}. The copied bytecode
 * references only {@link Dispatcher} (app classloader), never module types —
 * that is the no-classloader-leak invariant.
 */
public final class FrameAdvice {
    private FrameAdvice() {
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(@Advice.This Object self) {
        // The instance identifies the game's classloader (the shipped shim
        // loads the game outside the system loader).
        Dispatcher.frame(self);
    }
}
