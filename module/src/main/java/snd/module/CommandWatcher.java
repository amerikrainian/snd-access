package snd.module;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.fightLog.EntState;
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
        EntState pre = target != null && before != null ? before.getState(target) : null;
        EntState post = target != null && after != null ? after.getState(target) : null;
        if (pre == null || post == null) {
            target = null; // not held by both snapshots (summoned by this very step): an "other"
        }
        // Everyone else the step touched: a cleave's other victims, the
        // attacker's own pain or shield, a kill's on-death fallout.
        List<String> sentences = CombatChanges.clauses(before, after, target);

        if (players && command instanceof TargetableCommand) {
            sentences.add(0, playersHead((TargetableCommand) command, target, pre, post));
            return join(sentences);
        }
        String source = command.getSource() != null ? GameUi.entName(command.getSource()) : null;
        // Where a hit leaves its target is what it means to the player.
        String hit = target != null ? CombatChanges.describe(pre, post, true) : null;
        if (hit != null) {
            sentences.add(0, source == null
                    ? Loc.get("combat", "change.clause", "name", GameUi.entName(target), "parts", hit)
                    : Loc.get("combat", "change.by", "source", source, "target", GameUi.entName(target), "parts", hit));
        } else if (source != null && !sentences.isEmpty()) {
            return Loc.get("combat", "change.source", "source", source, "changes", join(sentences));
        }
        return join(sentences); // null for a skipped or wasted step: nothing a panel shows moved
    }

    // "2 damage, on Bandit 1, defeated" — the player knows who acted and what
    // with, so the outcome leads. Target-conditional keywords (engage, cruel,
    // wham...) multiply damage at resolution, so the side's description
    // understates exactly when it matters; the diff is the truth. With
    // nothing measurable moved (a buff with no icon, a group effect) the
    // side's own description stands in.
    private static String playersHead(TargetableCommand command, Ent target, EntState pre, EntState post) {
        String eff = target != null ? CombatChanges.parts(pre, post) : null;
        if (eff == null) {
            eff = GameText.t(command.targetable.getDerivedEffects().describe(false));
        }
        if (target == null) {
            return eff;
        }
        String text = Loc.get("combat", "applied", "eff", eff, "target", GameUi.entName(target));
        // A kill vanishes from the enemy column with only a death animation.
        String ending = CombatChanges.ending(pre, post, false);
        return ending != null ? text + ", " + ending : text;
    }

    private static String join(List<String> sentences) {
        if (sentences.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String sentence : sentences) {
            if (sb.length() > 0) {
                sb.append(". ");
            }
            sb.append(sentence);
        }
        return sb.toString();
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
