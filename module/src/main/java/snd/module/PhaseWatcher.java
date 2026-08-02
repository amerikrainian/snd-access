package snd.module;

import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.gameplay.phase.Phase;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.endPhase.runEnd.RunEndPhase;
import com.tann.dice.gameplay.phase.gameplay.DamagePhase;
import com.tann.dice.gameplay.phase.gameplay.EnemyRollingPhase;
import com.tann.dice.gameplay.phase.gameplay.PlayerRollingPhase;
import com.tann.dice.gameplay.phase.gameplay.SurrenderPhase;
import com.tann.dice.gameplay.phase.gameplay.TargetingPhase;
import com.tann.dice.gameplay.phase.levelEndPhase.LevelEndPhase;
import com.tann.dice.screens.dungeon.DungeonScreen;

import snd.core.loc.Loc;
import snd.core.speech.SpeechPipeline;

/**
 * Speaks the combat turn structure: the current phase of the
 * PhaseManager stack as it changes, stamped with the turn number at the start
 * of each round and the fight number on a fight's first round. Only the
 * gameplay/animation phases speak here — decision phases (ChoicePhase and the
 * other reward dialogs) have their own screens with their own announcements,
 * and unknown phases stay silent rather than reading class names. Polled from
 * the module tick; the previous phase is remembered by identity only, for the
 * change diff.
 */
final class PhaseWatcher {
    private final SpeechPipeline speech;
    private Phase lastPhase;
    // The last spoken fight/turn stamps. A fight's first announced phase can
    // be any of the in-combat phases (the opening enemy roll may resolve
    // beneath a ChoicePhase and never surface), so the stamp rides whichever
    // in-combat phase first speaks after the value changes.
    private String lastFight;
    private int lastTurn = -1;

    PhaseWatcher(SpeechPipeline speech) {
        this.speech = speech;
    }

    void tick() {
        Phase p = PhaseManager.get().getPhase();
        if (p == lastPhase) {
            return;
        }
        lastPhase = p;
        String line = lineFor(p);
        if (line != null) {
            speech.speak(line, false);
        }
    }

    private String lineFor(Phase p) {
        if (p instanceof EnemyRollingPhase) {
            return stamped(Loc.get("combat", "phase.enemy_rolling"));
        }
        if (p instanceof PlayerRollingPhase) {
            return stamped(Loc.get("combat", "phase.player_rolling"));
        }
        if (p instanceof TargetingPhase) {
            return stamped(Loc.get("combat", "phase.targeting"));
        }
        if (p instanceof DamagePhase) {
            return Loc.get("combat", "phase.damage");
        }
        if (p instanceof SurrenderPhase) {
            return Loc.get("combat", "phase.surrender");
        }
        if (p instanceof LevelEndPhase) {
            resetStamps();
            return Loc.get("combat", "phase.level_end");
        }
        if (p instanceof RunEndPhase) {
            resetStamps();
            return Loc.get("combat", "phase.run_end");
        }
        return null;
    }

    private void resetStamps() {
        lastFight = null;
        lastTurn = -1;
    }

    // "Fight 3/20, Turn 1, Enemies rolling" — each stamp speaks when its
    // value changes, ahead of the phase phrase.
    private String stamped(String phrase) {
        StringBuilder sb = new StringBuilder();
        String fight = fightProgress();
        if (fight != null && !fight.equals(lastFight)) {
            lastFight = fight;
            lastTurn = -1;
            sb.append(fight).append(", ");
        }
        int turn = currentTurn();
        if (turn > 0 && turn != lastTurn) {
            lastTurn = turn;
            sb.append(Loc.get("combat", "turn", "turn", turn)).append(", ");
        }
        return sb.append(phrase).toString();
    }

    private static int currentTurn() {
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null || ds.getFightLog() == null) {
            return -1;
        }
        return ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getTurn();
    }

    private static String fightProgress() {
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null || ds.getFightLog() == null) {
            return null;
        }
        return GameText.t(ds.getFightLog().getContext().getLevelProgressString(false));
    }
}
