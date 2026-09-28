package snd.module.screens;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.tann.dice.gameplay.content.ent.group.PartyLayoutType;
import com.tann.dice.gameplay.content.ent.type.HeroCol;
import com.tann.dice.gameplay.content.ent.type.HeroType;
import com.tann.dice.gameplay.content.ent.type.lib.HeroTypeUtils;
import com.tann.dice.gameplay.context.config.ContextConfig;
import com.tann.dice.gameplay.save.settings.option.OptionLib;
import com.tann.dice.gameplay.trigger.global.chance.RarityUtils;
import com.tann.dice.gameplay.trigger.global.heroLevelupAffect.HeroGenType;
import com.tann.dice.util.listener.TannListener;

import snd.contracts.SndLog;
import snd.core.loc.Loc;
import snd.module.GameText;

/**
 * A party-layout option ("Choose party layout", GameStart) is an anonymous
 * card: a name over five squares, coloured, or a "?" for a slot filled at
 * random when the run starts. What it starts is read from its click listener,
 * which holds the layout, the run's config and how the heroes are made.
 */
final class PartyLayouts {
    private PartyLayouts() {
    }

    private static Field colsField;

    static String name(Actor actor) {
        PartyLayoutType layout = captured(actor, PartyLayoutType.class);
        if (layout == null) {
            return null;
        }
        String name = GameText.t(layout.name());
        // PartyLayoutType.addRarityIfNecessary
        if (OptionLib.SHOW_RARITY.c() && layout.getChance() != RarityUtils.IGNORED_RARITY) {
            name += ", r: " + layout.getChance();
        }
        return name;
    }

    /** The squares, left to right: a colour each, or random. */
    static String colours(Actor actor) {
        PartyLayoutType layout = captured(actor, PartyLayoutType.class);
        if (layout == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (HeroCol col : cols(layout)) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(col == null ? Loc.get("ui", "value.random") : GameText.t(col.colName));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * For each colour on the card, the heroes the run can start with in it:
     * the pool Party.makeNormal draws from at the levels the party starts at.
     * A mode that generates its heroes (HeroGenType other than Normal) has no
     * such pool, and a "?" slot's colour is not known until the run starts.
     */
    static List<String> pools(Actor actor) {
        List<String> lines = new ArrayList<String>();
        PartyLayoutType layout = captured(actor, PartyLayoutType.class);
        ContextConfig cc = captured(actor, ContextConfig.class);
        HeroGenType hgt = captured(actor, HeroGenType.class);
        if (layout == null || cc == null || hgt != HeroGenType.Normal) {
            return lines;
        }
        Set<Integer> levels = startingLevels(cc.getLevelOffset(), layout.length());
        long banned = layout.getBannedCollisionBits(false);
        Set<HeroCol> seen = new LinkedHashSet<HeroCol>(Arrays.asList(cols(layout)));
        seen.remove(null);
        for (HeroCol col : seen) {
            for (int level : levels) {
                StringBuilder heroes = new StringBuilder();
                for (HeroType ht : HeroTypeUtils.getFilteredTypes(col, level, false)) {
                    if (ht.isMissingno() || HeroTypeUtils.bannedHeroTypeByCollision(ht, banned)) {
                        continue;
                    }
                    if (heroes.length() > 0) {
                        heroes.append(", ");
                    }
                    heroes.append(GameText.t(ht.getName(true)));
                }
                if (heroes.length() == 0) {
                    continue;
                }
                String head = GameText.t(col.colName);
                if (levels.size() > 1) {
                    head += ", " + Loc.get("combat", "level", "n", level);
                }
                lines.add(head + ": " + heroes);
            }
        }
        return lines;
    }

    // Party.generateHeroes: a level budget spread evenly over the slots, then
    // shuffled — which slot gets which level is random, the set of levels is not.
    private static Set<Integer> startingLevels(int rewardsGained, int slots) {
        Set<Integer> levels = new TreeSet<Integer>();
        int budget = slots + Math.min(rewardsGained + 1, 20) / 2;
        for (int i = 0; i < slots; i++) {
            int level = budget / (slots - i);
            levels.add(level);
            budget -= level;
        }
        return levels;
    }

    private static HeroCol[] cols(PartyLayoutType layout) {
        try {
            if (colsField == null) {
                colsField = PartyLayoutType.class.getDeclaredField("cols");
                colsField.setAccessible(true);
            }
            return (HeroCol[]) colsField.get(layout);
        } catch (Throwable t) {
            SndLog.error("party layout colours read failed", t);
            return new HeroCol[0];
        }
    }

    // A variable the card's TannListener captured (GameStart.startWithPLTChoice).
    private static <T> T captured(Actor actor, Class<T> type) {
        for (EventListener listener : actor.getListeners()) {
            if (!(listener instanceof TannListener)) {
                continue;
            }
            for (Field field : listener.getClass().getDeclaredFields()) {
                if (!type.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    return type.cast(field.get(listener));
                } catch (Throwable t) {
                    SndLog.error("party layout card: " + type.getSimpleName() + " read failed", t);
                    return null;
                }
            }
        }
        return null;
    }
}
