package snd.core.nav;

import java.util.ArrayList;
import java.util.List;

import snd.core.SndLog;
import snd.core.input.InputAction;
import snd.core.input.InputRegistry;
import snd.core.loc.Loc;

/**
 * The keys that would do something right now, for the attached screen and the
 * focused control: the context-sensitive key help. Nothing is declared for it.
 * A key is listed when the screen offers it ({@link AccessScreen#keys()},
 * which screens answer from live game state: roll while rolling, undo while
 * targeting), when it is a navigator key and the navigator, asked without
 * acting ({@link GraphNavigator#wouldHandle}), would handle it (an arrow with
 * a way that way, Enter on a control that activates), or when it has a
 * handler that says it applies ({@link InputAction#isAvailable}: a glance is
 * not listed where its fact is not on screen).
 *
 * <p>In order, the most particular first: the screen's own keys; the handler
 * keys that say when they apply (the glances); the navigator's keys; the
 * handler keys that always do. Within a group, the registry's order.
 * Collected the moment the help opens, while the game's screen still has the
 * navigator: once the help is up, the focused control is the help's own.
 */
public final class KeyHelp {
    private KeyHelp() {
    }

    /** A key the help lists: what it does, the keys bound to it, how to run it. */
    public static final class Entry {
        public final String id;
        public final String label;
        public final String keys;
        /** Null = listed only (a key range). */
        public final Runnable run;

        Entry(String id, String label, String keys, Runnable run) {
            this.id = id;
            this.label = label;
            this.keys = keys;
            this.run = run;
        }
    }

    public static List<Entry> collect(final GraphNavigator nav, InputRegistry registry) {
        List<Entry> screenKeys = new ArrayList<Entry>();
        List<Entry> contextKeys = new ArrayList<Entry>();
        List<Entry> navigationKeys = new ArrayList<Entry>();
        List<Entry> generalKeys = new ArrayList<Entry>();

        AccessScreen screen = nav.screen();
        if (screen != null) {
            try {
                for (KeyOffer offer : screen.keys()) {
                    screenKeys.add(new Entry("key:" + offer.id, offer.label, offer.keys, offer.run));
                }
            } catch (Throwable t) {
                SndLog.error("key help: keys() threw for screen " + screen.key(), t);
            }
        }
        for (final InputAction action : registry.actions()) {
            if (action.isUnlisted()) {
                continue;
            }
            if (action.nav != null) {
                if (nav.wouldHandle(action.nav)) {
                    navigationKeys.add(entry(action, new Runnable() {
                        @Override
                        public void run() {
                            nav.onAction(action.nav);
                        }
                    }));
                }
            } else if (action.isAvailable()) {
                Runnable run = action.isDigitFamily() ? null : new Runnable() {
                    @Override
                    public void run() {
                        action.perform(-1);
                    }
                };
                (action.hasCondition() ? contextKeys : generalKeys).add(entry(action, run));
            }
        }

        List<Entry> all = new ArrayList<Entry>(screenKeys);
        all.addAll(contextKeys);
        all.addAll(navigationKeys);
        all.addAll(generalKeys);
        return all;
    }

    private static Entry entry(InputAction action, Runnable run) {
        return new Entry(action.id, Loc.get("ui", action.labelKey), action.keysDisplay(), run);
    }
}
