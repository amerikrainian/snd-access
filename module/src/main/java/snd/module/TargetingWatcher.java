package snd.module;

import java.lang.reflect.Field;
import java.util.List;

import com.tann.dice.gameplay.effect.targetable.Targetable;
import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.gameplay.fightLog.command.Command;
import com.tann.dice.gameplay.fightLog.command.TargetableCommand;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.gameplay.TargetingPhase;
import com.tann.dice.screens.dungeon.DungeonScreen;

import snd.core.SndLog;
import snd.core.loc.Loc;
import snd.core.speech.SpeechPipeline;
import snd.module.screens.CombatScreen;

/**
 * Speaks the targeting loop's state changes, whichever input path caused them
 * (a digit key, a click, or the navigator): selecting a die or ability reads
 * its calculated effect (the pushed Explanel's content), deselecting says so,
 * and every applied command reads as "effect, on target" — the visual is only
 * a damage-preview repaint. Commands are read from the FightLog's own list
 * (its private pastCommands, reflectively), so the announcement can never
 * disagree with the model. Polled from the module tick.
 */
final class TargetingWatcher {
    private final SpeechPipeline speech;
    private Targetable lastSelected;
    private int lastCommandCount = -1;

    TargetingWatcher(SpeechPipeline speech) {
        this.speech = speech;
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
                speech.speak(appliedText((TargetableCommand) last), false);
            }
        }
        lastCommandCount = count;

        Targetable selected = ds.targetingManager.getSelectedTargetable();
        if (selected != lastSelected) {
            if (selected != null) {
                speech.speak(GameText.t(selected.getDerivedEffects().describe(false))
                        + ", " + Loc.get("ui", "state.selected"), true);
            } else if (!applied) {
                speech.speak(Loc.get("combat", "deselected"), true);
            }
            lastSelected = selected;
        }
    }

    private static String appliedText(TargetableCommand command) {
        String eff = GameText.t(command.targetable.getDerivedEffects().describe(false));
        if (command.target == null) {
            return eff;
        }
        return Loc.get("combat", "applied",
                "eff", eff, "target", GameText.t(command.target.getName(true)));
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
