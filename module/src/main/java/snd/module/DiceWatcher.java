package snd.module;

import java.util.List;

import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.content.ent.die.Die;
import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.gameplay.PlayerRollingPhase;
import com.tann.dice.screens.dungeon.DungeonScreen;

import snd.core.loc.Loc;
import snd.core.buffers.EventLog;
import snd.contracts.speech.SpeechPipeline;
import snd.module.screens.CombatScreen;

/**
 * Speaks the rolling phase's die changes, which are all physical-only
 * visuals. When the last tumbling die settles, every die that took part in
 * the roll reads its landed side, followed by the reroll counter ("Reroll
 * 2/3" — the counter the visual UI draws on the button, whose red-at-zero
 * label is otherwise the only exhaustion cue); locked dice sat out the roll
 * and stay silent. A die's lock toggling (Enter, a digit key, or a click —
 * the visual is the die sliding to its panel) speaks "name locked/unlocked".
 * Polled from the module tick.
 */
final class DiceWatcher {
    private final SpeechPipeline speech;
    private final EventLog events;
    private boolean wasRolling;
    private final java.util.WeakHashMap<Ent, Boolean> lockStates =
            new java.util.WeakHashMap<Ent, Boolean>();

    DiceWatcher(SpeechPipeline speech, EventLog events) {
        this.speech = speech;
        this.events = events;
    }

    void tick() {
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null || ds.getFightLog() == null
                || !(PhaseManager.get().getPhase() instanceof PlayerRollingPhase)) {
            // Off-phase transitions (confirm locks every die as it exits) stay silent.
            wasRolling = false;
            lockStates.clear();
            return;
        }
        List<Ent> heroes = ds.getFightLog().getSnapshot(FightLog.Temporality.Present)
                .getEntities(true, false);
        boolean rolling = false;
        for (Ent h : heroes) {
            if (h.getDie().getState() == Die.DieState.Rolling) {
                rolling = true;
                break;
            }
        }
        if (wasRolling && !rolling) {
            for (String result : rollResults(ds, heroes)) {
                events.say(result, false);
            }
        }
        wasRolling = rolling;

        for (Ent h : heroes) {
            boolean locked = h.getDie().getState().isLockedOrLocking();
            Boolean prev = lockStates.put(h, locked);
            if (prev != null && prev != locked) {
                // A direct response to the player's toggle — supersedes.
                speech.speak(GameUi.entName(h) + " "
                        + (locked ? GameText.t("locked") : Loc.get("combat", "unlocked")), true);
            }
        }
    }

    // A line per die that rolled, then the rerolls left — each an event of its own.
    private static List<String> rollResults(DungeonScreen ds, List<Ent> heroes) {
        List<String> lines = new java.util.ArrayList<String>();
        for (Ent h : heroes) {
            if (h.getDie().getState().isLockedOrLocking()) {
                continue; // sat out the roll
            }
            lines.add(GameUi.entName(h) + ": " + CombatScreen.currentSideText(h));
        }
        lines.add(GameText.t("Reroll") + ' ' + CombatScreen.rollCounter(ds));
        return lines;
    }
}
