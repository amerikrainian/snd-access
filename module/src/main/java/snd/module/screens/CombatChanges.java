package snd.module.screens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.fightLog.EntState;
import com.tann.dice.gameplay.fightLog.Snapshot;
import com.tann.dice.gameplay.trigger.personal.Personal;

import snd.core.graph.AnnouncementKinds;
import snd.core.loc.Loc;
import snd.module.GameText;
import snd.module.GameUi;

/**
 * What a step of the fight did, read as the difference between the FightLog's
 * own snapshots before and after it — for every combatant, not just the one
 * that was aimed at. Nothing here knows which effect caused a change: it
 * reads what every mechanic must move to matter (hp, the poison counter,
 * death and flight, who is on the field, the statuses a panel draws), so a
 * cleave's second victim, a pain side's self-damage, a thorns reflect, a
 * summon, or a modded effect nobody has met all speak. Tersely: the hp a
 * unit lost is a bare number after its name, damage a shield took is not an
 * event, and where a hit leaves its target is the hp glance's to say.
 */
public final class CombatChanges {
    private CombatChanges() {
    }

    /**
     * The step as a line per combatant that changed — "Brute 1", "Lazy 2" —
     * with {@code first} (whoever was aimed at) leading, and source (who
     * acted, or null) opening the first; empty when nothing spoken moved.
     * Each is an event of its own ({@link EventParts#OUTCOME}). A side whose
     * every living member changed alike is said once: "all heroes 1".
     */
    public static List<String> lines(Snapshot before, Snapshot after, Ent first, String source) {
        if (before == null || after == null || before == after) {
            return new ArrayList<String>(); // a skipped command shares its predecessor's snapshot
        }
        List<Ent> ents = new ArrayList<Ent>();
        List<Map<String, String>> changes = new ArrayList<Map<String, String>>();
        for (EntState post : after.getStates(null, null)) {
            Ent ent = post.getEnt();
            Map<String, String> change = describe(before.getState(ent), post);
            if (change != null) {
                int at = ent == first ? 0 : ents.size();
                ents.add(at, ent);
                changes.add(at, change);
            }
        }
        List<String> names = new ArrayList<String>();
        for (Ent ent : ents) {
            names.add(GameUi.entName(ent));
        }
        collapse(before, true, ents, names, changes);
        collapse(before, false, ents, names, changes);

        List<String> clauses = new ArrayList<String>();
        for (int i = 0; i < names.size(); i++) {
            Map<String, String> parts = new HashMap<String, String>(changes.get(i));
            parts.put(AnnouncementKinds.LABEL, names.get(i));
            if (clauses.isEmpty() && source != null) {
                parts.put(EventParts.SOURCE, source);
            }
            String clause = EventParts.line(EventParts.OUTCOME, parts);
            if (clause != null) {
                clauses.add(clause);
            }
        }
        return clauses;
    }

    // One clause for a side when all of its living changed, and all alike.
    private static void collapse(Snapshot before, boolean heroes, List<Ent> ents, List<String> names,
            List<Map<String, String>> changes) {
        List<Ent> living = before.getEntities(heroes, false);
        List<Integer> members = new ArrayList<Integer>();
        for (int i = 0; i < ents.size(); i++) {
            if (ents.get(i).isPlayer() != heroes) {
                continue;
            }
            Map<String, String> shared = changes.get(members.isEmpty() ? i : members.get(0));
            if (!living.contains(ents.get(i)) || !changes.get(i).equals(shared)) {
                return;
            }
            members.add(i);
        }
        if (members.size() < 2 || members.size() != living.size()) {
            return;
        }
        names.set(members.get(0), Loc.get("combat", heroes ? "change.all_heroes" : "change.all_enemies"));
        for (int m = members.size() - 1; m > 0; m--) {
            int i = members.get(m);
            ents.remove(i);
            names.remove(i);
            changes.remove(i);
        }
    }

