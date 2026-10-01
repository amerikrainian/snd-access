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
public final class SpecialPips {
    private SpecialPips() {
    }

    /** A status or trait in full; a marked pip's with the pip named in place of its image. */
    public static String describe(Personal p) {
        if (!(p instanceof SpecialHp)) {
            return GameText.t(p.describeForTriggerPanel());
        }
        // SpecialHp.describeForSelfBuff joins the pip's image to its rule and
        // where the pips sit; the rule and the place, from their own parts.
        return Loc.get("combat", "pip", "rule", rule((SpecialHp) p));
    }

    private static java.lang.reflect.Method ruleMethod;

    private static String rule(SpecialHp hp) {
        try {
            if (ruleMethod == null) {
                ruleMethod = SpecialHp.class.getDeclaredMethod("describe");
                ruleMethod.setAccessible(true);
            }
            Object where = snd.module.Captured.field(hp, SpecialHp.class, "pipLoc");
            String place = where != null
                    ? " [grey](" + GameText.t(((com.tann.dice.gameplay.trigger.personal.specialPips.pipLoc.PipLoc) where)
                            .describe()) + ")[cu]"
                    : "";
            return GameText.t((String) ruleMethod.invoke(hp)) + place;
        } catch (Exception e) {
            snd.contracts.SndLog.error("special hp rule read failed", e);
            return GameText.t(hp.describeForTriggerPanel());
        }
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
