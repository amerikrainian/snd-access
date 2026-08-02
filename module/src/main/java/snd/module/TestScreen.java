package snd.module;

import java.util.Arrays;
import java.util.function.Supplier;

import com.tann.dice.gameplay.mode.Mode;
import com.tann.dice.screens.titleScreen.TitleScreen;

import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;

/**
 * The navigator's proof-of-life screen, active at the title screen: the
 * game's real mode list as one stop (identity = the live Mode objects), plus
 * an actions stop exercising activate/stateText and live parts. Replaced by
 * the real title-flow screens in the next phase.
 */
class TestScreen extends snd.core.nav.AccessScreen {
    private boolean toggleOn;

    @Override
    public String key() {
        return "test";
    }

    @Override
    public int layer() {
        return 0;
    }

    @Override
    public String screenName() {
        return "Access test panel";
    }

    @Override
    public boolean isActive() {
        return com.tann.dice.Main.getCurrentScreen() instanceof TitleScreen;
    }

    @Override
    public void build(GraphBuilder b) {
        b.beginStop("modes").pushContext("Modes", "list");
        for (final Mode m : Mode.getPlayableModes()) {
            if (m.skipFromMainList()) {
                continue;
            }
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.BUTTON;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return m.getName();
                }
            }, AnnouncementKinds.LABEL));
            vt.stateText = new Supplier<String>() {
                @Override
                public String get() {
                    return "selected " + m.getName();
                }
            };
            vt.onActivate = new Runnable() {
                @Override
                public void run() {
                    // Proof-of-life only: stateText speaks; no game state changes.
                }
            };
            b.addItem(ControlId.referenced(m, CompositeKey.of("mode", m.getName())), vt);
        }
        b.popContext();

        b.beginStop("actions").pushContext("Actions");
        NodeVtable toggle = new NodeVtable();
        toggle.controlType = ControlTypes.TOGGLE;
        toggle.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return "Test toggle";
                    }
                }, AnnouncementKinds.LABEL),
                new NodeAnnouncement(new Supplier<String>() {
                    @Override
                    public String get() {
                        return toggleOn ? "on" : "off";
                    }
                }, true, AnnouncementKinds.VALUE));
        toggle.stateText = new Supplier<String>() {
            @Override
            public String get() {
                return toggleOn ? "on" : "off";
            }
        };
        toggle.onActivate = new Runnable() {
            @Override
            public void run() {
                toggleOn = !toggleOn;
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("test", "toggle")), toggle);
        b.popContext();
    }
}
