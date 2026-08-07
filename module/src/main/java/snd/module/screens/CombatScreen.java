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
import com.tann.dice.screens.dungeon.panels.tutorial.TutorialManager;

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
        if (phase instanceof PlayerRollingPhase || phase instanceof TargetingPhase) {
            buildAbilitiesStop(b, ds);
        }
        if (phase instanceof PlayerRollingPhase) {
            buildButtons(b, ds, ds.doneRollingButton, true);
        } else if (phase instanceof TargetingPhase) {
            buildButtons(b, ds, ds.confirmButton, false);
        }
        TutorialNodes.build(b, ds);
    }

    // ---- the ability bar: one node per spell/tactic card, in the bar's own
    // QWERTY slot order, plus the mana store ----

    private void buildAbilitiesStop(GraphBuilder b, final DungeonScreen ds) {
        com.tann.dice.gameplay.fightLog.Snapshot present =
                ds.getFightLog().getSnapshot(FightLog.Temporality.Present);
        java.util.List<com.tann.dice.gameplay.effect.targetable.ability.Ability> abilities =
                new java.util.ArrayList<com.tann.dice.gameplay.effect.targetable.ability.Ability>();
        for (int i = 0; i < 8; i++) {
            com.tann.dice.gameplay.effect.targetable.ability.Ability a = ds.abilityHolder.getByIndex(i);
            if (a != null) {
                abilities.add(a);
            }
        }
        boolean hasMana = present.getMaxMana() > 0 || present.getTotalMana() > 0;
        if (abilities.isEmpty() && !hasMana) {
            return;
        }

        b.beginStop("abilities").pushContext(
                GameText.t("Abilities"), Loc.get("ui", "role.list"));
        for (com.tann.dice.gameplay.effect.targetable.ability.Ability a : abilities) {
            b.addItem(ControlId.referenced(a, CompositeKey.of("ability", a.getTitle())),
                    abilityNode(ds, a));
        }
        if (hasMana) {
            NodeVtable mana = new NodeVtable();
            mana.controlType = ControlTypes.TEXT;
            mana.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    // The pip strip's right-click banner, the game's own words.
                    com.tann.dice.gameplay.fightLog.Snapshot now =
                            ds.getFightLog().getSnapshot(FightLog.Temporality.Present);
                    return GameText.t(now.getTotalMana() + "/" + now.getMaxMana() + " mana stored");
                }
            }, AnnouncementKinds.LABEL));
            b.addItem(ControlId.structural(CompositeKey.of("abilities", "mana")), mana);
        }
        b.popContext();
    }

    private NodeVtable abilityNode(final DungeonScreen ds,
            final com.tann.dice.gameplay.effect.targetable.ability.Ability a) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return GameText.t(a.getTitle());
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return costText(ds, a);
                    }
                }, AnnouncementKinds.VALUE),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return ds.targetingManager.getSelectedTargetable() == a
                                ? Loc.get("ui", "state.selected") : null;
                    }
                }, AnnouncementKinds.SELECTED),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        // The card's skull overlay.
                        com.tann.dice.gameplay.content.ent.Ent source = a.getSource();
                        if (source == null) {
                            return null;
                        }
                        EntState state = ds.getFightLog().getState(FightLog.Temporality.Present, source);
                        return state != null && state.isDead()
                                ? Loc.get("combat", "caster_defeated") : null;
                    }
                }, AnnouncementKinds.ENABLED));
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                // The QWERTY-key path; cast errors surface as the game's banners.
                ds.popAllMedium();
                ds.abilityHolder.selectForCast(a);
            }
        };
        vt.onSecondary = new Runnable() {
            @Override
            public void run() {
                host.speech().speak(GameText.t(a.getDerivedEffects().describe(false)), false);
            }
        };
        vt.onTooltip = new Runnable() {
            @Override
            public void run() {
                String rules;
                try {
                    rules = keywordRules(a.getDerivedEffects().getKeywords());
                } catch (Throwable t) {
                    snd.core.SndLog.error("ability keyword rules failed", t);
                    rules = null;
                }
                host.speech().speak(rules, false);
            }
        };
        return vt;
    }

    // Mana for spells, required die faces for tactics — the cost strip.
    private static String costText(DungeonScreen ds,
            com.tann.dice.gameplay.effect.targetable.ability.Ability a) {
        if (a instanceof com.tann.dice.gameplay.effect.targetable.ability.tactic.Tactic) {
            return GameText.t(((com.tann.dice.gameplay.effect.targetable.ability.tactic.Tactic) a).describeCost());
        }
        if (a instanceof com.tann.dice.gameplay.effect.targetable.ability.spell.Spell) {
            int cost = ds.getFightLog().getSnapshot(FightLog.Temporality.Present)
                    .getSpellCost((com.tann.dice.gameplay.effect.targetable.ability.spell.Spell) a);
            return cost + " " + GameText.t(com.tann.dice.util.lang.Words.manaString());
        }
        return null;
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
        if (!heroes) {
            buildReinforcements(b, ds);
        }
        b.popContext();
    }

    // The "Reinforcements: N" box atop the enemy column: the count as the
    // game's own pattern string, the waiting monsters' names on Backspace.
    private void buildReinforcements(GraphBuilder b, final DungeonScreen ds) {
        final List<com.tann.dice.gameplay.content.ent.Monster> waiting =
                ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getReinforcements();
        if (waiting == null || waiting.isEmpty()) {
            return;
        }
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TEXT;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return GameText.t("Reinforcements: " + waiting.size());
            }
        }, AnnouncementKinds.LABEL));
        vt.onSecondary = new Runnable() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                for (com.tann.dice.gameplay.content.ent.Monster m : waiting) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(GameText.t(m.getName(true)));
                }
                host.speech().speak(sb.toString(), false);
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("enemies", "reinforcements")), vt);
    }

    private NodeVtable entNode(final DungeonScreen ds, final Ent ent) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return GameUi.entName(ent);
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
                        return ent.isPlayer() ? null : targetsText(ds, ent);
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
        // The right-click route: pushes the game's character sheet (the
        // EntPanelInventory, a light modal) — SheetScreen reads it as
        // navigable nodes. The game refuses the panel for a dead ent, so the
        // sheet's skull rule is spoken directly instead of failing silently.
        vt.onSecondary = new Runnable() {
            @Override
            public void run() {
                // The tutorial hooks EntPanelCombat's info listener fires on
                // this same gesture.
                TutorialManager tm = ds.getTutorialManager();
                if (ent.isPlayer()) {
                    tm.onAction(TutorialManager.TutorialAction.DieInfo);
                }
                tm.onAction(ent.isPlayer() ? TutorialManager.TutorialAction.HeroPanelInfo
                        : TutorialManager.TutorialAction.MonsterPanelInfo, ent);
                EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
                if (present != null && present.isDead()) {
                    StringBuilder sb = new StringBuilder(GameUi.entName(ent));
                    sb.append(", ").append(Loc.get("combat", "defeated"));
                    if (ent.isPlayer()) {
                        // The sheet's skull-tag rule, the game's own sentence.
                        sb.append(". ").append(GameText.t("Heroes defeated last fight return with half hp"));
                    }
                    host.speech().speak(sb.toString(), false);
                    return;
                }
                ds.targetingManager.clicked(ent, false);
            }
        };
        // The current side's keyword rules — the game shows these only in
        // the almanac glossary or the right-click explanation panel.
        vt.onTooltip = new Runnable() {
            @Override
            public void run() {
                host.speech().speak(sideKeywordRules(ent), false);
            }
        };
        return vt;
    }

    // "bloodlust: +1 pip for each damaged enemy" for every keyword on the
    // rolled side's calculated effect. Null when there is nothing to say —
    // speak(null) is a no-op, so a keywordless side stays silent.
    static String sideKeywordRules(Ent ent) {
        try {
            EntSide side = ent.getDie().getCurrentSide();
            if (side == null) {
                return null;
            }
            return keywordRules(side.findState(FightLog.Temporality.Present, ent)
                    .getCalculatedEffect().getKeywords());
        } catch (Throwable t) {
            snd.core.SndLog.error("side keyword rules failed", t);
            return null;
        }
    }

    static String keywordRules(java.util.List<com.tann.dice.gameplay.effect.eff.keyword.Keyword> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (com.tann.dice.gameplay.effect.eff.keyword.Keyword keyword : keywords) {
            if (sb.length() > 0) {
                sb.append(". ");
            }
            sb.append(GameText.t(keyword.getColourTaggedString()))
                    .append(": ").append(GameText.t(keyword.getRules()));
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
    static String healthText(DungeonScreen ds, Ent ent) {
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
            String from = ent.isPlayer() ? attackerNames(ds, ent) : null;
            sb.append(from == null ? Loc.get("combat", "incoming_damage", "n", incoming)
                    : Loc.get("combat", "incoming_damage_from", "n", incoming, "names", from));
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

    // "targets Thief, Fighter" — the monster's locked-in targets, from the
    // same command walk the game's hover arrows and the hero-coloured
    // stripes on the monster panel draw from (redirects resolved). Cross-side
    // only: self and ally effects already read on the side text.
    private static String targetsText(DungeonScreen ds, Ent ent) {
        List<Ent> targets = new java.util.ArrayList<Ent>(
                ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getAllTargeters(ent, false));
        StringBuilder sb = new StringBuilder();
        for (Ent target : targets) {
            if (target.isPlayer() != ent.isPlayer()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(GameUi.entName(target));
            }
        }
        return sb.length() > 0 ? Loc.get("combat", "targets", "names", sb.toString()) : null;
    }

    // The enemies whose commands aim at this hero — the hover arrows' other
    // direction.
    private static String attackerNames(DungeonScreen ds, Ent ent) {
        List<Ent> attackers = new java.util.ArrayList<Ent>(
                ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getAllTargeters(ent, true));
        StringBuilder sb = new StringBuilder();
        for (Ent attacker : attackers) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(GameUi.entName(attacker));
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

        if (!rolling) {
            // Undo (the Z key): reverts commands, and rewinds to the rolling
            // phase when none are left and rerolls remain.
            NodeVtable undo = new NodeVtable();
            undo.controlType = ControlTypes.BUTTON;
            undo.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t("Undo");
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            // "(n rolls)" — shown when undoing rewinds to rolling.
                            Phase p = PhaseManager.get().getPhase();
                            if (!(p instanceof TargetingPhase) || ds.getFightLog().canUndo()) {
                                return null;
                            }
                            int rolls = ((TargetingPhase) p).getUnusedRolls();
                            if (rolls <= 0) {
                                return null;
                            }
                            return GameText.t(rolls + " " + com.tann.dice.util.lang.Words.plural("roll", rolls));
                        }
                    }, AnnouncementKinds.VALUE));
            undo.onActivate = new Runnable() {
                @Override
                public void run() {
                    ds.requestUndo();
                }
            };
            b.addItem(ControlId.structural(CompositeKey.of("buttons", "undo")), undo);
        }

        NodeVtable confirm = new NodeVtable();
        confirm.controlType = ControlTypes.BUTTON;
        confirm.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        String label = GameUi.confirmLabel(confirmButton);
                        return label != null ? label : Loc.get("combat", "confirm");
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        // The skull icons + red border, as words.
                        int deaths = predictedDeaths(ds);
                        if (deaths <= 0) {
                            return null;
                        }
                        return deaths == 1 ? Loc.get("combat", "predicted_death_one")
                                : Loc.get("combat", "predicted_deaths", "n", deaths);
                    }
                }, AnnouncementKinds.VALUE));
        confirm.onActivate = new Runnable() {
            @Override
            public void run() {
                ds.confirmClicked(true); // the Space path: confirms the phase
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("buttons", "confirm")), confirm);

        buildSystemButtons(b, ds);
    }

    private static int predictedDeaths(DungeonScreen ds) {
        int deaths = 0;
        for (Ent hero : ds.getFightLog().getSnapshot(FightLog.Temporality.Present)
                .getEntities(true, false)) {
            EntState future = ds.getFightLog().getState(FightLog.Temporality.Future, hero);
            if (future != null && future.isDead()) {
                deaths++;
            }
        }
        return deaths;
    }

    // ---- the top icon row (all icon-only and rebuilt every turn, so these
    // drive the underlying actions, never the actors) ----

    private void buildSystemButtons(GraphBuilder b, final DungeonScreen ds) {
        b.addItem(ControlId.structural(CompositeKey.of("sys", "menu")),
                simpleButton(Loc.get("ui", "sys.menu"), new Runnable() {
                    @Override
                    public void run() {
                        com.tann.dice.screens.dungeon.DungeonUtils.showCogMenu();
                    }
                }));
        b.addItem(ControlId.structural(CompositeKey.of("sys", "summary")),
                simpleButton(Loc.get("combat", "run_summary"), new Runnable() {
                    @Override
                    public void run() {
                        // The # button's panel — a blocker push the modal reader covers.
                        ds.getFightLog().getContext().showHashContents();
                    }
                }));
        final java.util.List<com.tann.dice.screens.dungeon.panels.hourglass.HourglassElement> schedule =
                com.tann.dice.screens.dungeon.panels.hourglass.HourglassUtils.getHourglassItems(
                        ds.getFightLog().getSnapshot(FightLog.Temporality.Present));
        if (!schedule.isEmpty()) {
            b.addItem(ControlId.structural(CompositeKey.of("sys", "hourglass")),
                    simpleButton(Loc.get("combat", "future_schedule"), new Runnable() {
                        @Override
                        public void run() {
                            host.speech().speak(scheduleText(ds, schedule), false);
                        }
                    }));
        }
        if (com.tann.dice.gameplay.save.settings.option.OptionLib.SEARCH_BUTT.c()) {
            b.addItem(ControlId.structural(CompositeKey.of("sys", "search")),
                    simpleButton(Loc.get("ui", "sys.search"), new Runnable() {
                        @Override
                        public void run() {
                            com.tann.dice.screens.dungeon.panels.book.page.stuffPage.APIUtils.showSearch();
                        }
                    }));
        }
        // The optional HUD timer/clock text, otherwise unreachable.
        if (com.tann.dice.gameplay.save.settings.option.OptionLib.SHOW_TIMER.c()
                || com.tann.dice.gameplay.save.settings.option.OptionLib.SHOW_CLOCK.c()) {
            b.addLabel(ControlId.structural(CompositeKey.of("sys", "time")),
                    new java.util.function.Supplier<String>() {
                        @Override
                        public String get() {
                            StringBuilder sb = new StringBuilder();
                            if (com.tann.dice.gameplay.save.settings.option.OptionLib.SHOW_TIMER.c()) {
                                sb.append(Loc.get("combat", "timer", "time",
                                        com.tann.dice.util.Tann.parseSeconds(
                                                ds.getDungeonContext().getTimeTakenSeconds(), false)));
                            }
                            if (com.tann.dice.gameplay.save.settings.option.OptionLib.SHOW_CLOCK.c()) {
                                if (sb.length() > 0) {
                                    sb.append(", ");
                                }
                                sb.append(Loc.get("combat", "clock", "time",
                                        new java.text.SimpleDateFormat("HH:mm")
                                                .format(new java.util.Date())));
                            }
                            return sb.toString();
                        }
                    });
        }
    }

    // "Turn 4: message; Turn 6: message" — the hourglass panel's schedule,
    // the only surfacing of delayed effects.
    private static String scheduleText(DungeonScreen ds,
            java.util.List<com.tann.dice.screens.dungeon.panels.hourglass.HourglassElement> schedule) {
        int turn = ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getTurn();
        StringBuilder sb = new StringBuilder();
        for (com.tann.dice.screens.dungeon.panels.hourglass.HourglassElement e : schedule) {
            for (int t : e.getTurns(turn)) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(GameText.t("Turn " + t)).append(": ").append(GameText.t(e.getRealMessage()));
            }
        }
        return sb.length() > 0 ? sb.toString() : GameText.t("Turn " + turn);
    }

    private static NodeVtable simpleButton(final String label, Runnable action) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return label;
            }
        }, AnnouncementKinds.LABEL));
        vt.onActivate = action;
        return vt;
    }

    /** "2/3" — the counter the visual UI draws on the Reroll button. */
    public static String rollCounter(DungeonScreen ds) {
        return ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getRolls()
                + "/" + ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getMaxRolls();
    }
}
