package snd.core.speech;

/**
 * The single choke point for everything the mod speaks. Never call a backend
 * directly. The dev-server tap sits upstream of the backend, so spoken lines
 * are observable even when speech is muted (headless runs, SND_NO_SPEECH=1).
 */
public final class SpeechPipeline {

    /** A TTS/screen-reader sink. The host installs the Prism-backed one. */
    public interface Backend {
        boolean speak(String text, boolean interrupt);

        void stop();
    }

    /** Observer for the dev server's /speech log. */
    public interface Tap {
        void spoken(String cleanText, boolean interrupt, String source);
    }

    private volatile Backend backend;
    private volatile Tap tap;
    private volatile boolean muted;

    public void setBackend(Backend b) {
        backend = b;
    }

    public void setTap(Tap t) {
        tap = t;
    }

    public void setMuted(boolean m) {
        muted = m;
    }

    public boolean isMuted() {
        return muted;
    }

    public boolean hasBackend() {
        return backend != null;
    }

    public void speak(String text, boolean interrupt) {
        String clean = TextFilter.clean(text);
        if (clean.isEmpty()) {
            return;
        }
        Backend b = backend;
        if (!muted && b != null) {
            b.speak(clean, interrupt);
        }
        Tap t = tap;
        if (t != null) {
            t.spoken(clean, interrupt, callerName());
        }
    }

    public void stop() {
        Backend b = backend;
        if (b != null) {
            b.stop();
        }
    }

    /**
     * Attributes a spoken line to the class that requested it — first stack
     * frame outside this pipeline. Only paid while a tap is attached.
     */
    private static String callerName() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (StackTraceElement e : stack) {
            String cls = e.getClassName();
            if (!cls.startsWith("snd.core.speech.")) {
                int dot = cls.lastIndexOf('.');
                return dot >= 0 ? cls.substring(dot + 1) : cls;
            }
        }
        return "?";
    }
}
