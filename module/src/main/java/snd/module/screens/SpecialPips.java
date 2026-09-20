package snd.module.screens;

import com.tann.dice.gameplay.fightLog.EntState;
import com.tann.dice.gameplay.trigger.personal.Personal;
import com.tann.dice.gameplay.trigger.personal.specialPips.SpecialHp;

import snd.core.loc.Loc;
import snd.module.GameText;

/**
 * The marked pips of an hp bar (SpecialHp: a Slimer's summon pip, stone and
 * ghost hp, a death pip). The game writes such a trait as "[pip image] =
 * rule (where)" and draws the pip in the bar; the image is the sentence's
 * subject, so here it is named, and the bar's next marked pip is given as the
 * hp that removing it leaves.
 */
final class SpecialPips {
    private SpecialPips() {
    }

    /** A status or trait in full; a marked pip's with the pip named in place of its image. */
    static String describe(Personal p) {
        String text = GameText.t(p.describeForTriggerPanel());
        if (!(p instanceof SpecialHp)) {
            return text;
        }
        // SpecialHp.describeForSelfBuff: image tags, then " = ", then the rule.
        int eq = text.indexOf(" = ");
        return Loc.get("combat", "pip", "rule", eq < 0 ? text : text.substring(eq + 3));
    }

    /**
     * The highest marked pip still in the bar, as the hp the unit is at once it
     * is removed; -1 without one. A marking that covers every hp places nothing.
     */
    static int next(EntState state) {
        int next = -1;
        for (Personal p : UnitLines.sheetPersonals(state)) {
            if (!(p instanceof SpecialHp)) {
                continue;
            }
            int[] pips = ((SpecialHp) p).getPips(state.getMaxHp());
            if (pips.length >= state.getMaxHp()) {
                continue;
            }
            for (int pip : pips) {
                if (pip < state.getHp() && pip > next) {
                    next = pip;
                }
            }
        }
        return next;
    }
}
