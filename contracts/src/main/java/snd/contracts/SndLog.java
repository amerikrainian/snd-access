package snd.contracts;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * The mod's logger. Every line goes to stderr with a [snd-access] prefix and to
 * a host-installed sink (the dev server's /log ring buffer). No silent
 * failures: catch blocks log what failed and where.
 */
public final class SndLog {
    private SndLog() {
    }

    public interface Sink {
        void line(String text);
    }

    private static volatile Sink sink;

    public static void setSink(Sink s) {
        sink = s;
    }

    public static void info(String msg) {
        emit("[snd-access] " + msg);
    }

    public static void error(String msg, Throwable t) {
        String line = "[snd-access] ERROR: " + msg;
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            line += "\n" + sw;
        }
        emit(line);
    }

    private static void emit(String line) {
        System.err.println(line);
        Sink s = sink;
        if (s != null) {
            try {
                s.line(line);
            } catch (Throwable ignored) {
                // the sink is best-effort; stderr already has the line
            }
        }
    }
}
