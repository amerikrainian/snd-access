package snd.module.screens;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.content.ent.die.side.EntSide;
import com.tann.dice.gameplay.content.ent.type.EntType;
import com.tann.dice.gameplay.content.item.Item;
import com.tann.dice.gameplay.effect.Trait;
import com.tann.dice.gameplay.effect.targetable.ability.Ability;
import com.tann.dice.gameplay.fightLog.EntSideState;
import com.tann.dice.gameplay.fightLog.EntState;
import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.gameplay.modifier.Modifier;
import com.tann.dice.gameplay.trigger.global.Global;
import com.tann.dice.gameplay.trigger.personal.Personal;
import com.tann.dice.screens.dungeon.DungeonScreen;
import com.tann.dice.screens.dungeon.panels.Explanel.NetPanel;

import snd.contracts.SndLog;
import snd.core.loc.Loc;
import snd.module.GameText;

/**
 * A unit as review lines, for the subject-fed buffers: the whole of a hero or
 * a monster — what its sheet shows, a line each — without opening the sheet or
 * leaving the control that concerns it. Read from the FightLog's Present
 * state at every buffer keypress. One tooltip is one line, never several
 * joined; a definition several sides or statuses share is given once. Terms
 * are defined by the game alone ({@link Terms}).
 */
public final class UnitLines {
    private UnitLines() {
    }

    /**
     * The unit: its name, level and hp display; a monster's targets; its six
     * sides, the rolled one marked; the sides' keyword rules; every status the
     * sheet draws, in full; the traits beside the net, a spell as its card.
     * Items are their own buffer.
     */
    public static List<String> of(Ent ent) {
        DungeonScreen ds = DungeonScreen.get();
        List<String> lines = new ArrayList<String>();
        EntState present = ds.getFightLog().getState(FightLog.Temporality.Present, ent);

        StringBuilder head = new StringBuilder(SheetScreen.headerText(ds, ent));
        if (present != null && present.isDead()) {
            head.append(", ").append(Loc.get("combat", "defeated"));
        } else {
            String incoming = CombatScreen.previewText(ds, ent);
            if (incoming != null) {
                head.append(", ").append(incoming);
            }
        }
        lines.add(head.toString());
        if (present != null && present.isDead() && ent.isPlayer()) {
            // The sheet's skull-tag rule, the game's own sentence.
            lines.add(GameText.t("Heroes defeated last fight return with half hp"));
        }
        // Who aims at whom: a monster's targets, and whoever targets this unit.
        for (String aim : new String[] {ent.isPlayer() ? null : CombatScreen.targetsText(ds, ent),
                CombatScreen.targetedByText(ds, ent)}) {
            if (aim != null) {
                lines.add(aim);
            }
        }
        body(lines, ent, present, true);
        return lines;
    }

    /**
     * A unit type, where no unit of it exists (the almanac's tiles, the
     * Choose-Party class picker): what the game's panel for it shows, read
     * from a unit of the type at rest, as the panel is built
     * (HeroType.makeEnt). Its name, colour and level, its hp, then the rest
     * as {@link #of(Ent)} gives it.
     */
    public static List<String> of(EntType type) {
        return atRest(type.makeEnt());
    }

    /** A unit outside the fight (a type's fresh unit, a panel's): its state is the game's blank one. */
    public static List<String> atRest(Ent ent) {
        List<String> lines = new ArrayList<String>();
        EntState state = ent.getState(FightLog.Temporality.Present);
        lines.add(restHeader(ent, state));
        body(lines, ent, state, false);
        return lines;
    }

    /** "Thief, orange, level 1, 4 hp": who a unit at rest is and its hp. */
    static String restHeader(Ent ent, EntState state) {
        String hp = state.isAtMaxHp() ? Loc.get("combat", "hp_full", "hp", state.getMaxHp())
                : Loc.get("combat", "hp", "hp", state.getHp(), "max", state.getMaxHp());
        return SheetScreen.identityText(ent) + ", " + hp;
    }