    /**
     * What changed for one combatant, by part, or null when nothing spoken
     * did: what the step did to them, then "defeated", "flees", "returns" or
     * "joins the fight" as its fate. {@code pre} is null for someone the
     * earlier snapshot did not hold (a summon, a reinforcement).
     */
    public static Map<String, String> describe(EntState pre, EntState post) {
        Map<String, String> parts = new HashMap<String, String>();
        if (pre == null) {
            parts.put(UnitParts.FATE, Loc.get("combat", "change.joins"));
            return parts;
        }
        if (pre.isDead() && post.isDead()) {
            // The game keeps ticking a unit killed this turn at the turn's end
            // (poison, regen) though its panel is gone; none of that is news.
            return null;
        }
        parts(pre, post, parts);
        if (!pre.isDead() && post.isDead()) {
            parts.put(UnitParts.FATE, post.isFled() ? Loc.get("combat", "change.flees") : Loc.get("combat", "defeated"));
        } else if (pre.isDead() && !post.isDead()) {
            parts.put(UnitParts.FATE, Loc.get("combat", "change.returns"));
        }
        return parts.isEmpty() ? null : parts;
    }

    // What the step did to one combatant (hp lost, poison, healing, shields, statuses), by part.
    private static void parts(EntState pre, EntState post, Map<String, String> parts) {
        boolean returned = pre.isDead() && !post.isDead();

        // The per-turn counters say how the hp moved; across a turn boundary
        // they have reset (a negative delta) and only the hp delta is left.
        int hpLost = pre.getHp() - post.getHp();
        int blocked = Math.max(0, post.getDamageBlocked() - pre.getDamageBlocked());
        int poison = Math.min(Math.max(0, hpLost),
                Math.max(0, post.getPoisonDamageTaken(true) - pre.getPoisonDamageTaken(true)));
        if (hpLost - poison > 0) {
            parts.put(EventParts.DAMAGE, String.valueOf(hpLost - poison));
        }
        if (poison > 0) {
            parts.put(EventParts.POISON, Loc.get("combat", "change.poison", "n", poison));
        }
        if (hpLost < 0 && !returned) {
            parts.put(EventParts.HEAL, GameText.t("Heal " + -hpLost));
        }
        // Shields spent on a block are the hit's, not a change of their own,
        // and shields lapsing with the turn say nothing in the game either.
        int shieldsGained = post.getShields() - pre.getShields() + blocked;
        if (shieldsGained > 0) {
            parts.put(UnitParts.SHIELD, GameText.t("Shield " + shieldsGained));
        }
        if (post.getMaxHp() != pre.getMaxHp()) {
            parts.put(EventParts.MAX_HP, Loc.get("combat", "change.max_hp", "n", post.getMaxHp()));
        }

        // Statuses by the game's own is-this-new test, whatever added them.
        if (!post.isDead()) {
            List<String> gained = new ArrayList<String>();
            for (Personal p : post.getActivePersonals()) {
                if (p.hasImage() && isNew(p, pre.getActivePersonals())) {
                    gained.add(GameText.t(CombatScreen.statusName(p)).trim());
                }
            }
            // Gone means no status of its kind is left: "Poison 2" growing to
            // "Poison 3" is the new one arriving, not the old one ending. What
            // was lost is named without the turns it had left.
            List<String> lost = new ArrayList<String>();
            for (Personal p : pre.getActivePersonals()) {
                if (p.hasImage() && Boolean.TRUE.equals(Personal.treatAsIncoming(p, post.getActivePersonals()))) {
                    lost.add(Loc.get("combat", "change.lost", "status",
                            GameText.t(CombatScreen.statusName(p, false)).trim()));
                }
            }
            putJoined(parts, UnitParts.STATUSES, gained);
            putJoined(parts, EventParts.LOST, lost);
        }
    }

    private static boolean isNew(Personal p, List<Personal> others) {
        Boolean incoming = Personal.treatAsIncoming(p, others);
        return incoming == null || incoming;
    }

    private static void putJoined(Map<String, String> parts, String part, List<String> texts) {
        if (texts.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String t : texts) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(t);
        }
        parts.put(part, sb.toString());
    }
}
