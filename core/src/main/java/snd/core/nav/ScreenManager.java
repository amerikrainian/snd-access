package snd.core.nav;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import snd.core.SndLog;
import snd.core.speech.SpeechPipeline;

/**
 * Poll-and-diff screen stack: every frame, resolve which registered screens
 * are active against live game state, attach the navigator to the top one
 * (highest layer; later registration breaks ties), and let it ensure focus. A
 * screen that leaves the active set entirely drops its cursor state; a
 * covered-but-active screen keeps it and restores in place.
 */
public final class ScreenManager {
    private final GraphNavigator nav;
    private final SpeechPipeline speech;
    private final List<AccessScreen> screens = new ArrayList<AccessScreen>();
    private final Set<AccessScreen> previouslyActive = new HashSet<AccessScreen>();
    private AccessScreen current;

    public ScreenManager(GraphNavigator nav, SpeechPipeline speech) {
        this.nav = nav;
        this.speech = speech;
    }

    public void register(AccessScreen screen) {
        screens.add(screen);
    }

    public AccessScreen current() {
        return current;
    }

    /** The navigator owns the keyboard while an access screen is attached. */
    public boolean ownsKeyboard() {
        return current != null;
    }

    public void tick() {
        AccessScreen top = null;
        Set<AccessScreen> active = new HashSet<AccessScreen>();
        for (AccessScreen s : screens) {
            if (safeIsActive(s)) {
                active.add(s);
                if (top == null || s.layer() >= top.layer()) {
                    top = s;
                }
            }
        }

        // Screens that left the active set drop their cursor state.
        for (AccessScreen s : previouslyActive) {
            if (!active.contains(s)) {
                nav.screenClosed(s);
            }
        }
        previouslyActive.clear();
        previouslyActive.addAll(active);

        if (top != current) {
            current = top;
            nav.attach(top);
            if (top != null && top.screenName() != null) {
                speech.speak(top.screenName(), true); // a new screen supersedes
            }
        }
        nav.ensureFocus();
    }

    private static boolean safeIsActive(AccessScreen s) {
        try {
            return s.isActive();
        } catch (Throwable t) {
            SndLog.error("isActive threw for screen " + s.key() + "; treating as inactive", t);
            return false;
        }
    }
}