    /** "4 hp": a unit type's hp, as the panel for it shows. */
    public static String restHp(EntType type) {
        return Loc.get("combat", "hp_full", "hp",
                type.makeEnt().getState(FightLog.Temporality.Present).getMaxHp());
    }

    // The sides, their rules, the statuses and traits the sheet draws, and
    // the glossary entries the lines use.
    private static void body(List<String> lines, Ent ent, EntState present, boolean rolledMarks) {
        // A rule is given once, however many sides carry the keyword.
        Set<String> rules = new LinkedHashSet<String>();
        EntSide[] sides = ent.getSides();
        for (int i : SideText.readingOrder()) {
            EntSideState side = sides[i].findState(FightLog.Temporality.Present, ent);
            String line = SideText.at(i, SideText.of(side));
            lines.add(rolledMarks && SheetScreen.isRolled(ent, i) ? line + ", " + Loc.get("ui", "sheet.rolled") : line);
            try {
                rules.addAll(CombatScreen.keywordRuleLines(side.getCalculatedEffect()));
            } catch (Throwable t) {
                SndLog.error("unit lines: side keyword rules failed", t);
            }
        }
        lines.addAll(rules);

        // Statuses and traits, each in full, then the keywords they reference.
        Set<String> referenced = new LinkedHashSet<String>();
        for (Personal p : sheetPersonals(present)) {
            lines.add(SpecialPips.describe(p));
            referenced.addAll(Terms.forPersonal(p));
        }
        // The traits drawn beside the net, a caster's spell among them.
        for (Trait t : netTraits(ent, present)) {
            lines.add(netTraitLine(t));
            referenced.addAll(netTraitRules(t));
        }
        referenced.removeAll(rules);
        lines.addAll(referenced);
        lines.addAll(Terms.glossary(lines));
    }

    /**
     * The die a control's subject rolls, for the side glance: a unit, or a
     * unit type where no unit exists (the almanac's tiles); null for
     * anything else.
     */
    public static Object dieOwner(Object subject) {
        return subject instanceof Ent || subject instanceof EntType ? subject : null;
    }

    /** How many sides the owner's die has. */
    public static int sideCount(Object owner) {
        return owner instanceof Ent ? ((Ent) owner).getSides().length : ((EntType) owner).sides.length;
    }

    /**
     * The digit'th side in the sheet's reading order ("left: 2 damage"): a
     * unit's as its state calculates it (items and all), a type's as its
     * base effect. Null past the last side.
     */
    public static String sideLine(Object owner, int digit) {
        int[] order = SideText.readingOrder();
        if (digit < 0 || digit >= order.length || order[digit] >= sideCount(owner)) {
            return null;
        }
        int i = order[digit];
        if (owner instanceof Ent) {
            Ent ent = (Ent) owner;
            return SideText.at(i, SideText.of(ent.getSides()[i].findState(FightLog.Temporality.Present, ent)));
        }
        return SideText.at(i, SideText.of(((EntType) owner).sides[i].getBaseEffect()));
    }

    /**
     * The statuses and traits the game's character sheet lists
     * (EntPanelInventory.makeTraitActors): whatever says it shows in the die
     * panel, a trait only while it is a visible one. The game's rule rather
     * than a test of our own, so a modded status shows exactly where the
     * game shows it.
     */
    static List<Personal> sheetPersonals(EntState state) {
        List<Personal> shown = new ArrayList<Personal>();
        if (state != null) {
            for (Personal p : state.getActivePersonals()) {
                if (p.showInDiePanel() && (p.getTrait() == null || p.getTrait().visible)) {
                    shown.add(p);
                }
            }
        }
        return shown;
    }

