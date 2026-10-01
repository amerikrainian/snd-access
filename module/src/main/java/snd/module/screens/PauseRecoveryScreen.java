package snd.module.screens;

import java.util.Arrays;
import java.util.function.Supplier;

import com.tann.dice.Main;
import com.tann.dice.screens.pauseScreen.PauseScreen;
import com.tann.dice.screens.titleScreen.TitleScreen;

import snd.contracts.SndLog;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.core.nav.AccessScreen;

/**
 * The game's stuck-pause screen — a black screen whose only affordances are
 * an invisible-until-a-minute "tap a few times to escape" text and the
 * window-refocus resume callback. Sighted players click; here Resume runs the
 * game's own resume path (re-init the 3D dice system, reload the pause save)
 * and Title runs the tap-escape.
 */
public class PauseRecoveryScreen extends AccessScreen {
    @Override
    public String key() {
        return "pause";
    }

    @Override
    public boolean isActive() {
        return Main.getCurrentScreen() instanceof PauseScreen;
    }

    @Override
    public String screenName() {
        return Loc.get("ui", "pause.title");
    }

    @Override
    public void build(GraphBuilder b) {
        b.addLabel(ControlId.structural(CompositeKey.of("pause", "text")),
                new Supplier<String>() {
                    @Override
                    public String get() {
                        return Loc.get("ui", "pause.text");
                    }
                });

        NodeVtable resume = new NodeVtable();
        resume.controlType = ControlTypes.BUTTON;
        resume.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", "pause.resume");
            }
        }, AnnouncementKinds.LABEL));
        resume.onActivate = new Runnable() {
            @Override
            public void run() {
                try {
                    Main.self().resume(); // reloads the pause save when one is pending
                } catch (Throwable t) {
                    SndLog.error("manual resume failed", t);
                }
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("pause", "resume")), resume);

        NodeVtable title = new NodeVtable();
        title.controlType = ControlTypes.BUTTON;
        title.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", "pause.title_button");
            }
        }, AnnouncementKinds.LABEL));
        title.onActivate = new Runnable() {
            @Override
            public void run() {
                // The tap-a-few-times escape hatch.
                Main.self().setScreen(new TitleScreen());
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("pause", "title")), title);
    }
}
