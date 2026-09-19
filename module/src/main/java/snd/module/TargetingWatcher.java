package snd.module;

import java.lang.reflect.Field;
import java.util.List;

import com.tann.dice.gameplay.effect.targetable.Targetable;
import com.tann.dice.gameplay.fightLog.EntState;
import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.gameplay.fightLog.Snapshot;
import com.tann.dice.gameplay.fightLog.command.Command;
import com.tann.dice.gameplay.fightLog.command.TargetableCommand;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.gameplay.TargetingPhase;
import com.tann.dice.gameplay.trigger.personal.Personal;
import com.tann.dice.screens.dungeon.DungeonScreen;

import snd.contracts.SndLog;
import snd.core.loc.Loc;
import snd.core.buffers.EventLog;
import snd.contracts.speech.SpeechPipeline;
import snd.module.screens.CombatScreen;

/**
 * Speaks the targeting loop's state changes, whichever input path caused them
 * (a digit key, a click, or the navigator): selecting a die or ability reads
 * its calculated effect (the pushed Explanel's content), deselecting says so,
 * and every applied command reads as "outcome, on target" — the visual is only
 * a damage-preview repaint. Commands are read from the FightLog's own list
 * (its private pastCommands, reflectively), and the outcome is diffed from the
 * command's own before/after snapshots, so the announcement can never disagree
 * with the model. Polled from the module tick.
 */
final class TargetingWatcher {
    private final SpeechPipeline speech;
    private final EventLog events;
    private Targetable lastSelected;
    private int lastCommandCount = -1;

    TargetingWatcher(SpeechPipeline speech, EventLog events) {
        this.speech = speech;
        this.events = events;
    }

    void tick() {
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null || ds.getFightLog() == null
                || !(PhaseManager.get().getPhase() instanceof TargetingPhase)) {
            lastSelected = null;
            lastCommandCount = -1;
            return;
        }

        List<Command> commands = pastCommands(ds.getFightLog());
        int count = commands != null ? commands.size() : 0;
        boolean applied = lastCommandCount >= 0 && count > lastCommandCount;
        if (applied) {
            Command last = commands.get(count - 1);
            if (last instanceof TargetableCommand) {
                events.say(appliedText(ds, (TargetableCommand) last), false);
            }
        } else if (lastCommandCount >= 0 && count < lastCommandCount) {
            // The revert repaints previews silently otherwise.
            events.say(GameText.t("Undo"), true);
        }
        lastCommandCount = count;

        Targetable selected = ds.targetingManager.getSelectedTargetable();
        if (selected != lastSelected) {
            if (selected != null) {
                // Just the word: the player scrolled (or digit-keyed) the die
                // to select it and already heard its side.
                speech.speak(Loc.get("ui", "state.selected"), true);
            } else if (!applied) {
                speech.speak(Loc.get("combat", "deselected"), true);
            }
            lastSelected = selected;
        }
    }

    private static String appliedText(DungeonScreen ds, TargetableCommand command) {
        String eff = command.target != null ? outcomeText(ds.getFightLog(), command) : null;
        if (eff == null) {
            // The side's own description — right for everything that leaves
            // the target's hp and shields alone (buffs, group effects).
            eff = GameText.t(command.targetable.getDerivedEffects().describe(false));
        }
        if (command.target == null) {
            return eff;
        }
        String text = Loc.get("combat", "applied",
                "eff", eff, "target", GameUi.entName(command.target));
        // Player damage resolves in the Present immediately — a kill vanishes
        // from the enemy column with only a death animation to show for it.
        EntState after = ds.getFightLog().getState(FightLog.Temporality.Present, command.target);
        if (after != null && after.isDead()) {
            text += ", " + Loc.get("combat", "defeated");
        }
        return text;
    }

    /**
     * What the command actually did to the target, diffed across the
     * FightLog's own before/after snapshots. Target-conditional keywords
     * (engage, cruel, wham...) multiply damage at resolution, so the side's
     * description understates exactly when it matters; hp and shield deltas
     * are the truth. Composed the way EffType.describe composes ("4 damage",
     * "Heal 4"), so the game's #{n}-keyed translation applies. Null when
     * neither hp nor shields moved.
     */
    private static String outcomeText(FightLog fightLog, TargetableCommand command) {
        Snapshot before = fightLog.getSnapshotBefore(command);
        Snapshot after = fightLog.getSnapshotAfter(command);
        // Snapshot.getState is null for entities the snapshot doesn't hold,
        // e.g. a target summoned mid-fight.
        EntState pre = before != null ? before.getState(command.target) : null;
        EntState post = after != null ? after.getState(command.target) : null;
        if (pre == null || post == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int hpLost = pre.getHp() - post.getHp();
        int shieldsLost = pre.getShields() - post.getShields();
        if (hpLost > 0 || shieldsLost > 0) {
            int dealt = Math.max(0, hpLost) + Math.max(0, shieldsLost);
            sb.append(GameText.t(dealt + " damage"));
            if (shieldsLost > 0) {
                sb.append(", ").append(hpLost > 0
                        ? Loc.get("combat", "blocked_n", "n", shieldsLost)
                        : Loc.get("combat", "blocked_all"));
            }
        } else {
            if (hpLost < 0) {
                sb.append(GameText.t("Heal " + -hpLost));
            }
            if (shieldsLost < 0) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(GameText.t("Shield " + -shieldsLost));
            }
        }
        // Statuses the command put on the target (a rider's poison, a debuff
        // side's wither) — the game's own incoming test against the
        // before-state finds them, whatever mechanic added them.
        for (Personal p : post.getActivePersonals()) {
            if (!p.hasImage()) {
                continue; // invisible mechanics don't show on the panel either
            }
            Boolean gained = Personal.treatAsIncoming(p, pre.getActivePersonals());
            if (gained != null && !gained) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(GameText.t(CombatScreen.statusName(p)));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // FightLog keeps its command list private; reading it beats re-deriving
    // command state from panels.
    private static Field pastCommandsField;

    @SuppressWarnings("unchecked")
    private static List<Command> pastCommands(FightLog fightLog) {
        try {
            if (pastCommandsField == null) {
                Field f = FightLog.class.getDeclaredField("pastCommands");
                f.setAccessible(true);
                pastCommandsField = f;
            }
            return (List<Command>) pastCommandsField.get(fightLog);
        } catch (Throwable t) {
            SndLog.error("failed to read FightLog.pastCommands", t);
            return null;
        }
    }
}
