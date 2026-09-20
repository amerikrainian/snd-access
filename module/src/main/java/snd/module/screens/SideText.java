package snd.module.screens;

import com.tann.dice.gameplay.effect.eff.Eff;
import com.tann.dice.gameplay.effect.eff.keyword.Keyword;
import com.tann.dice.gameplay.fightLog.EntSideState;

import snd.core.loc.Loc;
import snd.module.GameText;

/**
 * A die side as the game describes it, with the pips the face draws. The
 * game's sentence carries the value where the effect type uses it ("4 damage
 * pain") and drops it where only a keyword does: a Monk's "Redirect all
 * damage and enemy effects from an ally to me selfshield" shows its 3 as
 * pips on the face alone. Such a side leads with what the pips feed —
 * "selfshield 3, Redirect all damage..." — the keywords in the game's own
 * rendering.
 */
final class SideText {
    private SideText() {
    }

    /** A side in a fight or on a sheet: calculated effect, the game's bonus string kept. */
    static String of(EntSideState side) {
        Eff eff = side.getCalculatedEffect();
        if (valueSaid(eff)) {
            return GameText.t(side.describe());
        }
        String bonus = side.getBonusString();
        return lead(eff, bonus.isEmpty() ? eff.describe(false) : eff.describe(false) + " " + bonus);
    }

    /** A side's base effect, where no unit holds it (the almanac, a reward's tile). */
    static String of(Eff eff) {
        return valueSaid(eff) ? GameText.t(eff.describe()) : lead(eff, eff.describe(false));
    }

    private static String lead(Eff eff, String description) {
        String keywords = Eff.addKeywordsToString("", eff);
        String sentence = GameText.t(description);
        return Loc.get("ui", "side.keyword_pips", "keywords", GameText.t(keywords), "n", eff.getValue(),
                "side", sentence);
    }

    // Asked of the game itself: a sentence that reads the same at another
    // value does not say the value, and the pips are worth leading with only
    // when a displayed keyword's rules do move with them ("shield myself for
    // 3"). "Summon a Bones" is a 1 the sentence does say.
    private static boolean valueSaid(Eff eff) {
        if (!eff.hasValue()) {
            return true;
        }
        Eff other = eff.copy();
        other.setValue(eff.getValue() + 1);
        if (!other.describe(false).equals(eff.describe(false))) {
            return true;
        }
        for (Keyword keyword : eff.getKeywordsForDisplay(false)) {
            if (!String.valueOf(keyword.getRules(other)).equals(String.valueOf(keyword.getRules(eff)))) {
                return false;
            }
        }
        return true;
    }
}