    /**
     * The traits the game draws beside the die net and again under the sheet
     * (NetPanel.showTraitInNet, EntPanelInventory's extras): among them a
     * caster's spell, which the die-panel list never shows (LearnAbility).
     * One that list already shows (a stone hp trait is on both) is left out.
     */
    static List<Trait> netTraits(Ent ent, EntState present) {
        List<Personal> listed = sheetPersonals(present);
        List<Trait> shown = new ArrayList<Trait>();
        for (Trait t : ent.traits) {
            if (NetPanel.showTraitInNet(t) && !listed.contains(t.personal)) {
                shown.add(t);
            }
        }
        return shown;
    }

    /** A net trait as the sheet draws it: a taught ability as its card, any other trait in full. */
    static String netTraitLine(Trait t) {
        Ability a = t.personal.getAbility();
        return a != null ? CombatScreen.abilityLine(a) : SpecialPips.describe(t.personal);
    }

    /** The keyword rules a net trait's line uses. */
    static List<String> netTraitRules(Trait t) {
        Ability a = t.personal.getAbility();
        return a != null ? Terms.forEff(a.getDerivedEffects()) : Terms.forPersonal(t.personal);
    }

    /**
     * The abilities an item teaches, each as its card reads — the item's own
     * description only names them ("Learn the spell: Poultice").
     */
    static List<String> taughtAbilities(Item item) {
        List<String> lines = new ArrayList<String>();
        for (Personal p : item.getPersonals()) {
            if (p.getAbility() != null) {
                lines.add(CombatScreen.abilityLine(p.getAbility()));
            }
        }
        return lines;
    }

    /**
     * The spells a modifier teaches, each as its card reads — a spell
     * blessing's description does not even name it ("Learn a new spell").
     */
    static List<String> taughtAbilities(Modifier modifier) {
        List<String> lines = new ArrayList<String>();
        for (Global g : modifier.getGlobals()) {
            if (g.getGlobalSpell() != null) {
                lines.add(CombatScreen.abilityLine(g.getGlobalSpell()));
            }
        }
        return lines;
    }

    /** What a unit carries: each item's name and tier, then its description. */
    public static List<String> items(Ent ent) {
        // A keyword two items share is defined once.
        Set<String> lines = new LinkedHashSet<String>();
        List<Item> items = ent.getItems();
        if (items != null) {
            for (Item item : items) {
                lines.addAll(item(item));
            }
        }
        return new ArrayList<String>(lines);
    }

    public static List<String> item(Item item) {
        List<String> lines = new ArrayList<String>();
        String name = GameText.t(item.getName(true));
        lines.add(item.hasTier() ? name + ", " + Loc.get("ui", "choice.tier", "tier", item.getTier()) : name);
        String desc = item.getDescription();
        if (desc != null && !desc.trim().isEmpty()) {
            lines.add(GameText.t(desc));
        }
        lines.addAll(taughtAbilities(item));
        lines.addAll(Terms.forItem(item));
        return lines;
    }

    /**
     * A modifier as its card reads (ModifierPanel): name and tier, the
     * description, the spells it teaches, the rules of the keywords it
     * references.
     */
    public static List<String> modifier(Modifier modifier) {
        List<String> lines = new ArrayList<String>();
        String name = ChoiceScreen.nameOf(modifier);
        String tier = ChoosablePanelNodes.tierText(modifier);
        lines.add(tier != null ? name + ", " + tier : name);
        String desc = modifier.getFullDescription();
        if (desc != null && !desc.trim().isEmpty()) {
            lines.add(GameText.t(desc));
        }
        lines.addAll(taughtAbilities(modifier));
        lines.addAll(Terms.forModifier(modifier));
        return lines;
    }

    /**
     * One line per unit of a side, its hp display: the heroes as their column
     * stands (the defeated keep their place), the monsters still in the fight.
     */
    public static List<String> side(boolean heroes) {
        List<String> lines = new ArrayList<String>();
        List<Ent> ents = DungeonScreen.get().getFightLog().getSnapshot(FightLog.Temporality.Present)
                .getEntities(heroes, heroes ? null : Boolean.FALSE);
        for (Ent ent : ents) {
            lines.add(CombatScreen.vitalsLine(ent));
        }
        return lines;
    }
}
