package snd.module.screens;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.tann.dice.Main;
import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.content.ent.Hero;
import com.tann.dice.gameplay.content.ent.die.Die;
import com.tann.dice.gameplay.content.ent.die.EntDie;
import com.tann.dice.gameplay.content.ent.die.side.EntSide;
import com.tann.dice.gameplay.content.item.Item;
import com.tann.dice.gameplay.fightLog.EntState;
import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.gameplay.trigger.personal.Personal;
import com.tann.dice.screens.Screen;
import com.tann.dice.screens.dungeon.DungeonScreen;
import com.tann.dice.screens.dungeon.panels.Explanel.EntPanelInventory;

import snd.core.HostServices;
import snd.core.SndLog;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.core.nav.AccessScreen;
import snd.module.GameText;
import snd.module.GameUi;

/**
 * The character sheet the game pushes on panel right-click — the
 * EntPanelInventory, a light push over a still-interactive dungeon screen.
 * Read as navigable nodes: identity and hp, the six die sides (the rolled one
 * marked), equipped items, statuses. CombatScreen's secondary opens it through
 * the game's own route ({@code targetingManager.clicked}), so a sighted
 * co-player sees the same panel the reader is on. Backspace on any node
 * toggles it closed, mirroring the right-click toggle; Escape falls through
 * to the game's own light-pop. On close the combat screen restores focus to
 * the owning panel's node.
 */
public class SheetScreen extends AccessScreen {
    private final HostServices host;

    public SheetScreen(HostServices host) {
        this.host = host;
    }

    @Override
    public String key() {
        return "sheet";
    }

    @Override
    public int layer() {
        return 18; // over combat and the dialog phases; party management and
                   // blocker-backed modals (layer 20) still win
    }

    @Override
    public boolean isActive() {
        return panel() != null;
    }

