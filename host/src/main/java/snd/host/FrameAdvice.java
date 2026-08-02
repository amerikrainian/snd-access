package snd.host;

import net.bytebuddy.asm.Advice;
import snd.core.Dispatcher;

/**
 * Inlined into {@code com.tann.dice.Main.render()}. The copied bytecode
 * references only {@link Dispatcher} (app classloader), never module types —
 * that is the no-classloader-leak invariant.
 */
public final class FrameAdvice {
    private FrameAdvice() {
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit() {
        Dispatcher.frame();
    }
}
