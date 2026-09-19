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
 * reads what every mechanic must move to matter (hp, the blocked-damage and
 * poison counters, death and flight, who is on the field, the statuses a
 * panel draws), so a cleave's second victim, a pain side's self-damage, a
 * thorns reflect, a summon, or a modded effect nobody has met all speak.
 */
public final class CombatChanges {
    private CombatChanges() {
    }

    /** One clause per combatant that changed, "name: parts"; {@code skip} is left out (its change is said elsewhere). */
    public static List<String> clauses(Snapshot before, Snapshot after, Ent skip) {
        List<String> clauses = new ArrayList<String>();
        if (before == null || after == null || before == after) {
            return clauses; // a skipped command shares its predecessor's snapshot
        }
        for (EntState post : after.getStates(null, null)) {
            Ent ent = post.getEnt();
            if (ent == skip) {
                continue;
            }
            String parts = describe(before.getState(ent), post, true);
            if (parts != null) {
                clauses.add(Loc.get("combat", "change.clause", "name", GameUi.entName(ent), "parts", parts));
            }
        }
        return clauses;
    }

    /**
     * What changed for one combatant and how that leaves them, or null when
     * nothing a panel shows did. {@code pre} is null for someone the earlier
     * snapshot did not hold (a summon, a reinforcement).
     */
    public static String describe(EntState pre, EntState post, boolean standing) {
        if (pre == null) {
            return Loc.get("combat", "change.joins");
        }
        List<String> all = new ArrayList<String>();
        String parts = parts(pre, post);
        if (parts != null) {
            all.add(parts);
        }
        String ending = ending(pre, post, standing);
        if (ending != null) {
            all.add(ending);
        }
        return join(all);
    }

    /**
     * How the change leaves them: "defeated", "flees", back from the dead, or
     * — with {@code standing} — the hp they are left on ("3 of 9 hp"), since
     * what a hit means is what is left. Null when none applies.
     */
    public static String ending(EntState pre, EntState post, boolean standing) {
        if (!pre.isDead() && post.isDead()) {
            return post.isFled() ? Loc.get("combat", "change.flees") : Loc.get("combat", "defeated");
        }
        if (pre.isDead() && !post.isDead()) {
            return Loc.get("combat", "change.returns", "hp", hpText(post));
        }
        boolean moved = pre.getHp() != post.getHp() || pre.getMaxHp() != post.getMaxHp();
        return standing && moved && !post.isDead() ? hpText(post) : null;
    }

    /** What the step did to one combatant (damage, blocking, poison, healing, shields, statuses), or null. */
    public static String parts(EntState pre, EntState post) {
        List<String> parts = new ArrayList<String>();
        boolean returned = pre.isDead() && !post.isDead();

        // The per-turn counters say how the hp moved; across a turn boundary
        // they have reset (a negative delta) and only the hp delta is left.
        int hpLost = pre.getHp() - post.getHp();
        int blocked = Math.max(0, post.getDamageBlocked() - pre.getDamageBlocked());
        int poison = Math.max(0, post.getPoisonDamageTaken(true) - pre.getPoisonDamageTaken(true));
        if (hpLost > 0 || blocked > 0) {
            int lost = Math.max(0, hpLost);
            int struck = Math.max(0, lost - poison) + blocked;
            if (struck > 0) {
                String damage = GameText.t(struck + " damage");
                if (blocked > 0) {
                    damage += ", " + (lost - poison > 0 ? Loc.get("combat", "blocked_n", "n", blocked)
                            : Loc.get("combat", "blocked_all"));
                }
                parts.add(damage);
            }
            if (Math.min(poison, lost) > 0) {
                parts.add(Loc.get("combat", "change.poison", "n", Math.min(poison, lost)));
            }
        } else if (hpLost < 0 && !returned) {
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
            // "Poison 3" is the new one arriving, not the old one ending.
            for (Personal p : pre.getActivePersonals()) {
                if (p.hasImage() && Boolean.TRUE.equals(Personal.treatAsIncoming(p, post.getActivePersonals()))) {
                    parts.add(Loc.get("combat", "change.ends", "status", GameText.t(CombatScreen.statusName(p)).trim()));
                }
            }
        }

        return join(parts);
    }

    private static boolean isNew(Personal p, List<Personal> others) {
        Boolean incoming = Personal.treatAsIncoming(p, others);
        return incoming == null || incoming;
    }

    private static String hpText(EntState state) {
        return state.getHp() >= state.getMaxHp() ? Loc.get("combat", "hp_full", "hp", state.getHp())
                : Loc.get("combat", "hp", "hp", state.getHp(), "max", state.getMaxHp());
    }

    private static String join(List<String> parts) {
        if (parts.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(part);
        }
        return sb.toString();
    }
}
