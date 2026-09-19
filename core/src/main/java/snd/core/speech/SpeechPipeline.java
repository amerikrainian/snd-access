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
        synchronized (held) {
            if (holding()) {
                // The first line cuts off what was being said when the hold
                // opened; everything after it queues behind it, in order.
                interrupt = held.isEmpty();
                held.add(clean);
            }
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

    // ---- the hold: while open, the first line spoken interrupts and nothing
    // after it does — every later line queues, in the order it was said. The
    // key help opens one to run an action for the player: whatever the action
    // says (its feedback interrupts, as a key press wants, some of it frames
    // later), then the focus again, heard in that order instead of each
    // cutting the last off. Ended by endHold, by the next real key press, or
    // by itself after a few seconds (its closer may have died with a screen).

    /** Nanosecond clock; tests substitute their own. */
    public interface Clock {
        long nanos();
    }

    private static final long HOLD_LAPSE_NANOS = 3000000000L;
    private final java.util.List<String> held = new java.util.ArrayList<String>();
    private boolean holdOpen;
    private long holdUntil;
    private volatile Clock clock = new Clock() {
        @Override
        public long nanos() {
            return System.nanoTime();
        }
    };

    public void setClock(Clock c) {
        clock = c;
    }

    public void beginHold() {
        synchronized (held) {
            held.clear();
            holdOpen = true;
            holdUntil = clock.nanos() + HOLD_LAPSE_NANOS;
        }
    }

    public void endHold() {
        synchronized (held) {
            holdOpen = false;
        }
    }

    public boolean holding() {
        synchronized (held) {
            if (holdOpen && clock.nanos() - holdUntil > 0) {
                holdOpen = false;
            }
            return holdOpen;
        }
    }

    /** What was spoken since the hold opened, as cleaned lines. */
    public java.util.List<String> held() {
        synchronized (held) {
            return new java.util.ArrayList<String>(held);
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
