package snd.module.screens;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.content.ent.Hero;
import com.tann.dice.gameplay.content.ent.die.side.EntSide;
import com.tann.dice.gameplay.fightLog.EntState;
import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.ChoicePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.ChoiceType;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.Choosable;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.special.LevelupHeroChoosable;
import com.tann.dice.screens.dungeon.DungeonScreen;
import com.tann.dice.util.lang.Words;
import com.tann.dice.util.ui.choice.ChoiceDialog;

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
import snd.core.nav.KeyOffer;
import snd.module.ChoicePhases;
import snd.module.GameKeys;
import snd.module.GameText;
import snd.module.GameUi;

/**
 * The choice-confirmation dialog a ChoicePhase pushes before committing. For
 * a single level-up the game shows the current hero's sheet, an arrow, and
 * the upgraded hero's sheet; that reads here as two column-aligned rows —
 * header plus the six sides with their keyword rules as details — so
 * up/down compares the same side before and after. Other confirmations read
 * one node per chosen option. Yes and cancel drive the dialog's own key
 * route ({@code ChoiceDialog.keyPress}), the same path its physical Enter
 * and Backspace take.
 */
public class ChoiceConfirmScreen extends AccessScreen {
    @Override
    public String key() {
        return "choice-confirm";
    }

    @Override
    public List<KeyOffer> keys() {
        return GameKeys.escapeOnly();
    }

    @Override
    public int layer() {
        return 21; // replaces the generic modal reader for this dialog
    }

    @Override
    public boolean isActive() {
        return dialog() != null && !chosen().isEmpty();
    }

    private static ChoicePhase phase() {
        if (com.tann.dice.Main.getCurrentScreen() == null) {
            return null;
        }
        com.tann.dice.gameplay.phase.Phase p = PhaseManager.get().getPhase();
        return p instanceof ChoicePhase ? (ChoicePhase) p : null;
    }

    /** The pushed confirmation dialog, or null. */
    private static ChoiceDialog dialog() {
        if (phase() == null) {
            return null;
        }
        Object modal = GameUi.topModal();
        return modal instanceof ChoiceDialog ? (ChoiceDialog) modal : null;
    }

