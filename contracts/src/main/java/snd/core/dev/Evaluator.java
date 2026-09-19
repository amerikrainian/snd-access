package snd.core.dev;

/**
 * The /eval engine contract. Implemented by snd.devrepl.JShellEvaluator (JDK
 * 9+ only, dev classpath only); the host discovers it reflectively and reports
 * eval as unavailable when it is absent (the shipped Java 8 build).
 */
public interface Evaluator {
    /** Evaluates source, returning output/diagnostics/value as display text. */
    String eval(String source);

    /** Drops the session (used after a module reload so types re-resolve). */
    void reset();
}
