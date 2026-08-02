package snd.module;

import java.util.List;

import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.content.ent.die.Die;
import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.gameplay.PlayerRollingPhase;
import com.tann.dice.screens.dungeon.DungeonScreen;

import snd.core.loc.Loc;
import snd.core.speech.SpeechPipeline;
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
    private boolean wasRolling;
    private final java.util.WeakHashMap<Ent, Boolean> lockStates =
            new java.util.WeakHashMap<Ent, Boolean>();

    DiceWatcher(SpeechPipeline speech) {
        this.speech = speech;
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
            speech.speak(rollResults(ds, heroes), false);
        }
        wasRolling = rolling;

        for (Ent h : heroes) {
            boolean locked = h.getDie().getState().isLockedOrLocking();
            Boolean prev = lockStates.put(h, locked);
            if (prev != null && prev != locked) {
                // A direct response to the player's toggle — supersedes.
                speech.speak(GameText.t(h.getName(true)) + " "
                        + (locked ? GameText.t("locked") : Loc.get("combat", "unlocked")), true);
            }
        }
    }

    private static String rollResults(DungeonScreen ds, List<Ent> heroes) {
        StringBuilder sb = new StringBuilder();
        for (Ent h : heroes) {
            if (h.getDie().getState().isLockedOrLocking()) {
                continue; // sat out the roll
            }
            if (sb.length() > 0) {
                sb.append(". ");
            }
            sb.append(GameText.t(h.getName(true))).append(": ")
                    .append(CombatScreen.currentSideText(h));
        }
        if (sb.length() > 0) {
            sb.append(". ");
        }
        sb.append(GameText.t("Reroll")).append(' ').append(CombatScreen.rollCounter(ds));
        return sb.toString();
    }
}
