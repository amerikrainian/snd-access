package snd.module.screens;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import snd.core.graph.AnnouncementKinds;
import snd.core.settings.Announcements;
import snd.module.settings.ModSettings;

/**
 * The events the mod speaks, each with the parts its lines are made of: what
 * the menu lets the player turn off, reorder, and keep out of the log. A
 * line is composed from its parts' texts by {@link #line}; an event with no
 * parts is spoken whole, its settings only whether it is spoken and kept.
 */
public final class EventParts {
    private EventParts() {
    }

    /** What a step of the fight did, a clause per combatant. */
    public static final String OUTCOME = "outcome";
    /** What each monster will do this turn, and who it kills. */
    public static final String INTENT = "intent";
    /** "Fight 3/20, Turn 2, rolling". */
    public static final String PHASE = "phase";
    /** The faces the party's dice landed on, and the rerolls left. */
    public static final String ROLL = "roll";
    /** The game's notice banner ("not enough mana"). */
    public static final String BANNER = "banner";
    /** Why a target was refused, the targeting red flash. */
    public static final String REFUSAL = "refusal";
    /** The ability bar's notices: mana gained, a spell discarded. */
    public static final String WISP = "wisp";
    /** Words floating over a unit: "dodged", "immune". */
    public static final String FLOATER = "floater";
    /** A unit's chatter. */
    public static final String CHATTER = "chatter";
    /** Achievements and other notices the game pops up. */
    public static final String POPUP = "popup";
    public static final String UNDO = "undo";
    /** A tutorial quest done. */
    public static final String TUTORIAL = "tutorial";
    /** A newer version of the mod is out. */
    public static final String UPDATE = "update";

    /** Who acted, opening an outcome. */
    public static final String SOURCE = "source";
    /** Hp lost, a bare number after the name. */
    public static final String DAMAGE = "damage";
    public static final String POISON = "poison";
    public static final String HEAL = "heal";
    public static final String MAX_HP = "max_hp";
    /** Statuses that ended. */
    public static final String LOST = "lost";
    /** "Fight 3/20". */
    public static final String FIGHT = "fight";
    public static final String TURN = "turn";
    /** "Enemies rolling", "Choose targets". */
    public static final String PHASE_NAME = "phase";
    /** "Reroll 1/2". */
    public static final String REROLLS = "rerolls";
    /** What floated over a unit, or what it said. */
    public static final String MESSAGE = "message";

    public static void declare(Announcements a) {
        String label = AnnouncementKinds.LABEL;
        a.event(OUTCOME, Arrays.asList(SOURCE, label, DAMAGE, POISON, HEAL, UnitParts.SHIELD, MAX_HP,
                UnitParts.STATUSES, LOST, UnitParts.FATE));
        a.suffix(id(OUTCOME), SOURCE, ":");
        a.suffix(id(OUTCOME), label, ""); // "Bandit 1 2": the hp lost runs on from the name
        a.event(INTENT, Arrays.asList(label, UnitParts.SIDE, UnitParts.AIM, UnitParts.FATE));
        a.suffix(id(INTENT), label, ":");
        a.event(PHASE, Arrays.asList(FIGHT, TURN, PHASE_NAME));
        a.event(ROLL, Arrays.asList(label, UnitParts.SIDE, REROLLS));
        a.suffix(id(ROLL), label, ":");
        for (String overUnit : new String[] {FLOATER, CHATTER}) {
            a.event(overUnit, Arrays.asList(label, MESSAGE));
            a.suffix(id(overUnit), label, ":");
        }
        for (String whole : new String[] {BANNER, REFUSAL, WISP, POPUP, UNDO, TUTORIAL, UPDATE}) {
            a.event(whole, Collections.<String>emptyList());
        }
    }

    private static String id(String event) {
        return Announcements.EVENTS + "." + event;
    }

    /** A line of event from its parts' texts, as the player arranged them; null when nothing is left to say. */
    public static String line(String event, Map<String, String> parts) {
        return ModSettings.get().announcements.compose(id(event), parts);
    }
}
