package snd.module.screens;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.tann.dice.gameplay.content.ent.Hero;
import com.tann.dice.gameplay.content.ent.type.HeroType;
import com.tann.dice.gameplay.fightLog.EntState;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.ChoicePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.ChoiceType;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.Choosable;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.special.LevelupHeroChoosable;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.special.OrChoosable;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.special.ReplaceChoosable;
import com.tann.dice.screens.dungeon.DungeonScreen;
import com.tann.dice.util.lang.Words;

import snd.core.HostServices;
import snd.core.SndLog;
import snd.core.loc.Loc;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.nav.AccessScreen;
import snd.module.ChoicePhases;
import snd.module.GameText;
import snd.module.GameUi;

/**
 * The reward/decision offer (ChoicePhase): level-up and loot choices, curse
 * and blessing picks, the starting difficulty pick. The offer is drawn
 * straight onto the dungeon screen rather than pushed as a modal, so it gets
 * its own screen rather than riding the generic modal reader.
 *
 * <p>Options speak their real identity and effect from the underlying
 * Choosable (level-ups read the upgraded hero's full sheet); toggling runs
 * the phase's own path — the exact route its number keys and clicks take,
 * confirmation dialogs included. The choice styles surface their own
 * controls: a Confirm button for up-to-N offers, the running tally plus
 * reset/confirm for point-buy, accept/decline for optional offers, and the
 * first-fight anticheese reroll button.</p>
 */
public class ChoiceScreen extends AccessScreen {
    private final HostServices host;

    public ChoiceScreen(HostServices host) {
        this.host = host;
    }

    @Override
    public String key() {
        return "choice";
    }

    @Override
    public int layer() {
        return 10; // above the combat screen, below pushed modals
    }

    @Override
    public boolean isActive() {
        return phase() != null;
    }

