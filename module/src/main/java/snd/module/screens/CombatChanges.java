package snd.module.screens;

import java.util.ArrayList;
import java.util.List;

import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.fightLog.EntState;
import com.tann.dice.gameplay.fightLog.Snapshot;
import com.tann.dice.gameplay.trigger.personal.Personal;

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
     * with {@code first} (whoever was aimed at) leading; empty when nothing
     * spoken moved. Each is an event of its own. A side whose every living
     * member changed alike is said once: "all heroes 1".
     */
    public static List<String> lines(Snapshot before, Snapshot after, Ent first) {
        if (before == null || after == null || before == after) {
            return new ArrayList<String>(); // a skipped command shares its predecessor's snapshot
        }
        List<Ent> ents = new ArrayList<Ent>();
        List<String> changes = new ArrayList<String>();
        for (EntState post : after.getStates(null, null)) {
            Ent ent = post.getEnt();
            String change = describe(before.getState(ent), post);
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
            clauses.add(Loc.get("combat", "change.clause", "name", names.get(i), "parts", changes.get(i)));
        }
        return clauses;
    }

    // One clause for a side when all of its living changed, and all alike.
    private static void collapse(Snapshot before, boolean heroes, List<Ent> ents, List<String> names,
            List<String> changes) {
        List<Ent> living = before.getEntities(heroes, false);
        List<Integer> members = new ArrayList<Integer>();
        for (int i = 0; i < ents.size(); i++) {
            if (ents.get(i).isPlayer() != heroes) {
                continue;
            }
            String shared = changes.get(members.isEmpty() ? i : members.get(0));
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
     * What changed for one combatant, or null when nothing spoken did: what
     * the step did to them, then "defeated", "flees" or "returns". {@code pre}
     * is null for someone the earlier snapshot did not hold (a summon, a
     * reinforcement).
     */
    public static String describe(EntState pre, EntState post) {
        if (pre == null) {
            return Loc.get("combat", "change.joins");
        }
        List<String> all = new ArrayList<String>();
        String parts = parts(pre, post);
        if (parts != null) {
            all.add(parts);
        }
        if (!pre.isDead() && post.isDead()) {
            all.add(post.isFled() ? Loc.get("combat", "change.flees") : Loc.get("combat", "defeated"));
        } else if (pre.isDead() && !post.isDead()) {
            all.add(Loc.get("combat", "change.returns"));
        }
        return join(all, ", ");
    }

    /** What the step did to one combatant (hp lost, poison, healing, shields, statuses), or null. */
    public static String parts(EntState pre, EntState post) {
        List<String> parts = new ArrayList<String>();
        boolean returned = pre.isDead() && !post.isDead();

        // The per-turn counters say how the hp moved; across a turn boundary
        // they have reset (a negative delta) and only the hp delta is left.
        int hpLost = pre.getHp() - post.getHp();
        int blocked = Math.max(0, post.getDamageBlocked() - pre.getDamageBlocked());
        int poison = Math.min(Math.max(0, hpLost),
                Math.max(0, post.getPoisonDamageTaken(true) - pre.getPoisonDamageTaken(true)));
        if (hpLost - poison > 0) {
            parts.add(String.valueOf(hpLost - poison));
        }
        if (poison > 0) {
            parts.add(Loc.get("combat", "change.poison", "n", poison));
        }
        if (hpLost < 0 && !returned) {
            parts.add(GameText.t("Heal " + -hpLost));
        }
        // Shields spent on a block are the hit's, not a change of their own,
        // and shields lapsing with the turn say nothing in the game either.
        int shieldsGained = post.getShields() - pre.getShields() + blocked;
        if (shieldsGained > 0) {
            parts.add(GameText.t("Shield " + shieldsGained));
        }
        if (post.getMaxHp() != pre.getMaxHp()) {
            parts.add(Loc.get("combat", "change.max_hp", "n", post.getMaxHp()));
        }

        // Statuses by the game's own is-this-new test, whatever added them.
        if (!post.isDead()) {
            for (Personal p : post.getActivePersonals()) {
                if (p.hasImage() && isNew(p, pre.getActivePersonals())) {
                    parts.add(GameText.t(CombatScreen.statusName(p)).trim());
                }
            }
            // Gone means no status of its kind is left: "Poison 2" growing to
            // "Poison 3" is the new one arriving, not the old one ending. What
            // was lost is named without the turns it had left.
            for (Personal p : pre.getActivePersonals()) {
                if (p.hasImage() && Boolean.TRUE.equals(Personal.treatAsIncoming(p, post.getActivePersonals()))) {
                    parts.add(Loc.get("combat", "change.lost", "status",
                            GameText.t(CombatScreen.statusName(p, false)).trim()));
                }
            }
        }

        return join(parts, ", ");
    }

    private static boolean isNew(Personal p, List<Personal> others) {
        Boolean incoming = Personal.treatAsIncoming(p, others);
        return incoming == null || incoming;
    }

    private static String join(List<String> parts, String separator) {
        if (parts.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(part);
        }
        return sb.toString();
    }
}
