package snd.module.screens;

import java.util.Arrays;

import snd.core.graph.AnnouncementKinds;
import snd.core.settings.Announcements;

/**
 * The parts of a unit as the mod speaks it — a combatant's row, and the
 * glances, buffers and events that say the same facts. One name per fact, so
 * silencing "shield" silences it everywhere it is said.
 */
public final class UnitParts {
    private UnitParts() {
    }

    /** The readout of a unit's row among the combatants. */
    public static final String COMBATANT = "combatant";

    /** "used", "locked", "defeated": the hero's die, said first so a spent row can be skipped. */
    public static final String DIE_STATE = "die_state";
    /** "invalid" while choosing a target. */
    public static final String INVALID = "invalid";
    public static final String SIDE = "side";
    /** A monster's targets. */
    public static final String AIM = "aim";
    public static final String HP = "hp";
    public static final String SHIELD = "shield";
    /** The next marked hp pip. */
    public static final String MARK = "mark";
    public static final String STATUSES = "statuses";
    public static final String INCOMING = "incoming";
    /** "dies", "flees", "overkill 2". */
    public static final String FATE = "fate";

    /** An offer to choose: a reward, a level-up, a modifier. */
    public static final String OFFER = "offer";
    /** A spell card of the party's. */
    public static final String ABILITY = "ability";
    /** An item in the inventory's bag. */
    public static final String BAG_ITEM = "bag_item";

    /** "upgrades Thief": the hero a level-up goes to. */
    public static final String UPGRADES = "upgrades";
    /** "item", "hero", "curse". */
    public static final String KIND = "kind";
    public static final String TIER = "tier";
    /** What an item, offer or ability does. */
    public static final String EFFECT = "effect";
    /** An ability's mana cost. */
    public static final String COST = "cost";
    /** "caster defeated" on an ability. */
    public static final String CASTER = "caster";
    /** The item picked up to equip. */
    public static final String HELD = "held";
    /** "new", "must equip". */
    public static final String ITEM_FLAG = "item_flag";
    /** "targeted by Rat 1". */
    public static final String TARGETED_BY = "targeted_by";
    /** "2/3 mana stored". */
    public static final String MANA = "mana";

    /** A line of the party and enemies buffers: a unit's name and vitals. */
    public static final String ROSTER = Announcements.BUFFERS + ".roster";
    /** Ctrl+1: the focused unit's vitals. */
    public static final String GLANCE_VITALS = Announcements.GLANCES + ".vitals";
    /** Ctrl+2: who the focused unit targets and who targets it. */
    public static final String GLANCE_AIM = Announcements.GLANCES + ".aim";
    /** Ctrl+T: the turn, the rerolls and the mana. */
    public static final String GLANCE_TURN = Announcements.GLANCES + ".turn";
    /** Ctrl+I: what is coming at each hero. */
    public static final String GLANCE_INCOMING = Announcements.GLANCES + ".incoming";

    public static void declare(Announcements a) {
        a.readout(Announcements.READOUTS, COMBATANT, Arrays.asList(DIE_STATE, INVALID, AnnouncementKinds.LABEL,
                SIDE, AIM, HP, SHIELD, MARK, STATUSES, INCOMING, FATE));
        a.readout(Announcements.READOUTS, OFFER, Arrays.asList(AnnouncementKinds.LABEL, AnnouncementKinds.ROLE,
                UPGRADES, KIND, TIER, AnnouncementKinds.SELECTED, EFFECT));
        a.readout(Announcements.READOUTS, ABILITY, Arrays.asList(AnnouncementKinds.LABEL, AnnouncementKinds.ROLE,
                COST, EFFECT, AnnouncementKinds.SELECTED, CASTER));
        a.readout(Announcements.READOUTS, BAG_ITEM, Arrays.asList(AnnouncementKinds.LABEL, AnnouncementKinds.ROLE,
                HELD, TIER, ITEM_FLAG, EFFECT));

        a.readout(Announcements.BUFFERS, "roster", Arrays.asList(AnnouncementKinds.LABEL, HP, SHIELD, MARK,
                INCOMING, FATE));
        UnitLines.declare(a);
        a.readout(Announcements.GLANCES, "vitals", Arrays.asList(HP, SHIELD, MARK, INCOMING, FATE));
        a.readout(Announcements.GLANCES, "aim", Arrays.asList(AIM, TARGETED_BY));
        a.readout(Announcements.GLANCES, "turn", Arrays.asList(EventParts.TURN, EventParts.REROLLS, MANA));
        a.readout(Announcements.GLANCES, "incoming", Arrays.asList(AnnouncementKinds.LABEL, INCOMING, FATE));
        a.suffix(GLANCE_INCOMING, AnnouncementKinds.LABEL, ":");
    }

    /** A line of readout id from its parts' texts, as the player arranged them; null when nothing is left to say. */
    public static String line(String id, java.util.Map<String, String> parts) {
        return snd.module.settings.ModSettings.get().announcements.compose(id, parts);
    }

    /** The non-empty texts joined with ", ", or null. */
    static String join(String... texts) {
        StringBuilder sb = new StringBuilder();
        for (String t : texts) {
            if (t == null || t.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(t);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