    /** The pending selection the dialog is asking about. */
    private static List<Choosable> chosen() {
        try {
            ChoicePhase p = phase();
            ChoiceType ct = p == null ? null : ChoicePhases.choiceType(p);
            List<Choosable> chosen = ct == null ? null : ChoicePhases.currentChoices(ct);
            return chosen == null ? java.util.Collections.<Choosable>emptyList() : chosen;
        } catch (Throwable t) {
            SndLog.error("failed to read the pending choice", t);
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public String screenName() {
        List<Choosable> chosen = chosen();
        if (chosen.isEmpty()) {
            return null;
        }
        // The game's own title composition; the full phrase is a lang key.
        return GameText.t("Confirm " + Words.plural("choice", chosen.size()));
    }

    @Override
    public void build(GraphBuilder b) {
        final ChoicePhase p = phase();
        final ChoiceDialog cd = dialog();
        List<Choosable> chosen = chosen();
        if (p == null || cd == null || chosen.isEmpty()) {
            return;
        }
        b.beginStop("confirm");

        List<Choosable> options = ChoicePhases.options(p);
        LevelupHeroChoosable levelup = chosen.size() == 1 && chosen.get(0) instanceof LevelupHeroChoosable
                ? (LevelupHeroChoosable) chosen.get(0) : null;
        if (levelup != null) {
            buildLevelupComparison(b, levelup, options.indexOf(levelup));
        } else {
            for (int i = 0; i < chosen.size(); i++) {
                final Choosable option = chosen.get(i);
                final int optIndex = options.indexOf(option);
                NodeVtable vt = new NodeVtable();
                vt.controlType = ControlTypes.TEXT;
                vt.announcements = Arrays.asList(
                        NodeAnnouncement.kinded(new Supplier<String>() {
                            @Override
                            public String get() {
                                return ChoiceScreen.nameOf(option);
                            }
                        }, AnnouncementKinds.LABEL),
                        NodeAnnouncement.kinded(new Supplier<String>() {
                            @Override
                            public String get() {
                                return ChoiceScreen.valueOf(option, optIndex);
                            }
                        }, AnnouncementKinds.VALUE),
                        NodeAnnouncement.kinded(new Supplier<String>() {
                            @Override
                            public String get() {
                                return ChoiceScreen.effectOf(option, optIndex);
                            }
                        }, AnnouncementKinds.TOOLTIP));
                b.addItem(ControlId.structural(CompositeKey.of("confirm", "option", i)), vt);
            }
        }

        // Below the choice, one under the other: Down from what is being
        // confirmed reaches cancel, then yes. The dialog's own key route:
        // index 0 = decline, 1 = accept.
        addButton(b, "cancel", GameText.t("cancel"), cd, 67);
        addButton(b, "yes", GameText.t("yes"), cd, 66);
    }

    // The before and after sheets as two rows sharing a key, so vertical
    // navigation compares the same side across the upgrade.
    private void buildLevelupComparison(GraphBuilder b, LevelupHeroChoosable option, int optIndex) {
        final Hero current = ChoiceScreen.targetHero(option, optIndex);
        final Hero upgraded = ChoiceScreen.upgradedHero(option, optIndex);
        final DungeonScreen ds = DungeonScreen.get();
        if (current == null || upgraded == null || ds == null) {
            return;
        }

        b.startRow("confirm-hero");
        NodeVtable before = new NodeVtable();
        before.controlType = ControlTypes.TEXT;
        before.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                StringBuilder sb = new StringBuilder(GameUi.entName(current));
                sb.append(", ").append(Loc.get("combat", "level", "n", current.getLevel()));
                String health = CombatScreen.healthText(ds, current);
                if (health != null) {
                    sb.append(", ").append(health);
                }
                return sb.toString();
            }
        }, AnnouncementKinds.LABEL));
        b.addItem(ControlId.structural(CompositeKey.of("confirm", "before")), before);
        for (final int side : SideText.readingOrder()) {
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.TEXT;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return SideText.at(side, SideText.of(currentSide(current, side)));
                }
            }, AnnouncementKinds.LABEL));
            vt.details = CombatScreen.ruleDetails(new Supplier<com.tann.dice.gameplay.effect.eff.Eff>() {
                @Override
                public com.tann.dice.gameplay.effect.eff.Eff get() {
                    return currentSide(current, side).getCalculatedEffect();
                }
            });
            b.addItem(ControlId.structural(CompositeKey.of("confirm", "before", side)), vt);
        }
        b.endRow();

        b.startRow("confirm-hero");
        final EntState blank = upgraded.getBlankState();
        NodeVtable after = new NodeVtable();
        after.controlType = ControlTypes.TEXT;
        after.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", "confirm.becomes", "name", GameUi.entName(upgraded))
                        + ", " + Loc.get("combat", "level", "n", upgraded.getLevel())
                        + ", " + blank.getMaxHp() + " " + GameText.t("hp");
            }
        }, AnnouncementKinds.LABEL));
        b.addItem(ControlId.structural(CompositeKey.of("confirm", "after")), after);
        for (final int side : SideText.readingOrder()) {
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.TEXT;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return SideText.at(side, SideText.of(blank.getSideState(side)));
                }
            }, AnnouncementKinds.LABEL));
            vt.details = CombatScreen.ruleDetails(new Supplier<com.tann.dice.gameplay.effect.eff.Eff>() {
                @Override
                public com.tann.dice.gameplay.effect.eff.Eff get() {
                    return blank.getSideState(side).getCalculatedEffect();
                }
            });
            b.addItem(ControlId.structural(CompositeKey.of("confirm", "after", side)), vt);
        }
        b.endRow();
    }

    private static com.tann.dice.gameplay.fightLog.EntSideState currentSide(Ent ent, int side) {
        EntSide[] sides = ent.getSides();
        return sides[side].findState(FightLog.Temporality.Present, ent);
    }


    private static void addButton(GraphBuilder b, Object key, final String label,
            final ChoiceDialog cd, final int keycode) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return label;
            }
        }, AnnouncementKinds.LABEL));
        vt.speaksOwnPosition = true; // two answers under a sheet are not a list to count
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                cd.keyPress(keycode);
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("confirm", key)), vt);
    }
}
