package snd.module.screens;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.content.ent.die.Die;
import com.tann.dice.gameplay.content.ent.die.EntDie;
import com.tann.dice.gameplay.content.ent.die.side.EntSide;
import com.tann.dice.gameplay.effect.targetable.Targetable;
import com.tann.dice.gameplay.fightLog.EntState;
import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.gameplay.phase.Phase;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.gameplay.DamagePhase;
import com.tann.dice.gameplay.phase.gameplay.EnemyRollingPhase;
import com.tann.dice.gameplay.phase.gameplay.PlayerRollingPhase;
import com.tann.dice.gameplay.phase.gameplay.SurrenderPhase;
import com.tann.dice.gameplay.phase.gameplay.TargetingPhase;
import com.tann.dice.screens.dungeon.DungeonScreen;
import com.tann.dice.screens.dungeon.TargetingManager;

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
        buildEntityStop(b, ds, true);
        buildEntityStop(b, ds, false);
        Phase phase = PhaseManager.get().getPhase();
        if (phase instanceof PlayerRollingPhase) {
            buildButtons(b, ds, ds.doneRollingButton, true);
        } else if (phase instanceof TargetingPhase) {
            buildButtons(b, ds, ds.confirmButton, false);
        }
    }

    // ---- the two combatant columns: one node per hero/monster ----

    private void buildEntityStop(GraphBuilder b, final DungeonScreen ds, boolean heroes) {
        String key = heroes ? "heroes" : "enemies";
        b.beginStop(key).pushContext(Loc.get("combat", key), Loc.get("ui", "role.list"));
        // Dead heroes keep their place (the greyed skull panel); dead
        // monsters vanish, matching the visual column.
        List<Ent> ents = ds.getFightLog().getSnapshot(FightLog.Temporality.Present)
                .getEntities(heroes, heroes ? null : Boolean.FALSE);
        for (int i = 0; i < ents.size(); i++) {
            final Ent ent = ents.get(i);
            b.addItem(ControlId.referenced(ent, CompositeKey.of(key, i)), entNode(ds, ent));
        }
        b.popContext();
    }

    private NodeVtable entNode(final DungeonScreen ds, final Ent ent) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return GameText.t(ent.getName(true));
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        // For a monster, the locked face IS its intent for the turn.
                        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
                        if (present != null && present.isDead()) {
                            return null;
                        }
                        return currentSideText(ent);
                    }
                }, AnnouncementKinds.VALUE),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return dieUseState(ds, ent);
                    }
                }, AnnouncementKinds.SELECTED),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return healthText(ds, ent);
                    }
                }, AnnouncementKinds.ENABLED),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return previewText(ds, ent);
                    }
                }, AnnouncementKinds.ENABLED),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        // The valid-target border highlight, as a word.
                        return isValidTarget(ds, ent) ? Loc.get("combat", "target_valid") : null;
                    }
                }, AnnouncementKinds.ENABLED));
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                // The digit-key route: toggles the lock while rolling, selects
                // the side or applies the selection while targeting. A vetoed
                // target names its reason (the explanel's red text, spoken).
                Targetable selected = ds.targetingManager.getSelectedTargetable();
                if (selected != null && PhaseManager.get().getPhase().canTarget()) {
                    String reason = ds.targetingManager.getInvalidTargetReason(ent, selected, true);
                    if (reason != null) {
                        host.speech().speak(GameText.t(reason), true);
                        return;
                    }
                }
                ds.targetingManager.clicked(ent, true);
            }
        };
        // The full character sheet (the right-click EntPanelInventory), as
        // one readout: name, level, hp, die net, equipped items, statuses.
        vt.onSecondary = new Runnable() {
            @Override
            public void run() {
                host.speech().speak(sheetText(ds, ent), false);
            }
        };
        return vt;
    }

    private static String sheetText(DungeonScreen ds, Ent ent) {
        StringBuilder sb = new StringBuilder(GameText.t(ent.getName(true)));
        if (ent instanceof com.tann.dice.gameplay.content.ent.Hero) {
            sb.append(", ").append(Loc.get("combat", "level", "n",
                    ((com.tann.dice.gameplay.content.ent.Hero) ent).getLevel()));
        }
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        String health = healthText(ds, ent);
        if (health != null) {
            sb.append(", ").append(health);
        }
        if (present != null && present.isDead()) {
            sb.append(", ").append(Loc.get("combat", "defeated"));
            if (ent.isPlayer()) {
                // The sheet's skull-tag rule, the game's own sentence.
                sb.append(". ").append(GameText.t("Heroes defeated last fight return with half hp"));
            }
        }
        sb.append(". ").append(dieNetText(ent));
        List<com.tann.dice.gameplay.content.item.Item> items = ent.getItems();
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
        String statuses = statusText(ds, ent);
        if (statuses != null) {
            sb.append(". ").append(statuses);
        }
        return sb.toString();
    }

    // "defeated" for a dead hero's greyed panel; "locked" while rolling (the
    // slide-to-panel visual); "used" while targeting (the dark overlay, its
    // only visual cue).
    private static String dieUseState(DungeonScreen ds, Ent ent) {
        if (!ent.isPlayer()) {
            return null;
        }
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        if (present != null && present.isDead()) {
            return Loc.get("combat", "defeated");
        }
        if (PhaseManager.get().getPhase() instanceof TargetingPhase) {
            return ds.getFightLog().getSnapshot(FightLog.Temporality.Present)
                    .getNumDiceUsedThisTurn(ent) > 0 ? Loc.get("combat", "used") : null;
        }
        return ent.getDie().getState().isLockedOrLocking() ? GameText.t("locked") : null;
    }

    // The HP pip grid and shield badge, as text: "8 of 10 hp, shield 2".
    private static String healthText(DungeonScreen ds, Ent ent) {
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        if (present == null) {
            return null;
        }
        String text = Loc.get("combat", "hp", "hp", present.getHp(), "max", present.getMaxHp());
        if (present.getShields() > 0) {
            text += ", " + GameText.t("Shield") + " " + present.getShields();
        }
        return text;
    }

    // The damage preview the pips paint in colour: yellow = incoming
    // blockable, green = incoming poison, the red flash = the Future
    // snapshot's death (grey = flees).
    private static String previewText(DungeonScreen ds, Ent ent) {
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        EntState future = ds.getFightLog().getState(FightLog.Temporality.Future, ent);
        if (present == null || future == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int incoming = present.getIncomingDamage();
        if (incoming > 0) {
            sb.append(Loc.get("combat", "incoming_damage", "n", incoming));
        }
        int poison = future.getPoisonDamageTaken(true) - present.getPoisonDamageTaken(true);
        if (poison > 0) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(Loc.get("combat", "incoming_poison", "n", poison));
        }
        if (future.isDead() && !present.isDead()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(Loc.get("combat", future.isFled() ? "flees" : "dies"));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // Each active buff/debuff's own description — the 5x5 icon grid's text.
    private static String statusText(DungeonScreen ds, Ent ent) {
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        if (present == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (com.tann.dice.gameplay.trigger.personal.Personal p : present.getActivePersonals()) {
            if (!p.hasImage()) {
                continue; // invisible mechanics don't show on the panel either
            }
            if (sb.length() > 0) {
                sb.append(". ");
            }
            sb.append(GameText.t(p.describeForTriggerPanel()));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static boolean isValidTarget(DungeonScreen ds, Ent ent) {
        Targetable selected = ds.targetingManager.getSelectedTargetable();
        if (selected == null || !PhaseManager.get().getPhase().canTarget()) {
            return false;
        }
        return TargetingManager.getValidTargets(
                ds.getFightLog().getSnapshot(FightLog.Temporality.Present), selected, true)
                .contains(ent);
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

    // ---- the phase's buttons: Reroll (n/max, rolling only) and the
    // phase's confirm ("Done Rolling" / "End turn", its own state text) ----

    private void buildButtons(GraphBuilder b, final DungeonScreen ds,
            final com.tann.dice.screens.dungeon.panels.ConfirmButton confirmButton, boolean rolling) {
        b.beginStop("buttons");

        if (rolling) {
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
        }

        NodeVtable confirm = new NodeVtable();
        confirm.controlType = ControlTypes.BUTTON;
        confirm.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                String label = GameUi.confirmLabel(confirmButton);
                return label != null ? label : Loc.get("combat", "confirm");
            }
        }, AnnouncementKinds.LABEL));
        confirm.onActivate = new Runnable() {
            @Override
            public void run() {
                ds.confirmClicked(true); // the Space path: confirms the phase
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
