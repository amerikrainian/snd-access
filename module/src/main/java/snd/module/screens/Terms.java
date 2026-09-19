package snd.module.screens;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.tann.dice.gameplay.content.item.Item;
import com.tann.dice.gameplay.effect.eff.Eff;
import com.tann.dice.gameplay.effect.eff.keyword.Keyword;
import com.tann.dice.gameplay.trigger.personal.Personal;

import snd.contracts.speech.TextFilter;
import snd.module.GameText;

/**
 * The game's own definitions of the terms a description uses, as review
 * lines — and nothing else: a term the game never defines (it has no
 * definition of "overkill", say) gets none here either. Three sources, all
 * the game's:
 * <ul>
 * <li>keywords — a side's or an ability's displayed keywords, and the ones a
 * status, trait or item says it references ({@code getReferencedKeywords},
 * the list the game's info panels draw keyword boxes from), each as the
 * game composes the box ("name: rules", {@code KUtils.makeActor}), followed by
 * the longer explanation its almanac page adds;</li>
 * <li>the almanac glossary's entries, for the ones a line actually uses.</li>
 * </ul>
 * Which keywords apply is always the game's call (what a thing displays or
 * says it references), so modded content is covered and nothing is guessed.
 * Only the glossary is matched by word, since the game links its entries to
 * nothing.
 */
public final class Terms {
    private Terms() {
    }

    /**
     * One line per keyword, "name: rules", then its extra rules when it has
     * any. {@code source} specialises the numbers ("inflicts 2", not
     * "inflicts N"); null reads the general rule, as the game does for a
     * status.
     */
    public static List<String> keywordLines(List<Keyword> keywords, Eff source) {
        List<String> lines = new ArrayList<String>();
        if (keywords == null) {
            return lines; // AffectSideEffect's base returns null for "none"
        }
        for (Keyword keyword : keywords) {
            lines.add(GameText.t(keyword.getColourTaggedString()) + ": " + GameText.t(keyword.getRules(source)));
            String extra = keyword.getExtraRules();
            if (extra != null && !extra.trim().isEmpty()) {
                lines.add(GameText.t(extra));
            }
        }
        return lines;
    }

    /** A side's or an ability's effect: the keywords the game displays for it, numbers filled in. */
    public static List<String> forEff(Eff eff) {
        return eff == null ? new ArrayList<String>() : keywordLines(eff.getKeywordsForDisplay(true), eff);
    }

    /** A status or trait: the keywords it says it references. */
    public static List<String> forPersonal(Personal personal) {
        return keywordLines(personal.getReferencedKeywords(), null);
    }

    /** An item: the keywords its effects reference. */
    public static List<String> forItem(Item item) {
        return keywordLines(item.getReferencedKeywords(), null);
    }

    // The almanac glossary (HelpPage.populateList, case Glossary), the entries
    // a description can use. The page builds them inline as actors, so the
    // English sources are repeated here and go through the game's translator
    // like any model-sourced text.
    private static final String[][] GLOSSARY = {
        {"(?i)\\bon-hit\\b", "[orange]On-Hit",
            "An effect that triggers when attacked by a hero [grey](upon receiving unblocked non-ranged damage)"},
        {"(?<![\\p{L}\\p{N}])N(?![\\p{L}\\p{N}])", "N", "The number of pips on this side"},
        {"(?i)\\bpips?\\b", "Pips",
            "The white bars on the right of each side showing its value ([light][p][img][p][cu])."},
    };
    private static final Pattern[] GLOSSARY_PATTERNS = new Pattern[GLOSSARY.length];

    static {
        for (int i = 0; i < GLOSSARY.length; i++) {
            GLOSSARY_PATTERNS[i] = Pattern.compile(GLOSSARY[i][0]);
        }
    }

    /** The glossary entries for the terms these lines use, each once. */
    public static List<String> glossary(List<String> lines) {
        List<String> entries = new ArrayList<String>();
        for (int i = 0; i < GLOSSARY.length; i++) {
            for (String line : lines) {
                if (line != null && GLOSSARY_PATTERNS[i].matcher(TextFilter.clean(line)).find()) {
                    entries.add(GameText.t(GLOSSARY[i][1]) + ": " + GameText.t(GLOSSARY[i][2]));
                    break;
                }
            }
        }
        return entries;
    }
}
