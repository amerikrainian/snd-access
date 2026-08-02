package snd.module.screens;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.content.ent.die.Die;
import com.tann.dice.gameplay.content.ent.die.EntDie;
import com.tann.dice.gameplay.content.ent.die.side.EntSide;
import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.gameplay.phase.Phase;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.gameplay.DamagePhase;
import com.tann.dice.gameplay.phase.gameplay.EnemyRollingPhase;
import com.tann.dice.gameplay.phase.gameplay.PlayerRollingPhase;
import com.tann.dice.gameplay.phase.gameplay.SurrenderPhase;
import com.tann.dice.gameplay.phase.gameplay.TargetingPhase;
import com.tann.dice.screens.dungeon.DungeonScreen;

import snd.core.HostServices;
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
 * The fight itself: heroes with their dice, and the current phase's own
 * buttons. Everything reads the FightLog's Present snapshot and drives the
 * game's own input paths — activating a hero runs
 * {@code targetingManager.clicked}, the digit-key route, so it toggles the
 * die's lock while rolling and selects the side while targeting; Reroll and
 * the confirm button run the R and Space handlers. The game's combat hotkeys
 * keep working alongside (digits, R, Z, Space fall through the navigator).
 */
public class CombatScreen extends AccessScreen {
    private final HostServices host;

    public CombatScreen(HostServices host) {
        this.host = host;
    }

    @Override
    public String key() {
        return "combat";
    }

    @Override
    public boolean isActive() {
        if (!(com.tann.dice.Main.getCurrentScreen() instanceof DungeonScreen)) {
            return false;
        }
        Phase p = PhaseManager.get().getPhase();
        return p instanceof EnemyRollingPhase || p instanceof PlayerRollingPhase
                || p instanceof TargetingPhase || p instanceof DamagePhase
                || p instanceof SurrenderPhase;
    }

    @Override
    public void build(GraphBuilder b) {
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null || ds.getFightLog() == null) {
            return;
        }
        buildHeroesStop(b, ds);
        Phase phase = PhaseManager.get().getPhase();
        if (phase instanceof PlayerRollingPhase) {
            buildRollingButtons(b, ds);
        }
    }

    // ---- the hero column: one node per hero, die state included ----

    private void buildHeroesStop(GraphBuilder b, final DungeonScreen ds) {
        b.beginStop("heroes").pushContext(Loc.get("combat", "heroes"), Loc.get("ui", "role.list"));
        List<Ent> heroes = ds.getFightLog().getSnapshot(FightLog.Temporality.Present)
                .getEntities(true, false);
        for (int i = 0; i < heroes.size(); i++) {
            final Ent hero = heroes.get(i);
            b.addItem(ControlId.referenced(hero, CompositeKey.of("hero", i)), heroNode(ds, hero));
        }
        b.popContext();
    }

    private NodeVtable heroNode(final DungeonScreen ds, final Ent hero) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return GameText.t(hero.getName(true));
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return currentSideText(hero);
                    }
                }, AnnouncementKinds.VALUE),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        // The game's own word — the dark overlay/slide is the visual.
                        return hero.getDie().getState().isLockedOrLocking()
                                ? GameText.t("locked") : null;
                    }
                }, AnnouncementKinds.SELECTED));
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                ds.targetingManager.clicked(hero, true);
            }
        };
        // The die net — every side's calculated text, the keyboard stand-in
        // for the right-click RollPanel.
        vt.onSecondary = new Runnable() {
            @Override
            public void run() {
                host.speech().speak(dieNetText(hero), false);
            }
        };
        return vt;
    }

    /** The rolled side's calculated, buff-adjusted text, or "rolling" mid-tumble. */
    public static String currentSideText(Ent ent) {
        EntDie die = ent.getDie();
        EntSide side = die.getCurrentSide();
        if (side == null || die.getState() == Die.DieState.Rolling) {
            return Loc.get("combat", "die_rolling");
        }
        return GameText.t(side.findState(FightLog.Temporality.Present, ent).describe());
    }

    private static String dieNetText(Ent ent) {
        EntSide[] sides = ent.getSides();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sides.length; i++) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(i + 1).append(": ")
                    .append(GameText.t(sides[i].findState(FightLog.Temporality.Present, ent).describe()));
        }
        return sb.toString();
    }

    // ---- the rolling phase's buttons: Reroll (n/max) and Done Rolling ----

    private void buildRollingButtons(GraphBuilder b, final DungeonScreen ds) {
        b.beginStop("buttons");

        NodeVtable reroll = new NodeVtable();
        reroll.controlType = ControlTypes.BUTTON;
        reroll.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return GameText.t("Reroll");
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return rollCounter(ds);
                    }
                }, AnnouncementKinds.VALUE));
        reroll.onActivate = new Runnable() {
            @Override
            public void run() {
                ds.rollManager.requestPlayerRoll(); // the R-key path; vetoes speak via the game
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("buttons", "reroll")), reroll);

        NodeVtable confirm = new NodeVtable();
        confirm.controlType = ControlTypes.BUTTON;
        confirm.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                String label = GameUi.confirmLabel(ds.doneRollingButton);
                return label != null ? label : Loc.get("combat", "confirm");
            }
        }, AnnouncementKinds.LABEL));
        confirm.onActivate = new Runnable() {
            @Override
            public void run() {
                ds.confirmClicked(true); // the Space path: locks all dice and confirms
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("buttons", "confirm")), confirm);
    }

    /** "2/3" — the counter the visual UI draws on the Reroll button. */
    public static String rollCounter(DungeonScreen ds) {
        return ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getRolls()
                + "/" + ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getMaxRolls();
    }
}
