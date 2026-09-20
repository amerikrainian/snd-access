package snd.module.screens;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.content.ent.die.side.EntSide;
import com.tann.dice.gameplay.content.item.Item;
import com.tann.dice.gameplay.fightLog.EntSideState;
import com.tann.dice.gameplay.fightLog.EntState;
import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.gameplay.trigger.personal.Personal;
import com.tann.dice.screens.dungeon.DungeonScreen;

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
     * sheet draws, in full. Items are their own buffer.
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

        // A rule is given once, however many sides carry the keyword.
        Set<String> rules = new LinkedHashSet<String>();
        EntSide[] sides = ent.getSides();
        for (int i = 0; i < sides.length; i++) {
            EntSideState side = sides[i].findState(FightLog.Temporality.Present, ent);
            String line = (i + 1) + ": " + GameText.t(side.describe());
            lines.add(SheetScreen.isRolled(ent, i) ? line + ", " + Loc.get("ui", "sheet.rolled") : line);
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
        referenced.removeAll(rules);
        lines.addAll(referenced);
        lines.addAll(Terms.glossary(lines));
        return lines;
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
        lines.addAll(Terms.forItem(item));
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