    private static ChoicePhase phase() {
        try {
            if (com.tann.dice.Main.getCurrentScreen() == null) {
                return null;
            }
            com.tann.dice.gameplay.phase.Phase p = PhaseManager.get().getPhase();
            return p instanceof ChoicePhase ? (ChoicePhase) p : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String screenName() {
        ChoicePhase p = phase();
        if (p == null) {
            return null;
        }
        String header = ChoicePhases.header(p);
        if (header == null) {
            header = Loc.get("ui", "choice.header");
        }
        String top = ChoicePhases.topMessage(p);
        return top != null && !top.trim().isEmpty() ? GameText.t(top) + ", " + header : header;
    }

    @Override
    public void build(GraphBuilder b) {
        final ChoicePhase p = phase();
        if (p == null) {
            return;
        }
        final ChoiceType ct = ChoicePhases.choiceType(p);
        String style = ct != null ? ChoicePhases.style(ct) : null;
        final boolean optional = "Optional".equals(style);
        final boolean pointBuy = "PointBuy".equals(style);
        List<Choosable> options = ChoicePhases.options(p);

        for (int i = 0; i < options.size(); i++) {
            final Choosable option = options.get(i);
            final int index = i;
            NodeVtable vt = new NodeVtable();
            vt.controlType = optional ? ControlTypes.TEXT : ControlTypes.BUTTON;
            vt.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return nameOf(option);
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return valueOf(option, index);
                        }
                    }, AnnouncementKinds.VALUE),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return ct != null && ChoicePhases.currentChoices(ct).contains(option)
                                    ? Loc.get("ui", "state.selected") : null;
                        }
                    }, AnnouncementKinds.SELECTED),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return effectOf(option, index);
                        }
                    }, AnnouncementKinds.TOOLTIP));
            if (!optional) {
                vt.onActivate = new Runnable() {
                    @Override
                    public void run() {
                        ChoicePhases.toggle(p, option);
                    }
                };
                vt.stateText = new Supplier<String>() {
                    @Override
                    public String get() {
                        if (ct == null) {
                            return null;
                        }
                        boolean selected = ChoicePhases.currentChoices(ct).contains(option);
                        String state = selected ? Loc.get("ui", "state.selected")
                                : Loc.get("combat", "deselected");
                        return pointBuy ? state + ", " + tallyText(ct) : state;
                    }
                };
            }
            b.addItem(ControlId.referenced(option, CompositeKey.of("choice", i, option.getSaveString())), vt);
        }

        if ("UpToNumber".equals(style)) {
            NodeVtable confirm = new NodeVtable();
            confirm.controlType = ControlTypes.BUTTON;
            confirm.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t("Confirm");
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return Loc.get("ui", "choice.selected_count",
                                    "n", ChoicePhases.currentChoices(ct).size(),
                                    "max", ChoicePhases.targetValue(ct));
                        }
                    }, AnnouncementKinds.VALUE));
            confirm.onActivate = new Runnable() {
                @Override
                public void run() {
                    ChoicePhases.choose(p, ChoicePhases.currentChoices(ct), true);
                }
            };
            b.addItem(ControlId.structural(CompositeKey.of("choice", "confirm")), confirm);
        }

        if (pointBuy) {
            b.addLabel(ControlId.structural(CompositeKey.of("choice", "tally")),
                    new Supplier<String>() {
                        @Override
                        public String get() {
                            return tallyText(ct);
                        }
                    });

            NodeVtable reset = new NodeVtable();
            reset.controlType = ControlTypes.BUTTON;
            reset.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return Loc.get("ui", "choice.reset");
                }
            }, AnnouncementKinds.LABEL));
            reset.onActivate = new Runnable() {
                @Override
                public void run() {
                    ChoicePhases.clearChoices(p);
                }
            };
            reset.stateText = new Supplier<String>() {
                @Override
                public String get() {
                    return tallyText(ct);
                }
            };
            b.addItem(ControlId.structural(CompositeKey.of("choice", "reset")), reset);

            NodeVtable confirm = new NodeVtable();
            confirm.controlType = ControlTypes.BUTTON;
            confirm.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t("Confirm");
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return tallyText(ct);
                        }
                    }, AnnouncementKinds.VALUE));
            confirm.onActivate = new Runnable() {
                @Override
                public void run() {
                    if (!ct.checkValid(true)) {
                        host.speech().speak(Loc.get("ui", "choice.invalid") + ", " + tallyText(ct), true);
                        return;
                    }
                    ChoicePhases.choose(p, ChoicePhases.currentChoices(ct), true);
                }
            };
            b.addItem(ControlId.structural(CompositeKey.of("choice", "pb_confirm")), confirm);
        }

        if (optional) {
            // The offer is wrapped in an accept/decline dialog whose options
            // carry no toggle listeners; the two buttons run the dialog's own
            // routes.
            NodeVtable accept = new NodeVtable();
            accept.controlType = ControlTypes.BUTTON;
            accept.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return GameText.t("accept");
                }
            }, AnnouncementKinds.LABEL));
            final List<Choosable> all = options;
            accept.onActivate = new Runnable() {
                @Override
                public void run() {
                    ChoicePhases.choose(p, all, true);
                }
            };
            b.addItem(ControlId.structural(CompositeKey.of("choice", "accept")), accept);

            NodeVtable decline = new NodeVtable();
            decline.controlType = ControlTypes.BUTTON;
            decline.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return GameText.t("decline");
                }
            }, AnnouncementKinds.LABEL));
            decline.onActivate = new Runnable() {
                @Override
                public void run() {
                    ChoicePhases.endPhase(p);
                }
            };
            b.addItem(ControlId.structural(CompositeKey.of("choice", "decline")), decline);
        }

        buildAnticheeseReroll(b, p, options.size());
    }

    private static String tallyText(ChoiceType ct) {
        int total = 0;
        for (Choosable c : ChoicePhases.currentChoices(ct)) {
            total += c.getTier();
        }
        return Loc.get("ui", "choice.value_tally",
                "current", GameText.t(Words.getTierString(total, false)),
                "target", GameText.t(Words.getTierString(ChoicePhases.targetValue(ct), false)));
    }

    // The tiny unlabeled icon button top-right of first-fight offers: reroll
    // the starting party and options. Found by its texture under the offer
    // group; activation fires the game's own listener, warning dialogs and
    // all.
    private void buildAnticheeseReroll(GraphBuilder b, ChoicePhase p, int optionCount) {
        try {
            DungeonScreen ds = DungeonScreen.get();
            if (ds == null || optionCount == 2) {
                return; // mirrors the game's addRerollButtonMaybe condition
            }
            com.tann.dice.gameplay.context.DungeonContext dc = ds.getDungeonContext();
            if (!dc.isFirstLevel() || !dc.getContextConfig().usesAnticheese()) {
                return;
            }
            Group group = ChoicePhases.choiceGroup(p);
            if (group == null) {
                return;
            }
            final Actor button = findFlaffButton(group);
            if (button == null) {
                return;
            }
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.BUTTON;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return Loc.get("ui", "choice.reroll_start");
                }
            }, AnnouncementKinds.LABEL));
            vt.onActivate = new Runnable() {
                @Override
                public void run() {
                    GameUi.activate(button);
                }
            };
            b.addItem(ControlId.referenced(button, CompositeKey.of("choice", "anticheese")), vt);
        } catch (Throwable t) {
            SndLog.error("anticheese reroll node failed", t);
        }
    }

    // The reroll button is the direct child of the offer group that carries a
    // listener and the flaff icon.
    private static Actor findFlaffButton(Group group) {
        for (Actor child : group.getChildren()) {
            if (!GameUi.hasTannListener(child)) {
                continue;
            }
            if (hasFlaffImage(child)) {
                return child;
            }
        }
        return null;
    }

    private static boolean hasFlaffImage(Actor actor) {
        if (actor instanceof com.tann.dice.util.ImageActor) {
            return ((com.tann.dice.util.ImageActor) actor).tr == com.tann.dice.statics.Images.flaff;
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                if (hasFlaffImage(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- what each option IS: name, short value, full effect ----

    // An option's real identity: modifiers, items, and the special choosables
    // all self-describe through Choosable.getName (Or/And compose their
    // children, level-ups name the upgraded class).
    private static String nameOf(Choosable option) {
        try {
            String name = option.getName();
            if (name != null && !name.trim().isEmpty()) {
                return GameText.t(name);
            }
            String described = safeDescribe(option);
            return described != null ? described : Loc.get("ui", "choice.unreadable");
        } catch (Throwable t) {
            SndLog.error("failed to name a choice option", t);
            return Loc.get("ui", "choice.unreadable");
        }
    }

    // The short qualifier: the type word ("curse", "item"), the tier, and for
    // level-ups which hero upgrades.
    private static String valueOf(Choosable option, int index) {
        StringBuilder sb = new StringBuilder();
        if (option instanceof LevelupHeroChoosable) {
            try {
                Hero target = targetHero((LevelupHeroChoosable) option, index);
                if (target != null) {
                    sb.append(Loc.get("ui", "choice.upgrades", "hero", GameText.t(target.getName(true))));
                }
            } catch (Throwable t) {
                SndLog.error("failed to find level-up target hero", t);
            }
        }
        String type = safeDescribe(option);
        // Composite choosables' describe() repeats their full name; only the
        // short type words qualify.
        if (type != null && !type.equals(option.getName()) && type.length() <= 40) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(type);
        }
        int tier = option.getTier();
        if (tier != 0) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(Loc.get("ui", "choice.tier", "tier", tier));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // The full effect text — the same content the visual panels render.
    private static String effectOf(Choosable option, int index) {
        try {
            if (option instanceof com.tann.dice.gameplay.modifier.Modifier) {
                return GameText.t(((com.tann.dice.gameplay.modifier.Modifier) option).getFullDescription());
            }
            if (option instanceof com.tann.dice.gameplay.content.item.Item) {
                return GameText.t(((com.tann.dice.gameplay.content.item.Item) option).getDescription());
            }
            if (option instanceof LevelupHeroChoosable) {
                return levelupSheet((LevelupHeroChoosable) option, index);
            }
            if (option instanceof ReplaceChoosable) {
                ReplaceChoosable rc = (ReplaceChoosable) option;
                String gain = nameOf(rc.gain);
                String effect = effectOf(rc.gain, index);
                return effect != null ? gain + ", " + effect : gain;
            }
            if (option instanceof OrChoosable) {
                StringBuilder sb = new StringBuilder();
                for (Choosable child : ((OrChoosable) option).getAll()) {
                    String effect = effectOf(child, index);
                    if (effect == null) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append("; ");
                    }
                    sb.append(nameOf(child)).append(": ").append(effect);
                }
                return sb.length() > 0 ? sb.toString() : null;
            }
            return null;
        } catch (Throwable t) {
            SndLog.error("failed to describe a choice option", t);
            return null;
        }
    }

    private static String safeDescribe(Choosable option) {
        try {
            return GameText.t(option.describe());
        } catch (Throwable t) {
            SndLog.error("Choosable.describe failed", t);
            return null;
        }
    }

    private static Hero targetHero(LevelupHeroChoosable option, int index) {
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null) {
            return null;
        }
        return ds.getDungeonContext().getParty().getHeroFor(option.getHeroType(), index);
    }

    // The upgraded hero's full character sheet — the same numbers the offer's
    // EntPanelInventory renders, from a hypothetical hero built the way the
    // game builds its panel.
    private static String levelupSheet(LevelupHeroChoosable option, int index) {
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null) {
            return null;
        }
        HeroType ht = option.getHeroType();
        Hero target = targetHero(option, index);
        Hero upgraded = (target != null ? target.transformLevelup(ht) : ht).makeEnt();
        upgraded.setRealFightLog(ds.getFightLog());
        EntState blank = upgraded.getBlankState();
        StringBuilder sb = new StringBuilder();
        sb.append(Loc.get("combat", "level", "n", upgraded.getLevel()));
        sb.append(", ").append(blank.getMaxHp()).append(" ").append(GameText.t("hp"));
        for (int i = 0; i < 6; i++) {
            sb.append(", ").append(i + 1).append(": ")
                    .append(GameText.t(blank.getSideState(i).describe()));
        }
        for (com.tann.dice.gameplay.trigger.personal.Personal personal : blank.getActivePersonals()) {
            if (!personal.hasImage()) {
                continue;
            }
            sb.append(". ").append(GameText.t(personal.describeForTriggerPanel()));
        }
        return sb.toString();
    }
}
