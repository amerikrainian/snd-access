package snd.module.screens;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.tann.dice.screens.dungeon.DungeonScreen;
import com.tann.dice.screens.dungeon.panels.tutorial.TutorialHolder;
import com.tann.dice.screens.dungeon.panels.tutorial.TutorialItem;
import com.tann.dice.screens.dungeon.panels.tutorial.TutorialQuest;

import snd.contracts.SndLog;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.module.GameUi;

/**
 * The tutorial box (tips + tracked quests) shown during the rolling,
 * targeting, and level-end phases — a stop appended to whichever access
 * screen owns the phase. Quests speak an explicit done/to-do state (the
 * visual checkbox is markup the speech filter strips); the box's
 * right-click-only close control becomes a button opening the game's own
 * Skip-all / Dismiss dialog.
 */
final class TutorialNodes {
    private TutorialNodes() {
    }

    static void build(GraphBuilder b, DungeonScreen ds) {
        com.tann.dice.screens.dungeon.panels.tutorial.TutorialManager manager = ds.getTutorialManager();
        TutorialHolder holder = manager != null ? manager.tutorialHolder : null;
        if (holder == null || holder.getStage() == null || !shown(manager, holder)) {
            return;
        }
        List<TutorialItem> items = items(holder);
        if (items.isEmpty()) {
            return;
        }

        b.beginStop("tutorial").pushContext(snd.module.GameText.t("Tutorial"), Loc.get("ui", "role.list"));
        for (int i = 0; i < items.size(); i++) {
            final TutorialItem item = items.get(i);
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.TEXT;
            vt.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            String text = item.getSortText();
                            // The one actor-only tip: the pip-colour legend
                            // (HpGrid.makeTutorial). Its sort text is the
                            // actor's internal name; speak the tip's purpose
                            // instead — the pips' meanings are already spoken
                            // in words on every character here.
                            if ("hp display show thing".equals(text)) {
                                return Loc.get("ui", "tutorial.hp_legend");
                            }
                            return text;
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            if (!(item instanceof TutorialQuest)) {
                                return Loc.get("ui", "tutorial.tip");
                            }
                            return Loc.get("ui", item.isComplete()
                                    ? "tutorial.done" : "tutorial.todo");
                        }
                    }, AnnouncementKinds.VALUE));
            b.addItem(ControlId.referenced(item, CompositeKey.of("tutorial", i)), vt);
        }

        final Actor close = closeButton(holder);
        if (close != null) {
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.BUTTON;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return Loc.get("ui", "tutorial.close");
                }
            }, AnnouncementKinds.LABEL));
            vt.onActivate = new Runnable() {
                @Override
                public void run() {
                    GameUi.info(close); // its listener is right-click-only
                }
            };
            b.addItem(ControlId.referenced(close, "tutorial-close"), vt);
        }
        b.popContext();
    }

    // The box is up while the tutorial is on and the phase has items for it
    // that are not all done (TutorialManager.showManagerForPhase, afterAction:
    // otherwise it slides the box away, keeping its last items).
    private static boolean shown(com.tann.dice.screens.dungeon.panels.tutorial.TutorialManager manager,
            TutorialHolder holder) {
        List<?> actives = (List<?>) snd.module.Captured.field(manager,
                com.tann.dice.screens.dungeon.panels.tutorial.TutorialManager.class, "actives");
        return manager.isEnabled() && actives != null && !actives.isEmpty() && !holder.allComplete();
    }

    // The box's close control: the game's close image (TutorialHolder).
    private static Actor closeButton(TutorialHolder holder) {
        for (Actor child : holder.getChildren()) {
            if (child instanceof com.tann.dice.util.ImageActor
                    && ((com.tann.dice.util.ImageActor) child).tr == com.tann.dice.statics.Images.tut_close) {
                return child;
            }
        }
        return null;
    }

    private static Field itemsField;

    @SuppressWarnings("unchecked")
    static List<TutorialItem> items(TutorialHolder holder) {
        try {
            if (itemsField == null) {
                itemsField = TutorialHolder.class.getDeclaredField("items");
                itemsField.setAccessible(true);
            }
            List<TutorialItem> items = (List<TutorialItem>) itemsField.get(holder);
            return items != null ? items : Collections.<TutorialItem>emptyList();
        } catch (Throwable t) {
            SndLog.error("failed to read TutorialHolder.items", t);
            return Collections.emptyList();
        }
    }
}