    /** The light-pushed sheet on top of the dungeon's modal stack, or null. */
    private static EntPanelInventory panel() {
        try {
            Screen screen = Main.getCurrentScreen();
            if (!(screen instanceof DungeonScreen)) {
                return null;
            }
            Actor top = screen.getTopPushedActor();
            if (!(top instanceof EntPanelInventory) || top.getStage() == null) {
                return null;
            }
            EntPanelInventory pan = (EntPanelInventory) top;
            return pan.ent == null ? null : pan;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String screenName() {
        EntPanelInventory pan = panel();
        return pan == null ? null : Loc.get("ui", "sheet.title", "name", GameUi.entName(pan.ent));
    }

    @Override
    public void build(GraphBuilder b) {
        EntPanelInventory pan = panel();
        final DungeonScreen ds = DungeonScreen.get();
        if (pan == null || ds == null) {
            return;
        }
        // No context label: screenName() already announces the title on
        // attach, and a same-text context would read it out twice.
        final Ent ent = pan.ent;
        b.beginStop("sheet");

        NodeVtable header = textVtable();
        header.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return headerText(ds, ent);
            }
        }, AnnouncementKinds.LABEL));
        b.addItem(ControlId.structural(CompositeKey.of("sheet", "header")), header);

        final EntSide[] sides = ent.getSides();
        for (int i = 0; i < sides.length; i++) {
            final int index = i;
            NodeVtable vt = textVtable();
            vt.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return (index + 1) + ": " + GameText.t(sides[index]
                                    .findState(FightLog.Temporality.Present, ent).describe());
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return isRolled(ent, index) ? Loc.get("ui", "sheet.rolled") : null;
                        }
                    }, AnnouncementKinds.SELECTED));
            vt.onTooltip = new Runnable() {
                @Override
                public void run() {
                    String rules;
                    try {
                        rules = CombatScreen.keywordRules(sides[index]
                                .findState(FightLog.Temporality.Present, ent)
                                .getCalculatedEffect().getKeywords());
                    } catch (Throwable t) {
                        SndLog.error("sheet side keyword rules failed", t);
                        rules = null;
                    }
                    host.speech().speak(rules, false);
                }
            };
            b.addItem(ControlId.structural(CompositeKey.of("sheet", "side", index)), vt);
        }

        List<Item> items = ent.getItems();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                final Item item = items.get(i);
                NodeVtable vt = textVtable();
                vt.announcements = Arrays.asList(
                        NodeAnnouncement.kinded(new Supplier<String>() {
                            @Override
                            public String get() {
                                return GameText.t(item.getName());
                            }
                        }, AnnouncementKinds.LABEL),
                        NodeAnnouncement.kinded(new Supplier<String>() {
                            @Override
                            public String get() {
                                String desc = item.getDescription();
                                return desc == null || desc.trim().isEmpty()
                                        ? null : GameText.t(desc);
                            }
                        }, AnnouncementKinds.VALUE));
                b.addItem(ControlId.structural(CompositeKey.of("sheet", "item", i)), vt);
            }
        }

        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        if (present != null) {
            int i = 0;
            for (Personal p : present.getActivePersonals()) {
                if (!p.hasImage()) {
                    continue; // invisible mechanics don't show on the panel either
                }
                final Personal personal = p;
                NodeVtable vt = textVtable();
                vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return GameText.t(personal.describeForTriggerPanel());
                    }
                }, AnnouncementKinds.LABEL));
                b.addItem(ControlId.structural(CompositeKey.of("sheet", "status", i)), vt);
                i++;
            }
        }
    }

    /**
     * The whole sheet as one burst — for contexts that read it without
     * pushing the panel (the party management hero nodes).
     */
    static String sheetText(DungeonScreen ds, Ent ent) {
        StringBuilder sb = new StringBuilder(headerText(ds, ent));
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        if (present != null && present.isDead()) {
            sb.append(", ").append(Loc.get("combat", "defeated"));
            if (ent.isPlayer()) {
                // The sheet's skull-tag rule, the game's own sentence.
                sb.append(". ").append(GameText.t("Heroes defeated last fight return with half hp"));
            }
        }
        EntSide[] sides = ent.getSides();
        for (int i = 0; i < sides.length; i++) {
            sb.append(i == 0 ? ". " : ", ").append(i + 1).append(": ")
                    .append(GameText.t(sides[i].findState(FightLog.Temporality.Present, ent).describe()));
        }
        List<Item> items = ent.getItems();
        if (items != null && !items.isEmpty()) {
            sb.append(". ").append(GameText.t("Items")).append(": ");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(GameText.t(items.get(i).getName()));
                String desc = items.get(i).getDescription();
                if (desc != null && !desc.trim().isEmpty()) {
                    sb.append(", ").append(GameText.t(desc));
                }
            }
        }
        if (present != null) {
            for (Personal p : present.getActivePersonals()) {
                if (!p.hasImage()) {
                    continue;
                }
                sb.append(". ").append(GameText.t(p.describeForTriggerPanel()));
            }
        }
        return sb.toString();
    }

    static String headerText(DungeonScreen ds, Ent ent) {
        StringBuilder sb = new StringBuilder(GameUi.entName(ent));
        if (ent instanceof Hero) {
            sb.append(", ").append(Loc.get("combat", "level", "n", ((Hero) ent).getLevel()));
        }
        String health = CombatScreen.healthText(ds, ent);
        if (health != null) {
            sb.append(", ").append(health);
        }
        return sb.toString();
    }

    private static boolean isRolled(Ent ent, int index) {
        try {
            EntDie die = ent.getDie();
            return die.getState() != Die.DieState.Rolling
                    && die.getCurrentSide() != null
                    && die.getSideIndex() == index;
        } catch (Throwable t) {
            return false;
        }
    }

    private static NodeVtable textVtable() {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TEXT;
        // Backspace toggles the sheet closed — the same key that opened it.
        vt.onSecondary = CLOSE;
        return vt;
    }

    private static final Runnable CLOSE = new Runnable() {
        @Override
        public void run() {
            Screen screen = Main.getCurrentScreen();
            if (screen != null) {
                screen.popAllLight(); // the game's own light-pop, Escape's first step
            }
        }
    };
}
