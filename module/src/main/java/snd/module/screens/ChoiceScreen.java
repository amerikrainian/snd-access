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
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.ChoosableType;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.special.LevelupHeroChoosable;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.special.OrChoosable;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.special.ReplaceChoosable;
import com.tann.dice.screens.dungeon.DungeonScreen;
import com.tann.dice.util.lang.Words;

import snd.contracts.HostServices;
import snd.contracts.SndLog;
import snd.core.loc.Loc;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.nav.AccessScreen;
import snd.core.nav.KeyOffer;
import snd.module.Captured;
import snd.module.ChoicePhases;
import snd.module.GameKeys;
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
        if (com.tann.dice.Main.getCurrentScreen() == null) {
            return null;
        }
        com.tann.dice.gameplay.phase.Phase p = PhaseManager.get().getPhase();
        return p instanceof ChoicePhase ? (ChoicePhase) p : null;
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

    // ChoicePhase.keyPress: a digit toggles that option; I opens the
    // inventory where the phase shows its corner button (Phase.keyPress).
    @Override
    public List<KeyOffer> keys() {
        List<KeyOffer> keys = new java.util.ArrayList<KeyOffer>();
        ChoicePhase p = phase();
        if (p == null) {
            return keys;
        }
        int options = p.getOptions().size();
        if (options > 0) {
            keys.add(GameKeys.range("digits", GameKeys.digits(options), Loc.get("ui", "help.choose_option")));
        }
        if (p.showCornerInventory()) {
            keys.add(GameKeys.key("inventory", "I", GameText.t("Inventory"), GameKeys.I));
        }
        keys.add(GameKeys.escape());
        return keys;
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
        boolean sheets = false;
        for (Choosable option : options) {
            sheets |= option instanceof LevelupHeroChoosable || !recruits(option).isEmpty();
        }

        for (int i = 0; i < options.size(); i++) {
            final Choosable option = options.get(i);
            final int index = i;
            // A level-up offer is a whole character sheet in the game (the
            // EntPanelInventory panel); it reads as a row — the choose button
            // followed by the sheet's nodes — instead of one tooltip burst.
            final boolean levelup = option instanceof LevelupHeroChoosable;
            // So is a blessing that adds a hero (GlobalAddHero, whose big
            // panel is the hero's EntPanelInventory): the choose button, then
            // the hero as its panel reads.
            List<com.tann.dice.gameplay.trigger.global.GlobalAddHero> recruits = recruits(option);
            final boolean sheetRow = levelup || !recruits.isEmpty();
            if (sheetRow) {
                // Up/Down keep the column across offers; the row's container
                // names the offer focus crossed into.
                b.pushContext(CompositeKey.of("choice-row", i, option.getSaveString()), new Supplier<String>() {
                    @Override
                    public String get() {
                        return nameOf(option);
                    }
                });
                b.startRow(levelup ? "levelup" : "recruit");
            }
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
                            return levelup ? null : effectOf(option, index);
                        }
                    }, AnnouncementKinds.TOOLTIP));
            // The right-click route: the option's big panel — description
            // plus a rules box per referenced keyword — pushed the way
            // ConcisePanel's info listener pushes its copy. It is a
            // blocker-backed modal, so the generic modal reader takes over
            // and Escape pops it. Level-ups keep their sheet rows instead.
            if (!levelup) {
                // The panel's spell card and keyword boxes, a line each, and
                // the glossary entries its description uses.
                vt.details = new Supplier<List<String>>() {
                    @Override
                    public List<String> get() {
                        List<String> lines = new java.util.ArrayList<String>(termsOf(option));
                        lines.addAll(Terms.glossary(Arrays.asList(effectOf(option, index))));
                        return lines;
                    }
                };
                vt.onSecondary = new Runnable() {
                    @Override
                    public void run() {
                        com.badlogic.gdx.scenes.scene2d.Actor big =
                                option.makeChoosableActor(true, index);
                        com.tann.dice.statics.sound.Sounds.playSound(
                                com.tann.dice.statics.sound.Sounds.pip);
                        com.tann.dice.Main.getCurrentScreen().push(big, 0.7F);
                        com.tann.dice.util.Tann.center(big);
                    }
                };
            }
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
            // Beside level-up sheets, each a row counting its own nodes, the
            // plain options ("a random tier 2 levelup", "skip") would count
            // only each other.
            vt.speaksOwnPosition = sheets && !levelup;
            b.addItem(ControlId.referenced(option, CompositeKey.of("choice", i, option.getSaveString())), vt);
            if (levelup) {
                buildLevelupSheet(b, (LevelupHeroChoosable) option, index);
            } else if (!recruits.isEmpty()) {
                for (com.tann.dice.gameplay.trigger.global.GlobalAddHero recruit : recruits) {
                    EntPanelNodes.unit(b, recruitHero(recruit), java.util.Collections.<String>emptyList(),
                            java.util.Collections.<String>emptyList());
                }
            }
            if (sheetRow) {
                b.endRow();
                b.popContext();
            }
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
        ColumnNodes.build(b, host);
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

    private boolean rerollMissReported;

    // The tiny unlabeled icon button top-right of first-fight offers: reroll
    // the starting party and options. The game adds it to the offer group
    // under exactly these conditions (ChoicePhase.addRerollButtonMaybe), and
    // greys it when no reroll is left; activation fires its own listener,
    // warning dialogs and all.
    private void buildAnticheeseReroll(GraphBuilder b, ChoicePhase p, int optionCount) {
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null || optionCount == 2) {
            return;
        }
        com.tann.dice.gameplay.context.DungeonContext dc = ds.getDungeonContext();
        if (!dc.isFirstLevel() || !dc.getContextConfig().usesAnticheese()) {
            return;
        }
        Group group = ChoicePhases.choiceGroup(p);
        if (group == null) {
            return;
        }
        final Actor button = rerollButton(group);
        if (button == null) {
            // Built every frame, so said once per miss.
            if (!rerollMissReported) {
                rerollMissReported = true;
                SndLog.error("first-fight reroll button not found in the offer", null);
            }
            return;
        }
        rerollMissReported = false;
        final com.tann.dice.gameplay.context.config.ContextConfig cc = dc.getContextConfig();
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return Loc.get("ui", "choice.reroll_start");
                    }
                }, AnnouncementKinds.LABEL),
                // What the game says on pressing it: why it is greyed, or
                // that this reroll costs a loss.
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        com.tann.dice.gameplay.save.antiCheese.AnticheeseData acd = cc.getAnticheese();
                        if (acd != null && !acd.canReroll()) {
                            return GameText.t("Reach fight 3 on this mode or fight 10 on some other modes to reroll again.");
                        }
                        return acd != null && acd.rerollCountsAsLoss()
                                ? GameText.t("This will count as a loss because you have already rerolled once without playing")
                                : null;
                    }
                }, AnnouncementKinds.TOOLTIP));
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                GameUi.activate(button);
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("choice", "anticheese")), vt);
    }

    private static Actor rerollButton(Group group) {
        for (Actor child : group.getChildren()) {
            if (Captured.listenerBuiltBy(child, ChoicePhase.class, "addRerollButtonMaybe") != null) {
                return child;
            }
        }
        return null;
    }

    // The heroes a modifier adds to the party, one per GlobalAddHero.
    private static List<com.tann.dice.gameplay.trigger.global.GlobalAddHero> recruits(Choosable option) {
        List<com.tann.dice.gameplay.trigger.global.GlobalAddHero> recruits =
                new java.util.ArrayList<com.tann.dice.gameplay.trigger.global.GlobalAddHero>();
        if (option instanceof com.tann.dice.gameplay.modifier.Modifier) {
            for (com.tann.dice.gameplay.trigger.global.Global g
                    : ((com.tann.dice.gameplay.modifier.Modifier) option).getGlobals()) {
                if (g instanceof com.tann.dice.gameplay.trigger.global.GlobalAddHero) {
                    recruits.add((com.tann.dice.gameplay.trigger.global.GlobalAddHero) g);
                }
            }
        }
        return recruits;
    }

    // The panel shows a fresh unit of the class (GlobalAddHero.makePanelActorI):
    // one per offer, so the sheet's lines keep their identity.
    private final java.util.Map<Object, com.tann.dice.gameplay.content.ent.Ent> recruitHeroes =
            new java.util.WeakHashMap<Object, com.tann.dice.gameplay.content.ent.Ent>();

    private com.tann.dice.gameplay.content.ent.Ent recruitHero(com.tann.dice.gameplay.trigger.global.GlobalAddHero recruit) {
        com.tann.dice.gameplay.content.ent.Ent hero = recruitHeroes.get(recruit);
        if (hero == null) {
            hero = ((HeroType) Captured.field(recruit, com.tann.dice.gameplay.trigger.global.GlobalAddHero.class, "type"))
                    .makeEnt();
            recruitHeroes.put(recruit, hero);
        }
        return hero;
    }

    // ---- what each option IS: name, short value, full effect ----

    // An option's real identity: modifiers, items, and the special choosables
    // all self-describe through Choosable.getName (Or/And compose their
    // children, level-ups name the upgraded class).
    static String nameOf(Choosable option) {
        try {
            // An enum choosable's name is the word "enum" and a replacement's a
            // lookup key ("Replace X?name"): what either one is, and what its
            // panel draws, is its description.
            ChoosableType kind = option.getType();
            if (kind == ChoosableType.Enu || kind == ChoosableType.Replace) {
                String described = safeDescribe(option);
                if (described != null) {
                    return described;
                }
            }
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
    static String valueOf(Choosable option, int index) {
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
        String type = typeWord(option);
        if (type != null) {
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

    // The kinds whose describe() is a type word; every other kind describes
    // itself whole, which is its name already. The game translates "hero",
    // "curse" and "blessing" on their own; "item", "level-up" and "modifier"
    // it only ever uses inside a sentence ("Choose an item"), so those words
    // are ours.
    private static String typeWord(Choosable option) {
        switch (option.getType()) {
            case Item:
                return Loc.get("ui", "choice.kind.item");
            case Levelup:
                return Loc.get("ui", "choice.kind.levelup");
            case Hero:
                return safeDescribe(option);
            case Modifier:
                return option.getTier() == 0 ? Loc.get("ui", "choice.kind.modifier") : safeDescribe(option);
            default:
                return null;
        }
    }

    // The full effect text — the same content the visual panels render.
    static String effectOf(Choosable option, int index) {
        try {
            if (option instanceof com.tann.dice.gameplay.modifier.Modifier) {
                return GameText.t(((com.tann.dice.gameplay.modifier.Modifier) option).getFullDescription());
            }
            if (option instanceof com.tann.dice.gameplay.content.item.Item) {
                return GameText.itemDescription((com.tann.dice.gameplay.content.item.Item) option);
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

    // What the option's panel draws beside its description (ModifierPanel,
    // ItemPanel): the card of a spell it teaches, then a rules box per
    // keyword, each once; a replacement's are those of what it gains, an
    // or-choice's those of every alternative.
    static java.util.Collection<String> termsOf(Choosable option) {
        java.util.Set<String> lines = new java.util.LinkedHashSet<String>();
        if (option instanceof com.tann.dice.gameplay.modifier.Modifier) {
            lines.addAll(UnitLines.taughtAbilities((com.tann.dice.gameplay.modifier.Modifier) option));
            lines.addAll(Terms.forModifier((com.tann.dice.gameplay.modifier.Modifier) option));
        } else if (option instanceof com.tann.dice.gameplay.content.item.Item) {
            lines.addAll(UnitLines.taughtAbilities((com.tann.dice.gameplay.content.item.Item) option));
            lines.addAll(Terms.forItem((com.tann.dice.gameplay.content.item.Item) option));
        } else if (option instanceof ReplaceChoosable) {
            lines.addAll(termsOf(((ReplaceChoosable) option).gain));
        } else if (option instanceof OrChoosable) {
            for (Choosable child : ((OrChoosable) option).getAll()) {
                lines.addAll(termsOf(child));
            }
        }
        return lines;
    }

    private static String safeDescribe(Choosable option) {
        try {
            return GameText.t(option.describe());
        } catch (Throwable t) {
            SndLog.error("Choosable.describe failed", t);
            return null;
        }
    }

    static Hero targetHero(LevelupHeroChoosable option, int index) {
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null) {
            return null;
        }
        return ds.getDungeonContext().getParty().getHeroFor(option.getHeroType(), index);
    }

    // The offer's sheet as sibling nodes in the option's row — the same
    // per-side treatment as the combat sheet (SheetScreen): level and hp, the
    // six sides with their keyword rules as details, items, passives.
    private void buildLevelupSheet(GraphBuilder b, final LevelupHeroChoosable option, final int index) {
        final Hero upgraded = upgradedHero(option, index);
        if (upgraded == null) {
            return;
        }
        final EntState blank = upgraded.getBlankState();

        NodeVtable header = new NodeVtable();
        header.controlType = ControlTypes.TEXT;
        header.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("combat", "level", "n", upgraded.getLevel())
                        + ", " + blank.getMaxHp() + " " + GameText.t("hp");
            }
        }, AnnouncementKinds.LABEL));
        // Structural ids only: the option's reference belongs to its choose
        // node alone — a shared reference would pull tier-1 focus
        // reconciliation to an arbitrary sibling on every rebuild.
        b.addItem(ControlId.structural(CompositeKey.of("choice", index, "lvl")), header);

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
            b.addItem(ControlId.structural(CompositeKey.of("choice", index, "side", side)), vt);
        }

        List<com.tann.dice.gameplay.content.item.Item> items = upgraded.getItems();
        if (items != null) {
            for (int n = 0; n < items.size(); n++) {
                final com.tann.dice.gameplay.content.item.Item item = items.get(n);
                NodeVtable vt = new NodeVtable();
                vt.controlType = ControlTypes.TEXT;
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
                                String desc = GameText.itemDescription(item);
                                return desc == null || desc.trim().isEmpty()
                                        ? null : desc;
                            }
                        }, AnnouncementKinds.VALUE));
                b.addItem(ControlId.structural(CompositeKey.of("choice", index, "item", n)), vt);
            }
        }

        int n = 0;
        for (com.tann.dice.gameplay.trigger.personal.Personal p : blank.getActivePersonals()) {
            if (!p.hasImage()) {
                continue; // invisible mechanics don't show on the panel either
            }
            final com.tann.dice.gameplay.trigger.personal.Personal personal = p;
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.TEXT;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return SpecialPips.describe(personal);
                }
            }, AnnouncementKinds.LABEL));
            b.addItem(ControlId.structural(CompositeKey.of("choice", index, "passive", n)), vt);
            n++;
        }
        // The traits drawn beside the net, a caster's spell among them.
        int t = 0;
        for (final com.tann.dice.gameplay.effect.Trait trait : UnitLines.netTraits(upgraded, blank)) {
            if (trait.personal.hasImage()) {
                continue; // a passive row above already reads it
            }
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.TEXT;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return UnitLines.netTraitLine(trait);
                }
            }, AnnouncementKinds.LABEL));
            vt.details = new Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return UnitLines.netTraitRules(trait);
                }
            };
            b.addItem(ControlId.structural(CompositeKey.of("choice", index, "trait", t)), vt);
            t++;
        }
    }

    // The hypothetical upgraded hero, built the way makeChoosableActor builds
    // the offer's panel.
    static Hero upgradedHero(LevelupHeroChoosable option, int index) {
        try {
            DungeonScreen ds = DungeonScreen.get();
            if (ds == null) {
                return null;
            }
            HeroType ht = option.getHeroType();
            Hero target = targetHero(option, index);
            Hero upgraded = (target != null ? target.transformLevelup(ht) : ht).makeEnt();
            upgraded.setRealFightLog(ds.getFightLog());
            return upgraded;
        } catch (Throwable t) {
            SndLog.error("failed to build the upgraded-hero preview", t);
            return null;
        }
    }

    // The upgraded hero's full character sheet as one burst — for level-ups
    // nested inside composite (or-style) options, which stay single nodes.
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
        for (int i : SideText.readingOrder()) {
            sb.append(", ").append(SideText.at(i, SideText.of(blank.getSideState(i))));
        }
        for (com.tann.dice.gameplay.trigger.personal.Personal personal : blank.getActivePersonals()) {
            if (!personal.hasImage()) {
                continue;
            }
            sb.append(". ").append(SpecialPips.describe(personal));
        }
        for (com.tann.dice.gameplay.effect.Trait trait : UnitLines.netTraits(upgraded, blank)) {
            if (!trait.personal.hasImage()) {
                sb.append(". ").append(UnitLines.netTraitLine(trait));
            }
        }
        return sb.toString();
    }
}
