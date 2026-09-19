package snd.module;

import java.util.List;

import com.tann.dice.gameplay.effect.targetable.Targetable;
import com.tann.dice.gameplay.fightLog.command.Command;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.gameplay.TargetingPhase;
import com.tann.dice.screens.dungeon.DungeonScreen;

import snd.contracts.speech.SpeechPipeline;
import snd.core.buffers.EventLog;
import snd.core.loc.Loc;

/**
 * Speaks the targeting loop's own state changes, whichever input path caused
 * them (a digit key, a click, or the navigator): selecting a die or ability,
 * deselecting it, and an undo — which otherwise only repaints the previews.
 * What an applied command DID is the {@link CommandWatcher}'s to say. Polled
 * from the module tick.
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

        List<Command> commands = CommandWatcher.pastCommands(ds.getFightLog());
        int count = commands != null ? commands.size() : 0;
        boolean applied = lastCommandCount >= 0 && count > lastCommandCount;
        if (lastCommandCount >= 0 && count < lastCommandCount) {
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
                // Applying a command deselects too; its outcome says enough.
                speech.speak(Loc.get("combat", "deselected"), true);
            }
            lastSelected = selected;
        }
    }
}
