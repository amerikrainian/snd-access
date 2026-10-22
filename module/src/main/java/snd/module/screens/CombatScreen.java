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
import com.tann.dice.gameplay.trigger.personal.Personal;
import com.tann.dice.screens.dungeon.DungeonScreen;
import com.tann.dice.screens.dungeon.TargetingManager;
import com.tann.dice.screens.dungeon.panels.tutorial.TutorialManager;

import snd.contracts.HostServices;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.GraphDir;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.core.nav.AccessScreen;
import snd.core.nav.GraphNavigator;
import snd.core.nav.KeyOffer;
import snd.module.GameKeys;
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
        return inFight();
    }

    // Letters are the game's in a fight (Z undo, R reroll, QWERTY abilities):
    // a search they started would hold the arrows and Escape after them.
    @Override
    public boolean allowsTypeahead() {
        return false;
    }

    /** A fight is on screen: the combat phases of the dungeon screen. */
    public static boolean inFight() {
        if (!(com.tann.dice.Main.getCurrentScreen() instanceof DungeonScreen)) {
            return false;
        }
        Phase p = PhaseManager.get().getPhase();
        return p instanceof EnemyRollingPhase || p instanceof PlayerRollingPhase
                || p instanceof TargetingPhase || p instanceof DamagePhase
                || p instanceof SurrenderPhase;
    }

    // ---- the vitals glance: on a control that concerns a unit (a combat
    // row, a line of its sheet, its inventory slots), the unit's hp display
    // alone ----

    /**
     * The unit the focused control concerns ({@code NodeVtable.subject}),
     * while the dungeon's FightLog holds a state to read for it; null
     * anywhere else.
     */
    public static Ent focusedUnit(GraphNavigator nav) {
        Object subject = nav.focusedSubject();
        if (!(subject instanceof Ent) || !(com.tann.dice.Main.getCurrentScreen() instanceof DungeonScreen)) {
            return null;
        }
        FightLog log = DungeonScreen.get().getFightLog();
        return log != null && log.getState(FightLog.Temporality.Present, (Ent) subject) != null
                ? (Ent) subject : null;
    }

    /**
     * What the monsters are about to do, a line each ("Bandit 1: 5 damage,
     * targets Defender 1"), then every hero that leaves dead ("Defender 1:
     * overkill 2") — what the targeting arrows and the flashing hp bars tell
     * the eye the moment the enemy dice land. Each line is an event of its
     * own. Empty with no monster acting.
     */
    public static List<String> enemyIntents() {
        DungeonScreen ds = DungeonScreen.get();
        com.tann.dice.gameplay.fightLog.Snapshot present = ds.getFightLog().getSnapshot(FightLog.Temporality.Present);
        List<String> lines = new java.util.ArrayList<String>();
        for (Ent monster : present.getEntities(false, false)) {
            EntState state = present.getState(monster);
            if (state.skipTurn() || state.isSummonedSoNotAttacking()) {
                continue; // stunned or just summoned: its die is no threat this turn
            }
            java.util.Map<String, String> parts = new java.util.HashMap<String, String>();
            parts.put(AnnouncementKinds.LABEL, GameUi.entName(monster));
            parts.put(UnitParts.SIDE, currentSideText(monster));
            parts.put(UnitParts.AIM, aimText(ds, monster));
            addLine(lines, EventParts.line(EventParts.INTENT, parts));
        }
        if (lines.isEmpty()) {
            return lines;
        }
        for (Ent hero : present.getEntities(true, false)) {
            EntState future = ds.getFightLog().getState(FightLog.Temporality.Future, hero);
            if (future != null && future.isDead()) {
                java.util.Map<String, String> parts = new java.util.HashMap<String, String>();
                parts.put(AnnouncementKinds.LABEL, GameUi.entName(hero));
                parts.put(UnitParts.FATE, fateText(ds, hero));
                addLine(lines, EventParts.line(EventParts.INTENT, parts));
            }
        }
        return lines;
    }

    private static void addLine(List<String> lines, String line) {
        if (line != null) {
            lines.add(line);
        }
    }

    /**
     * What is coming at the party, a hero at a time in column order: each
     * living hero the damage preview moves ("Defender 1: incoming 6, dies
     * this turn"), joined; "nothing incoming" when it moves none.
     */
    public static String partyIncoming() {
        DungeonScreen ds = DungeonScreen.get();
        StringBuilder sb = new StringBuilder();
        for (Ent hero : ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getEntities(true, false)) {
            java.util.Map<String, String> parts = new java.util.HashMap<String, String>();
            parts.put(UnitParts.INCOMING, incomingText(ds, hero));
            parts.put(UnitParts.FATE, fateText(ds, hero));
            if (parts.get(UnitParts.INCOMING) == null && parts.get(UnitParts.FATE) == null) {
                continue;
            }
            parts.put(AnnouncementKinds.LABEL, GameUi.entName(hero));
            String line = UnitParts.line(UnitParts.GLANCE_INCOMING, parts);
            if (line != null) {
                if (sb.length() > 0) {
                    sb.append(". ");
                }
                sb.append(line);
            }
        }
        return sb.length() > 0 ? sb.toString() : Loc.get("combat", "party_safe");
    }

    /** "Ranger, 9 hp, shielded 2, incoming 6"; "Ranger, defeated" for a corpse: a line of the party and enemies buffers. */
    public static String vitalsLine(Ent ent) {
        java.util.Map<String, String> parts = vitalParts(ent);
        parts.put(AnnouncementKinds.LABEL, GameUi.entName(ent));
        return UnitParts.line(UnitParts.ROSTER, parts);
    }

    /** {@link #vitalsLine} without the name, for the unit focus is already on (Ctrl+1); null when all is silenced. */
    public static String vitalsText(Ent ent) {
        return UnitParts.line(UnitParts.GLANCE_VITALS, vitalParts(ent));
    }

    private static java.util.Map<String, String> vitalParts(Ent ent) {
        DungeonScreen ds = DungeonScreen.get();
        java.util.Map<String, String> parts = new java.util.HashMap<String, String>();
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        if (present != null && present.isDead()) {
            parts.put(UnitParts.FATE, Loc.get("combat", "defeated"));
            return parts;
        }
        parts.put(UnitParts.HP, hpText(ds, ent));
        parts.put(UnitParts.SHIELD, shieldText(ds, ent));
        parts.put(UnitParts.MARK, markText(ds, ent));
        parts.put(UnitParts.INCOMING, incomingText(ds, ent));
        parts.put(UnitParts.FATE, fateText(ds, ent));
        return parts;
    }

    /**
     * "targets Splint"; "targeted by Rat 1, Rat 3" — the hover arrows both
     * ways, a monster's own targets named in full even when they are every
     * hero. Null when no arrow touches the unit, or it is dead.
     */
    public static String aimLine(Ent ent) {
        DungeonScreen ds = DungeonScreen.get();
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        if (present != null && present.isDead()) {
            return null;
        }
        java.util.Map<String, String> parts = new java.util.HashMap<String, String>();
        parts.put(UnitParts.AIM, ent.isPlayer() ? null : targetsText(ds, ent));
        parts.put(UnitParts.TARGETED_BY, targetedByText(ds, ent));
        return UnitParts.line(UnitParts.GLANCE_AIM, parts);
    }

    /** "Back-row": the statuses the unit's row speaks, alone; null with none. */
    public static String statusLine(Ent ent) {
        return statusText(DungeonScreen.get(), ent);
    }

    /**
     * "Turn 2, Reroll 1/2, 2/3 mana stored": the hourglass's turn, the Reroll
     * button's counter while rolling, and the mana pips where the fight has
     * mana — each in the game's own words.
     */
    public static String turnLine() {
        DungeonScreen ds = DungeonScreen.get();
        com.tann.dice.gameplay.fightLog.Snapshot present = ds.getFightLog().getSnapshot(FightLog.Temporality.Present);
        java.util.Map<String, String> parts = new java.util.HashMap<String, String>();
        parts.put(EventParts.TURN, GameText.t("Turn " + present.getTurn()));
        if (PhaseManager.get().getPhase() instanceof PlayerRollingPhase) {
            parts.put(EventParts.REROLLS, GameText.t("Reroll") + ' ' + rollCounter(ds));
        }
        if (present.getMaxMana() > 0 || present.getTotalMana() > 0) {
            parts.put(UnitParts.MANA, GameText.t(present.getTotalMana() + "/" + present.getMaxMana() + " mana stored"));
        }
        return UnitParts.line(UnitParts.GLANCE_TURN, parts);
    }

    @Override
    public void build(GraphBuilder b) {
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null || ds.getFightLog() == null) {
            return;
        }
        buildColumns(b, host, ds, null, null);
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

    // The game's combat hotkeys live in the current phase
    // (PlayerRollingPhase.keyPress, TargetingPhase.keyPress), in the words of
    // the game's own Tips page. Enter is the navigator's here, so the confirm
    // button's key reads as Space.
    @Override
    public List<KeyOffer> keys() {
        List<KeyOffer> keys = new java.util.ArrayList<KeyOffer>();
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null || ds.getFightLog() == null) {
            return keys;
        }
        com.tann.dice.gameplay.fightLog.Snapshot present =
                ds.getFightLog().getSnapshot(FightLog.Temporality.Present);
        Phase phase = PhaseManager.get().getPhase();
        if (phase instanceof PlayerRollingPhase) {
            keys.add(GameKeys.key("roll", "R", GameText.t("roll dice"), GameKeys.R));
            addDigits(keys, "digits", false, present.getEntities(true, false).size(),
                    GameText.t("lock/unlock dice"));
            keys.add(GameKeys.key("confirm", Loc.get("ui", "key.space"),
                    confirmText(ds.doneRollingButton), GameKeys.SPACE));
        } else if (phase instanceof TargetingPhase) {
            Targetable selected = ds.targetingManager.getSelectedTargetable();
            if (selected == null) {
                addDigits(keys, "digits", false, present.getEntities(true, false).size(),
                        GameText.t("select heroes"));
            } else {
                // TargetingPhase.checkForEntPress: the digits reach the side
                // the selected effect aims at, Shift the other one.
                com.tann.dice.gameplay.effect.eff.Eff eff = selected.getDerivedEffects();
                boolean player = eff.getOr(false) == null && eff.isFriendly();
                addDigits(keys, "digits", false, present.getEntities(player, false).size(),
                        GameText.t("target"));
                addDigits(keys, "shift-digits", true, present.getEntities(!player, false).size(),
                        Loc.get("ui", "help.target_other_side"));
            }
            List<Integer> slots = new java.util.ArrayList<Integer>();
            for (int i = 0; i < GameKeys.ABILITY_SLOTS; i++) {
                if (ds.abilityHolder.getByIndex(i) != null) {
                    slots.add(i);
                }
            }
            if (!slots.isEmpty()) {
                keys.add(GameKeys.range("abilities", GameKeys.abilityKeys(slots), GameText.t("select ability")));
            }
            keys.add(GameKeys.key("undo", "Z", GameText.t("undo"), GameKeys.Z));
            keys.add(GameKeys.key("confirm", Loc.get("ui", "key.space"),
                    confirmText(ds.confirmButton), GameKeys.SPACE));
        }
        keys.add(GameKeys.escape());
        return keys;
    }

    private static void addDigits(List<KeyOffer> keys, String id, boolean shift, int count, String label) {
        if (count > 0) {
            keys.add(GameKeys.range(id, shift ? GameKeys.shiftDigits(count) : GameKeys.digits(count), label));
        }
    }

    private static String confirmText(com.tann.dice.screens.dungeon.panels.ConfirmButton button) {
        String label = GameUi.confirmLabel(button);
        return label != null ? label : Loc.get("combat", "confirm");
    }

    // ---- the ability bar: the mana store, then one node per spell/tactic
    // card in the bar's own QWERTY slot order ----

    private void buildAbilitiesStop(GraphBuilder b, final DungeonScreen ds) {
        com.tann.dice.gameplay.fightLog.Snapshot present =
                ds.getFightLog().getSnapshot(FightLog.Temporality.Present);
        java.util.List<com.tann.dice.gameplay.effect.targetable.ability.Ability> abilities =
                new java.util.ArrayList<com.tann.dice.gameplay.effect.targetable.ability.Ability>();
        // Every ability on the bar, keyed or not: getByIndex is null past the last one.
        for (int i = 0; ds.abilityHolder.getByIndex(i) != null; i++) {
            abilities.add(ds.abilityHolder.getByIndex(i));
        }
        boolean hasMana = present.getMaxMana() > 0 || present.getTotalMana() > 0;
        if (abilities.isEmpty() && !hasMana) {
            return;
        }

        // A bar of cards read by name and cost, not a list to count.
        b.beginStop("abilities").pushContext(GameText.t("Abilities"), null, false);
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
        for (com.tann.dice.gameplay.effect.targetable.ability.Ability a : abilities) {
            b.addItem(ControlId.referenced(a, CompositeKey.of("ability", a.getTitle())),
                    abilityNode(ds, a));
        }
        b.popContext();
    }

    private NodeVtable abilityNode(final DungeonScreen ds,
            final com.tann.dice.gameplay.effect.targetable.ability.Ability a) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.readout = UnitParts.ABILITY;
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
                }, UnitParts.COST),
                // What the card draws: the effect with its keywords' names, as a
                // die's face reads; their rules are the buffer's.
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return SideText.of(a.getDerivedEffects());
                    }
                }, UnitParts.EFFECT),
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
                }, UnitParts.CASTER));
        vt.details = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                // A line per keyword the card carries.
                return keywordRuleLines(a.getDerivedEffects());
            }
        };
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
                host.speech().speak(SideText.of(a.getDerivedEffects()), false);
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

    /**
     * An ability's cost where no fight prices it (the almanac): a spell's
     * base mana, a tactic's die faces.
     */
    public static String baseCostText(com.tann.dice.gameplay.effect.targetable.ability.Ability a) {
        if (a instanceof com.tann.dice.gameplay.effect.targetable.ability.tactic.Tactic) {
            return GameText.t(((com.tann.dice.gameplay.effect.targetable.ability.tactic.Tactic) a).describeCost());
        }
        if (a instanceof com.tann.dice.gameplay.effect.targetable.ability.spell.Spell) {
            return ((com.tann.dice.gameplay.effect.targetable.ability.spell.Spell) a).getBaseCost()
                    + " " + GameText.t(com.tann.dice.util.lang.Words.manaString());
        }
        return null;
    }

    /**
     * An ability as its card reads where something teaches it (an item, a
     * hero's trait, a blessing — Explanel(Ability)): title, the card's cost,
     * and the effect with its keywords' names, as the ability bar words it.
     */
    public static String abilityLine(com.tann.dice.gameplay.effect.targetable.ability.Ability a) {
        StringBuilder sb = new StringBuilder(GameText.t(a.getTitle()));
        String cost = baseCostText(a);
        if (cost != null) {
            sb.append(", ").append(cost);
        }
        return sb.append(", ").append(SideText.of(a.getDerivedEffects())).toString();
    }

    // ---- the two combatant columns: one node per hero/monster ----

    /**
     * Both columns as one stop, laid out as drawn: heroes on the left,
     * monsters on the right, Up/Down within a column, Left/Right across to
     * the row level with it (the other column's last row where it is
     * shorter). The columns stay on the dungeon screen between fights, under
     * every offer and dialog ({@link ColumnNodes}); heroFoot, when given, sits
     * below the hero column.
     */
    static void buildColumns(GraphBuilder b, HostServices host, final DungeonScreen ds,
            ControlId heroFoot, NodeVtable heroFootVt) {
        b.beginStop("combatants");
        com.tann.dice.gameplay.fightLog.Snapshot present = ds.getFightLog().getSnapshot(FightLog.Temporality.Present);
        // Dead heroes keep their place (the greyed skull panel); dead
        // monsters vanish, matching the visual column.
        b.pushContext(Loc.get("combat", "heroes"));
        List<ControlId> left = column(b, host, ds, present.getEntities(true, null), "heroes");
        b.popContext();
        if (heroFoot != null) {
            b.addNode(heroFoot, heroFootVt);
            left.add(heroFoot);
        }
        b.pushContext(Loc.get("combat", "enemies"));
        List<ControlId> right = column(b, host, ds, present.getEntities(false, Boolean.FALSE), "enemies");
        ControlId reinforcements = buildReinforcements(b, host, ds);
        if (reinforcements != null) {
            right.add(reinforcements);
        }
        b.popContext();
        chain(b, left);
        chain(b, right);
        across(b, left, right, GraphDir.RIGHT);
        across(b, right, left, GraphDir.LEFT);
    }

    // A column's unit rows, top to bottom; the ids in that order.
    private static List<ControlId> column(GraphBuilder b, HostServices host, DungeonScreen ds,
            List<Ent> ents, String key) {
        List<ControlId> ids = new java.util.ArrayList<ControlId>();
        for (int i = 0; i < ents.size(); i++) {
            Ent ent = ents.get(i);
            ControlId id = ControlId.referenced(ent, CompositeKey.of(key, i));
            b.addNode(id, entNode(host, ds, ent));
            ids.add(id);
        }
        return ids;
    }

    private static void chain(GraphBuilder b, List<ControlId> column) {
        for (int i = 1; i < column.size(); i++) {
            b.connect(column.get(i - 1), GraphDir.DOWN, column.get(i));
            b.connect(column.get(i), GraphDir.UP, column.get(i - 1));
        }
    }

    private static void across(GraphBuilder b, List<ControlId> from, List<ControlId> to, GraphDir dir) {
        if (to.isEmpty()) {
            return;
        }
        for (int i = 0; i < from.size(); i++) {
            b.connect(from.get(i), dir, to.get(Math.min(i, to.size() - 1)));
        }
    }

    // The "Reinforcements: N" box atop the enemy column: the count as the
    // game's own pattern string, the waiting monsters' names on Backspace.
    private static ControlId buildReinforcements(GraphBuilder b, final HostServices host, final DungeonScreen ds) {
        final List<com.tann.dice.gameplay.content.ent.Monster> waiting =
                ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getReinforcements();
        if (waiting == null || waiting.isEmpty()) {
            return null;
        }
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TEXT;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return GameText.t("Reinforcements: " + waiting.size());
            }
        }, AnnouncementKinds.LABEL));
        vt.details = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                List<String> names = new java.util.ArrayList<String>();
                for (com.tann.dice.gameplay.content.ent.Monster m : waiting) {
                    names.add(GameText.t(m.getName(true)));
                }
                return names;
            }
        };
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
        ControlId id = ControlId.structural(CompositeKey.of("enemies", "reinforcements"));
        b.addNode(id, vt);
        return id;
    }

    private static NodeVtable entNode(final HostServices host, final DungeonScreen ds, final Ent ent) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.subject = ent;
        vt.details = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return entTooltipLines(ds, ent);
            }
        };
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
                        // Every combat row is a button; silence the type's
                        // role word rather than spend it on every scroll step.
                        return null;
                    }
                }, AnnouncementKinds.ROLE),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        // For a monster, the locked face IS its intent for the turn.
                        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
                        if (present != null) {
                            if (present.isDead()) {
                                return null;
                            }
                            // A spent face is dead information — the row's
                            // longest part drops on exactly the rows being
                            // skipped. isUsed stays false for a multi-use die
                            // that can still act.
                            if (ent.isPlayer() && present.isUsed()
                                    && PhaseManager.get().getPhase() instanceof TargetingPhase) {
                                return null;
                            }
                            // A monster summoned this turn: the panel blanks its
                            // die box (EntPanelCombat.draw), and the die it holds
                            // is not an intent. A hero's face stays drawn, dimmed
                            // — every hero between fights.
                            if (present.isSummonedSoNotAttacking() && !ent.isPlayer()) {
                                return null;
                            }
                        }
                        return currentSideText(ent);
                    }
                }, UnitParts.SIDE),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return ent.isPlayer() ? null : aimText(ds, ent);
                    }
                }, UnitParts.AIM),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return dieUseState(ds, ent);
                    }
                }, UnitParts.DIE_STATE),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return hpText(ds, ent);
                    }
                }, UnitParts.HP),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return shieldText(ds, ent);
                    }
                }, UnitParts.SHIELD),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return markText(ds, ent);
                    }
                }, UnitParts.MARK),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return statusText(ds, ent);
                    }
                }, UnitParts.STATUSES),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return incomingText(ds, ent);
                    }
                }, UnitParts.INCOMING),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return fateText(ds, ent);
                    }
                }, UnitParts.FATE),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return invalidTargetText(ds, ent);
                    }
                }, UnitParts.INVALID));
        vt.readout = UnitParts.COMBATANT;
        // No auto "n of m": the roster is fixed for the whole fight, and
        // names already number duplicates ("Bandit 2").
        vt.speaksOwnPosition = true;
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                // The digit-key route: toggles the lock while rolling, selects
                // the side or applies the selection while targeting. A vetoed
                // target names its reason (the explanel's red text, spoken).
                if (!ent.isPlayer()) {
                    // The tutorial hook EntPanelCombat's tap listener fires on
                    // this same gesture, before the click resolves.
                    ds.getTutorialManager().onAction(TutorialManager.TutorialAction.SelectMonster);
                }
                Targetable selected = ds.targetingManager.getSelectedTargetable();
                if (selected != null && PhaseManager.get().getPhase().canTarget()) {
                    String reason = ds.targetingManager.getInvalidTargetReason(ent, selected, true);
                    if (reason != null) {
                        host.speech().speak(GameText.t(reason), true);
                        return;
                    }
                }
                ds.targetingManager.clicked(ent, true);
                if (!ent.isPlayer()) {
                    // Act-then-listen for the attack explanel: when this click
                    // opened it, speak its payload — the rolled side, the
                    // lit arrow's target, and the passives stack. The
                    // toggle-close and the applied-attack case stay silent
                    // (the TargetingWatcher reads applied commands).
                    com.badlogic.gdx.scenes.scene2d.Actor top = ds.getTopPushedActor();
                    EntSide side = ent.getDie().getCurrentSide();
                    if (top instanceof com.tann.dice.screens.dungeon.panels.Explanel.Explanel && side != null
                            && ((com.tann.dice.screens.dungeon.panels.Explanel.Explanel) top).isShowing(side)) {
                        host.speech().speak(attackPanelText(ds, ent), true);
                    }
                }
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
        return vt;
    }

    // The row's details, one line per tooltip: a line per keyword of the
    // current side, then each status the panel draws, in full — the row
    // speaks only status names. The game shows both only in the almanac
    // glossary or the right-click explanation panel.
    static List<String> entTooltipLines(DungeonScreen ds, Ent ent) {
        // A definition is given once, however many statuses reference it.
        java.util.Set<String> lines = new java.util.LinkedHashSet<String>(sideKeywordRuleLines(ent));
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        if (present != null) {
            for (Personal p : present.getActivePersonals()) {
                if (!p.showInEntPanel() || p.skipNetAndIcon()) {
                    continue;
                }
                lines.add(SpecialPips.describe(p));
                lines.addAll(Terms.forPersonal(p));
            }
        }
        List<String> all = new java.util.ArrayList<String>(lines);
        all.addAll(Terms.glossary(all));
        return all;
    }

    // "bloodlust: +1 pip for each damaged enemy" for every keyword on the
    // rolled side's calculated effect; nothing for a keywordless side.
    static List<String> sideKeywordRuleLines(Ent ent) {
        try {
            EntSide side = ent.getDie().getCurrentSide();
            if (side != null) {
                return keywordRuleLines(side.findState(FightLog.Temporality.Present, ent).getCalculatedEffect());
            }
        } catch (Throwable t) {
            snd.contracts.SndLog.error("side keyword rules failed", t);
        }
        return java.util.Collections.emptyList();
    }

    // The game's keyword box composition (KUtils.makeActor): the effect's
    // display-filtered keywords, each rules text parameterized by the source
    // effect — halved and treble rewrite their numbers from it.
    /** A die side's row: its keyword rules as the control buffer's details, a line per keyword. */
    static Supplier<List<String>> ruleDetails(final Supplier<com.tann.dice.gameplay.effect.eff.Eff> eff) {
        return new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return keywordRuleLines(eff.get());
            }
        };
    }

    // One line per keyword the effect displays ("ranged: can target enemies
    // in the back row..."), each followed by its longer explanation.
    static List<String> keywordRuleLines(com.tann.dice.gameplay.effect.eff.Eff e) {
        return Terms.forEff(e);
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
            // isUsed = exhausted (the dark overlay); a multi-use die that can
            // still act is not called used.
            return present != null && present.isUsed() ? Loc.get("combat", "used") : null;
        }
        return ent.getDie().getState().isLockedOrLocking() ? GameText.t("locked") : null;
    }

    // The HP pip grid and shield badge, as text: "4 of 8 hp, shielded 2". A
    // corpse has no health readout ("defeated" covers it).
    static String healthText(DungeonScreen ds, Ent ent) {
        return UnitParts.join(hpText(ds, ent), shieldText(ds, ent), markText(ds, ent));
    }

    private static EntState living(DungeonScreen ds, Ent ent) {
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        return present == null || present.isDead() ? null : present;
    }

    // "4 of 8 hp"; the ceiling speaks only when it differs — full hp reads "9 hp".
    static String hpText(DungeonScreen ds, Ent ent) {
        EntState present = living(ds, ent);
        if (present == null) {
            return null;
        }
        return present.getHp() >= present.getMaxHp()
                ? Loc.get("combat", "hp_full", "hp", present.getHp())
                : Loc.get("combat", "hp", "hp", present.getHp(), "max", present.getMaxHp());
    }

    // "shielded 3", not "Shield 3" — a rolled Shield side in the same row
    // would read as the same words twice.
    static String shieldText(DungeonScreen ds, Ent ent) {
        EntState present = living(ds, ent);
        return present != null && present.getShields() > 0
                ? Loc.get("combat", "shielded", "n", present.getShields()) : null;
    }

    // The hp bar draws its marked pips; the next one down is where it is.
    static String markText(DungeonScreen ds, Ent ent) {
        EntState present = living(ds, ent);
        int pip = present != null ? SpecialPips.next(present) : -1;
        return pip >= 0 ? Loc.get("combat", "pip_at", "hp", pip) : null;
    }

    // The TriggerPanel's icon strip, as text: every ACTIVE status/trait the
    // panel draws, by name — same visibility filter as TriggerPanel.draw, so
    // a mechanic that shows an icon speaks, whatever added it. Full rules
    // read on the tooltip key; statuses that are only incoming join the
    // "incoming" enumeration in previewText instead.
    private static String statusText(DungeonScreen ds, Ent ent) {
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        if (present == null || present.isDead()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean backRowSaid = false;
        for (Personal p : present.getActivePersonals()) {
            if (!p.showInEntPanel() || p.skipNetAndIcon()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(GameText.t(statusName(p)));
            backRowSaid |= p.backRow();
        }
        // Standing in the back row draws an icon only for those who START
        // there; one who retreats mid-fight just slides away from the front
        // (BackRow(false) hides its icon). Out of reach either way, so it is
        // said either way, in the game's word for it.
        if (!present.isForwards() && !backRowSaid) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(GameText.t("Back-row"));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * The status's name line — its own description up to the first [n]
     * break, which is where multi-line statuses put their rules text
     * ("Weakened 1[n]All sides reduced by 1") — with the buff's turns rider
     * kept ("Weakened 1 for 2 turns"). Single-line statuses have no separate
     * name and read whole. Rows and outcomes speak this; the tooltip key
     * reads the full description.
     */
    public static String statusName(Personal p) {
        return statusName(p, true);
    }

    /** {@link #statusName(Personal)}, with or without the buff's turns rider ("this turn", "for 2 turns"). */
    public static String statusName(Personal p, boolean turns) {
        String desc;
        try {
            desc = p.describeForSelfBuff();
        } catch (Throwable t) {
            // describeForTriggerPanel carries the game's own fallback.
            return p.describeForTriggerPanel();
        }
        if (desc == null) {
            return null;
        }
        int cut = desc.indexOf("[n]");
        if (cut >= 0) {
            desc = desc.substring(0, cut);
        }
        // Markup can hide a trailing space from the reader ("Poisoned 1 ,").
        return (turns && p.buff != null ? desc + p.buff.getTurnsString() : desc).trim();
    }

    // The damage preview, computed the way the game's own hp grid computes
    // its pip colours (HPHolder.setupStates): every part is a Present→Future
    // diff of an EntState counter — yellow pips = blockable damage, green =
    // poison. The net-hp remainder then catches every source the named
    // counters miss (unblockable triggers, incoming heals, modded
    // mechanics): a mechanic must move future hp to matter, so nothing
    // incoming can stay silent. All parts enumerate under one "incoming"
    // prefix, the blockable damage a bare number: "incoming 6, 2 poison";
    // then what it leads to, "dies" or "overkill 2".
    static String previewText(DungeonScreen ds, Ent ent) {
        return UnitParts.join(incomingText(ds, ent), fateText(ds, ent));
    }

    // The present and future states of a living unit within one turn, or
    // null: across a turn boundary the future counters have reset and diffs
    // are meaningless — the game blanks its pip preview then too.
    private static EntState[] previewStates(DungeonScreen ds, Ent ent) {
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        EntState future = ds.getFightLog().getState(FightLog.Temporality.Future, ent);
        if (present == null || future == null || present.isDead()
                || present.getSnapshot().getTurn() != future.getSnapshot().getTurn()) {
            return null;
        }
        return new EntState[] {present, future};
    }

    static String incomingText(DungeonScreen ds, Ent ent) {
        EntState[] states = previewStates(ds, ent);
        if (states == null) {
            return null;
        }
        EntState present = states[0];
        EntState future = states[1];
        StringBuilder parts = new StringBuilder();
        int damage = future.getBlockableDamageTaken() - present.getBlockableDamageTaken();
        if (damage > 0) {
            parts.append(damage);
        }
        int poison = future.getPoisonDamageTaken(true) - present.getPoisonDamageTaken(true);
        if (poison > 0) {
            if (parts.length() > 0) {
                parts.append(", ");
            }
            parts.append(Loc.get("combat", "incoming_poison", "n", poison));
        }
        int other = present.getHp() - future.getHp() - damage - poison;
        if (other != 0) {
            if (parts.length() > 0) {
                parts.append(", ");
            }
            parts.append(other > 0 ? Loc.get("combat", "incoming_unblockable", "n", other)
                    : Loc.get("combat", "incoming_healing", "n", -other));
        }
        // Statuses that will land this turn — the icons TriggerPanel ghosts
        // as incoming, mirrored with the game's own incoming test.
        for (Personal p : future.getActivePersonals()) {
            if (!p.showInEntPanel() || p.skipNetAndIcon() || !p.showAsIncoming()) {
                continue;
            }
            Boolean incoming = Personal.treatAsIncoming(p, present.getActivePersonals());
            if (incoming != null && !incoming) {
                continue;
            }
            if (parts.length() > 0) {
                parts.append(", ");
            }
            parts.append(GameText.t(statusName(p)));
        }
        return parts.length() > 0 ? Loc.get("combat", "incoming", "parts", parts.toString()) : null;
    }

    // Where this turn leaves a living unit: "flees", "dies", or "overkill 2",
    // which says both that they die and by how much — whether a partial
    // block can still save them.
    static String fateText(DungeonScreen ds, Ent ent) {
        EntState[] states = previewStates(ds, ent);
        if (states == null || !states[1].isDead()) {
            return null;
        }
        EntState future = states[1];
        if (future.isFled()) {
            return Loc.get("combat", "flees");
        }
        int overkill = -future.getHp();
        return overkill > 0 ? Loc.get("combat", "overkill", "n", overkill) : Loc.get("combat", "dies");
    }

    // "targets Thief, Fighter" — the monster's locked-in targets, from the
    // same command walk the game's hover arrows and the hero-coloured
    // stripes on the monster panel draw from (redirects resolved). A monster
    // supporting another monster shows the same arrow, so ally targets speak
    // too; only self stays on the side text.
    static String targetsText(DungeonScreen ds, Ent ent) {
        List<Ent> targets = new java.util.ArrayList<Ent>(
                ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getAllTargeters(ent, false));
        StringBuilder sb = new StringBuilder();
        for (Ent target : targets) {
            if (target != ent) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(GameUi.entName(target));
            }
        }
        return sb.length() > 0 ? Loc.get("combat", "targets", "names", sb.toString()) : null;
    }

    // The focus line's targets: nothing when they are every living hero —
    // the side already says whom it hits ("to all enemies") and the names
    // add five words to it. The monster buffer lists them regardless.
    static String aimText(DungeonScreen ds, Ent ent) {
        com.tann.dice.gameplay.fightLog.Snapshot present = ds.getFightLog().getSnapshot(FightLog.Temporality.Present);
        java.util.Set<Ent> targets = new java.util.HashSet<Ent>(present.getAllTargeters(ent, false));
        targets.remove(ent);
        return targets.equals(new java.util.HashSet<Ent>(present.getEntities(true, false))) ? null
                : targetsText(ds, ent);
    }

    // "targeted by Ogre, Slimer" — the enemies whose commands aim at this
    // unit, the hover arrows' other direction; null when none does.
    static String targetedByText(DungeonScreen ds, Ent ent) {
        List<Ent> attackers = new java.util.ArrayList<Ent>(
                ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getAllTargeters(ent, true));
        StringBuilder sb = new StringBuilder();
        for (Ent attacker : attackers) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(GameUi.entName(attacker));
        }
        return sb.length() > 0 ? Loc.get("combat", "targeted_by", "names", sb.toString()) : null;
    }

    // The attack explanel's payload: the calculated rolled side, the arrow's
    // target, and the passives the panel stacks beneath itself — statuses and
    // traits, on the panel's own showInDiePanel filter.
    private static String attackPanelText(DungeonScreen ds, Ent ent) {
        StringBuilder sb = new StringBuilder(currentSideText(ent));
        String targets = targetsText(ds, ent);
        if (targets != null) {
            sb.append(", ").append(targets);
        }
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);
        if (present != null) {
            for (com.tann.dice.gameplay.trigger.personal.Personal p : present.getActivePersonals()) {
                if (!p.showInDiePanel()) {
                    continue;
                }
                sb.append(". ").append(SpecialPips.describe(p));
            }
        }
        return sb.toString();
    }

    // The valid-target border highlight, inverted to words: silence is the
    // common case — a valid target, or a faction the selected effect cannot
    // reach at all (an attack never touches your own heroes). "invalid"
    // marks the informative exception: faction-mates are targetable but this
    // ent is not (back row out of reach, damaged-only restrictions...).
    private static String invalidTargetText(DungeonScreen ds, Ent ent) {
        Targetable selected = ds.targetingManager.getSelectedTargetable();
        if (selected == null || !PhaseManager.get().getPhase().canTarget()) {
            return null;
        }
        List<Ent> valid = TargetingManager.getValidTargets(
                ds.getFightLog().getSnapshot(FightLog.Temporality.Present), selected, true);
        if (valid.contains(ent)) {
            return null;
        }
        for (Ent v : valid) {
            if (v.isPlayer() == ent.isPlayer()) {
                return Loc.get("combat", "target_invalid");
            }
        }
        return null;
    }

    /** The rolled side's calculated, buff-adjusted text, or "rolling" mid-tumble. */
    public static String currentSideText(Ent ent) {
        EntDie die = ent.getDie();
        EntSide side = die.getCurrentSide();
        if (side == null || die.getState() == Die.DieState.Rolling) {
            return Loc.get("combat", "die_rolling");
        }
        return SideText.of(side.findState(FightLog.Temporality.Present, ent));
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
                        return confirmText(confirmButton);
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
