package snd.module;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.gameplay.fightLog.Snapshot;
import com.tann.dice.gameplay.fightLog.command.Command;
import com.tann.dice.gameplay.fightLog.command.TargetableCommand;
import com.tann.dice.screens.dungeon.DungeonScreen;

import snd.contracts.SndLog;
import snd.core.buffers.EventLog;
import snd.core.loc.Loc;
import snd.module.screens.CombatChanges;

/**
 * Speaks what every resolved step of a fight did — the player's dice and
 * abilities, each monster's attack, and the turn's own ticks (poison, regen,
 * start- and end-of-turn triggers) — where the game shows only a repaint, a
 * flash or a death animation. The steps are the FightLog's commands, read
 * from its own list (private pastCommands, reflectively); what one did is the
 * difference between the log's own snapshots before and after it
 * ({@link CombatChanges}), so an announcement can never disagree with the
 * model and no effect needs to be known by name.
 *
 * <p>A player's command speaks as soon as it is in the log: it resolves in
 * the Present at once. Anything else speaks when its animation lands
 * ({@code Command.getImpacted}), in order, which paces the enemy turn the way
 * the game paces it for the eye: one attack at a time. When a turn is over the
 * log archives its commands (pastCommands into commandHistory) in the very
 * tick the last one finishes, so the archive's new tail is read too: a step
 * that landed and was archived between two of our ticks is still spoken.
 * Polled from the module tick.
 */
final class CommandWatcher {
    private final EventLog events;
    // Identity of the commands already spoken; pruned to the live list every
    // tick, so it holds nothing the FightLog has let go of.
    private final Set<Command> spoken = Collections.newSetFromMap(new IdentityHashMap<Command, Boolean>());
    private FightLog watched;
    private int archived; // how much of the log's commandHistory has been gone through

    CommandWatcher(EventLog events) {
        this.events = events;
    }

    void tick() {
        DungeonScreen ds = DungeonScreen.get();
        FightLog log = ds != null && com.tann.dice.Main.getCurrentScreen() == ds ? ds.getFightLog() : null;
        List<Command> commands = log != null ? commandList(log, "pastCommands") : null;
        List<Command> history = log != null ? commandList(log, "commandHistory") : null;
        if (commands == null || history == null) {
            watched = null;
            spoken.clear();
            return;
        }
        if (log != watched || history.size() < archived) {
            // A fight met mid-way (a loaded save, a module reload) or a new
            // fight in the same log: what is already there happened before
            // anyone was listening.
            watched = log;
            archived = history.size();
            spoken.clear();
            spoken.addAll(commands);
            return;
        }
        // Archived since the last tick: finished, whatever their animation says.
        for (Command command : history.subList(archived, history.size())) {
            speak(log, command);
        }
        archived = history.size();
        spoken.retainAll(commands);
        for (Command command : commands) {
            if (!spoken.contains(command) && !players(command) && !command.getImpacted()) {
                break; // not landed yet; the ones after it wait their turn
            }
            speak(log, command);
        }
    }

    private void speak(FightLog log, Command command) {
        if (!spoken.add(command)) {
            return;
        }
        String line = lineFor(log, command, players(command));
        if (line != null) {
            events.say(line, false);
        }
    }

    // A spell has no source; nothing else without one is the player's doing.
    private static boolean players(Command command) {
        return command.getSource() == null ? command instanceof TargetableCommand : command.getSource().isPlayer();
    }

    private static String lineFor(FightLog log, Command command, boolean players) {
        Snapshot before = log.getSnapshotBefore(command);
        Snapshot after = log.getSnapshotAfter(command);
        Ent target = command instanceof TargetableCommand ? ((TargetableCommand) command).target : null;
        // Everyone the step touched, the one aimed at first: a cleave's other
        // victims, the attacker's own pain or shield, a kill's on-death fallout.
        String changes = CombatChanges.line(before, after, target);

        if (players && command instanceof TargetableCommand) {
            // "Bandit 1 2, defeated" — the player knows who acted and what
            // with, so the outcome is the whole line. Target-conditional
            // keywords (engage, cruel, wham...) multiply damage at
            // resolution, so the side's description understates exactly when
            // it matters; the diff is the truth. With nothing spoken moved (a
            // buff with no icon, a hit a shield took) the side's own
            // description answers the press.
            if (changes != null) {
                return changes;
            }
            String eff = GameText.t(((TargetableCommand) command).targetable.getDerivedEffects().describe(false));
            return target == null ? eff : Loc.get("combat", "applied", "eff", eff, "target", GameUi.entName(target));
        }
        // Null for a skipped, wasted or wholly blocked step: nothing spoken moved.
        return changes != null && command.getSource() != null
                ? Loc.get("combat", "change.source", "source", GameUi.entName(command.getSource()), "changes", changes)
                : changes;
    }

    // FightLog keeps its command lists private; reading them beats
    // re-deriving command state from panels.
    private static final java.util.Map<String, Field> FIELDS = new java.util.HashMap<String, Field>();

    static List<Command> pastCommands(FightLog fightLog) {
        return commandList(fightLog, "pastCommands");
    }

    @SuppressWarnings("unchecked")
    private static List<Command> commandList(FightLog fightLog, String name) {
        try {
            Field f = FIELDS.get(name);
            if (f == null) {
                f = FightLog.class.getDeclaredField(name);
                f.setAccessible(true);
                FIELDS.put(name, f);
            }
            return (List<Command>) f.get(fightLog);
        } catch (Throwable t) {
            SndLog.error("failed to read FightLog." + name, t);
            return null;
        }
    }
}
