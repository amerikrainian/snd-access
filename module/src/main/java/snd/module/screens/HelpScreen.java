package snd.module.screens;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import snd.core.HostServices;
import snd.core.SndLog;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.input.InputRegistry;
import snd.core.loc.Loc;
import snd.core.nav.AccessScreen;
import snd.core.nav.GraphNavigator;
import snd.core.nav.KeyHelp;
import snd.core.speech.TextFilter;

/**
 * "Keys here" (F1 anywhere): the keys that would do something right now, for
 * the screen and the control the player stood on when they asked
 * ({@link KeyHelp}: roll while rolling, undo while targeting, Enter only on a
 * control that activates), each row what the key does and the key bound to
 * it. The list is taken the moment the help opens, while the game's screen
 * still has the navigator: once this overlay is up, the focused control is
 * its own.
 *
 * <p>Enter on a row runs the key as a press would have: the help closes,
 * focus is back where it was, the key is dispatched, and then the focus is
 * read again, since the action is likely to have changed what stands there.
 * What is heard, in order: whatever the action itself says, then the focused
 * control; never the key's name, which the player has just read off the row.
 * A speech hold keeps that order (its first line interrupts, the rest queue:
 * an action's feedback interrupts, some of it frames later); the landing the
 * closing overlay brings is taken quiet, the focus being read after the
 * action instead. The focus is not read again when the action moved it (the
 * move spoke it), when it opened another screen (which announces itself), or
 * when the action's own feedback already said it.
 */
public final class HelpScreen extends AccessScreen {
    // Frames from closing to dispatching: the overlay leaves the active set
    // and the covered screen re-attaches on the next tick, one more for good
    // measure. Then from dispatching to reading the focus: past the feedback
    // the game defers (banners, the roll settling into the FightLog).
    private static final int DISPATCH_AFTER = 2;
    private static final int FOCUS_AFTER = 16;

    private final HostServices host;
    private final GraphNavigator nav;
    private InputRegistry keys;

    private boolean open;
    private List<KeyHelp.Entry> entries = Collections.emptyList();

    private KeyHelp.Entry pending;
    private int dispatchIn;
    private int focusIn;
    private AccessScreen screenAtDispatch;
    private ControlId focusAtDispatch;

    public HelpScreen(HostServices host, GraphNavigator nav) {
        this.host = host;
        this.nav = nav;
    }

    /** The key table the help reads; it holds the help's own key, so it arrives after construction. */
    public void setKeys(InputRegistry keys) {
        this.keys = keys;
    }

    public void toggle() {
        if (open) {
            open = false;
            return;
        }
        // Collected now, against the screen and the control the player is on.
        entries = KeyHelp.collect(nav, keys);
        open = true;
    }

    /** A real key press ends a hold: the player has moved on. */
    public void keyPressed() {
        host.speech().endHold();
    }

    @Override
    public String key() {
        return "help";
    }

    @Override
    public boolean isActive() {
        return open;
    }

    @Override
    public int layer() {
        return 60; // over every game-backed screen
    }

    @Override
    public String screenName() {
        return Loc.get("ui", "help.title");
    }

    @Override
    public boolean exclusive() {
        return true;
    }

    @Override
    public boolean onCancel() {
        open = false;
        return true;
    }

    @Override
    public void build(GraphBuilder b) {
        if (entries.isEmpty()) {
            NodeVtable none = new NodeVtable();
            none.announcements = Arrays.asList(NodeAnnouncement.of(Loc.get("ui", "help.none")));
            b.addItem(ControlId.structural(CompositeKey.of("help", "none")), none);
            return;
        }
        for (final KeyHelp.Entry entry : entries) {
            NodeVtable vt = new NodeVtable();
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return Loc.get("ui", "help.row", "label", entry.label, "keys", entry.keys);
                }
            }, AnnouncementKinds.LABEL));
            vt.searchText = new Supplier<String>() {
                @Override
                public String get() {
                    return entry.label;
                }
            };
            if (entry.run != null) {
                vt.onActivate = new Runnable() {
                    @Override
                    public void run() {
                        perform(entry);
                    }
                };
            }
            b.addItem(ControlId.structural(CompositeKey.of("help", entry.id)), vt);
        }
    }

    // Nothing is said for the Enter itself: the player has just read the row.
    // The hold's first line (the action's own, else the focus) cuts the row's
    // readout off.
    private void perform(KeyHelp.Entry entry) {
        host.speech().beginHold();
        nav.quietNextLanding();
        open = false;
        pending = entry;
        dispatchIn = DISPATCH_AFTER;
    }

    /** Per-frame, after the screen stack's tick. */
    public void tick() {
        if (dispatchIn > 0 && --dispatchIn == 0) {
            dispatch();
        }
        if (focusIn > 0 && --focusIn == 0) {
            readFocus();
        }
    }

    private void dispatch() {
        KeyHelp.Entry entry = pending;
        pending = null;
        screenAtDispatch = nav.screen();
        focusAtDispatch = nav.focusedId();
        try {
            entry.run.run();
        } catch (Throwable t) {
            SndLog.error("key help: running " + entry.id + " failed", t);
            host.speech().endHold();
            return;
        }
        focusIn = FOCUS_AFTER;
    }

    private void readFocus() {
        try {
            if (!host.speech().holding()) {
                return; // a real key press since then: the player has moved on
            }
            if (nav.screen() != screenAtDispatch) {
                return; // another screen: it announces itself
            }
            ControlId now = nav.focusedId();
            if (now == null || !now.equals(focusAtDispatch)) {
                return; // the action moved focus and spoke it
            }
            if (alreadySaid(nav.focusedLabel())) {
                return;
            }
            nav.readFocus();
        } finally {
            host.speech().endHold();
            screenAtDispatch = null;
            focusAtDispatch = null;
        }
    }

    // Whether the action's own feedback read the focused control already.
    private boolean alreadySaid(String label) {
        String clean = TextFilter.clean(label);
        if (clean.isEmpty()) {
            return false;
        }
        String needle = clean.toLowerCase();
        for (String said : host.speech().held()) {
            if (said.toLowerCase().contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
