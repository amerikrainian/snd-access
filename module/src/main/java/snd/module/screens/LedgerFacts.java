package snd.module.screens;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.tann.dice.gameplay.content.ent.type.HeroType;
import com.tann.dice.gameplay.content.ent.type.MonsterType;
import com.tann.dice.gameplay.content.ent.type.lib.HeroTypeUtils;
import com.tann.dice.gameplay.content.item.Item;
import com.tann.dice.gameplay.context.DungeonContext;
import com.tann.dice.gameplay.progress.chievo.unlock.UnUtil;
import com.tann.dice.gameplay.progress.stats.stat.Stat;
import com.tann.dice.gameplay.progress.stats.stat.pickRate.PickStat;
import com.tann.dice.screens.dungeon.DungeonScreen;
import com.tann.dice.screens.dungeon.panels.book.page.BookPage;

import snd.module.Captured;
import snd.module.GameText;

/**
 * What the almanac's hero, monster and item pages decide as they build
 * (LedgerUtils.makeHeroGroup / makeMonsterGroup / makeItemsGroup) and keep
 * nowhere: whether one has been met, a hero seen or banned in the current
 * run, and how often one was chosen. Worked out the game's way from the stats
 * the page was built with and the run in progress, so neither the tiles nor
 * the panels they open are read off what was drawn.
 */
final class LedgerFacts {
    private final Map<String, Stat> stats;

    private LedgerFacts(Map<String, Stat> stats) {
        this.stats = stats;
    }

    /** The facts of an almanac page, or null when its stats can't be read (logged). */
    @SuppressWarnings("unchecked")
    static LedgerFacts of(BookPage page) {
        Map<String, Stat> stats = (Map<String, Stat>) Captured.field(page, BookPage.class, "allMergedStats");
        return stats != null ? new LedgerFacts(stats) : null;
    }

    /** The marks the page shows on a tile, in the words of the panel it opens. */
    List<String> marks(Object subject) {
        List<String> marks = new ArrayList<String>();
        if (unencountered(subject)) {
            marks.add(GameText.t("Not encountered yet..."));
        }
        if (subject instanceof HeroType) {
            marks.addAll(runMarks((HeroType) subject));
        }
        return marks;
    }

    /**
     * A hero's marks in the run in progress, as its almanac panel says them.
     * The tile leaves the banned badge off red and blue heroes; the panel
     * does not.
     */
    static List<String> runMarks(HeroType hero) {
        List<String> marks = new ArrayList<String>();
        if (!(com.tann.dice.Main.getCurrentScreen() instanceof DungeonScreen)) {
            return marks;
        }
        DungeonContext dc = DungeonScreen.get().getDungeonContext();
        if (dc.makeSeenHeroTypes(null).contains(hero)) {
            marks.add(GameText.t("Seen this run"));
        }
        if (HeroTypeUtils.bannedHeroTypeByCollision(hero, dc.getParty().getBannedCollisionBits(false))) {
            marks.add(GameText.t("Banned this run [blue](mana)"));
        }
        return marks;
    }

    /** The veil over one never met (a hero above tier one never offered, a monster never fought, an item never offered). */
    private boolean unencountered(Object subject) {
        if (subject instanceof HeroType) {
            HeroType hero = (HeroType) subject;
            return !UnUtil.isLocked(hero) && hero.level != 1 && picks(hero) == 0;
        }
        if (subject instanceof MonsterType) {
            MonsterType monster = (MonsterType) subject;
            return !UnUtil.isLocked(monster)
                    && stats.get(com.tann.dice.gameplay.progress.stats.stat.endOfFight.monsters.KillsStat.getStatName(monster)).getValue() == 0
                    && fights(monster, true) == 0 && fights(monster, false) == 0;
        }
        if (subject instanceof Item) {
            Item item = (Item) subject;
            return !UnUtil.isLocked(item) && picks(item) == 0;
        }
        return false;
    }

    private int fights(MonsterType monster, boolean won) {
        return stats.get(com.tann.dice.gameplay.progress.stats.stat.endOfFight.monsters.tracker.MonsterTrackerStat.getNameFrom(monster, won))
                .getValue();
    }

    /** "chosen 1/2 (50%)": the line the page puts on a hero's (above tier one) or an item's panel. */
    String chosenLine(Object subject) {
        if (subject instanceof HeroType && ((HeroType) subject).level > 1) {
            HeroType hero = (HeroType) subject;
            return hero.skipStats() ? BookPage.getChosenString(0, 0)
                    : BookPage.getChosenString(chosen(hero, false), chosen(hero, true));
        }
        if (subject instanceof Item) {
            return BookPage.getChosenString(chosen(subject, false), chosen(subject, true));
        }
        return null;
    }

    private int picks(Object subject) {
        if (subject instanceof HeroType && ((HeroType) subject).skipStats()) {
            return 0;
        }
        return chosen(subject, false) + chosen(subject, true);
    }

    private int chosen(Object subject, boolean rejected) {
        Stat stat = subject instanceof HeroType ? stats.get(PickStat.nameFor((HeroType) subject))
                : stats.get(PickStat.nameFor((Item) subject));
        return PickStat.val(stat, rejected);
    }
}
