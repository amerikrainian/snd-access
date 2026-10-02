package snd.module.screens;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import snd.contracts.HostServices;
import snd.contracts.SndLog;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.core.nav.AccessScreen;
import snd.core.nav.GraphNavigator;

/**
 * A dropdown's open list: Enter on a dropdown control opens it over the
 * screen it sits on, landing on the value it holds. Enter on a value chooses
 * it and closes the list (the value held is left alone); Escape closes it
 * unchanged. Either way focus is
 * back on the dropdown, its landing taken quiet: after a choice the new value
 * is said, after Escape the dropdown is read again.
 */
public final class DropdownScreen extends AccessScreen {
    // Frames from closing to speaking: the list leaves the active set and the
    // covered screen re-attaches on the next tick, one more for good measure.
    private static final int SPEAK_AFTER = 2;

    private static DropdownScreen instance;

    private final HostServices host;
    private final GraphNavigator nav;

    private boolean open;
    private String label;
    private List<String> values = Collections.emptyList();
    private int chosen;
    private IntConsumer choose;
    private Supplier<String> value;

    private int speakIn;
    private boolean speakValue;
    private AccessScreen opener;

    public DropdownScreen(HostServices host, GraphNavigator nav) {
        this.host = host;
        this.nav = nav;
        instance = this;
    }

    /**
     * Open the list. values are the spoken choices in order, chosen the one
     * the control holds; choose applies an index; value reads the control's
     * value back once the choice has been applied.
     */
    public static void open(String label, List<String> values, int chosen, IntConsumer choose,
            Supplier<String> value) {
        DropdownScreen d = instance;
        d.label = label;
        d.values = values;
        d.chosen = chosen;
        d.choose = choose;
        d.value = value;
        d.opener = d.nav.screen();
        d.speakIn = 0;
        d.open = true;
    }

    @Override
    public String key() {
        return "dropdown";
    }

    @Override
    public boolean isActive() {
        return open;
    }

    @Override
    public int layer() {
        return 55; // over every game-backed screen, under the key help
    }

    @Override
    public String screenName() {
        return label;
    }

    @Override
    public boolean exclusive() {
        return true;
    }

    @Override
    public boolean onCancel() {
        close(false);
        return true;
    }

    @Override
    public void build(GraphBuilder b) {
        for (int i = 0; i < values.size(); i++) {
            final int index = i;
            final String text = values.get(i);
            NodeVtable vt = new NodeVtable();
            vt.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return text;
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return index == chosen ? Loc.get("ui", "state.selected") : null;
                        }
                    }, AnnouncementKinds.SELECTED));
            vt.onActivate = new Runnable() {
                @Override
                public void run() {
                    IntConsumer apply = choose;
                    close(true);
                    if (index == chosen) {
                        return; // the value held: re-applying it would rerun the change's side effects
                    }
                    try {
                        apply.accept(index);
                    } catch (Throwable t) {
                        SndLog.error("dropdown " + label + ": choosing " + text + " failed", t);
                    }
                }
            };
            // The SELECTED part is also where the list opens.
            b.addItem(ControlId.structural(CompositeKey.of("dropdown", i)), vt);
        }
    }

    private void close(boolean chose) {
        open = false;
        nav.quietNextLanding();
        speakValue = chose;
        speakIn = SPEAK_AFTER;
    }

    /** Per-frame, after the screen stack's tick. */
    public void tick() {
        if (speakIn <= 0 || --speakIn > 0) {
            return;
        }
        if (nav.screen() != opener) {
            return; // the choice brought up another screen (a warning): it announces itself
        }
        if (speakValue) {
            String now = value.get();
            if (now != null) {
                host.speech().speak(now, true);
            }
        } else {
            nav.readFocus();
        }
    }
}
