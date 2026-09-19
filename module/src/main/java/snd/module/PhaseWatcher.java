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

import snd.contracts.SndLog;
import snd.core.loc.Loc;
import snd.core.buffers.EventLog;
import snd.module.screens.CombatScreen;

/**
 * Speaks the combat turn structure: the current phase of the
 * PhaseManager stack as it changes, stamped with the turn number at the start
 * of each round and the fight number on a fight's first round. Only the
 * gameplay/animation phases speak here — decision phases (ChoicePhase and the
 * other reward dialogs) have their own screens with their own announcements,
 * and unknown phases stay silent rather than reading class names — but are
 * logged once per class, so a new phase can't slip by invisibly. Polled from
 * the module tick; the previous phase is remembered by identity only, for the
 * change diff.
 */
final class PhaseWatcher {
    private final EventLog events;
    private Phase lastPhase;
    // The last spoken fight/turn stamps. A fight's first announced phase can
    // be any of the in-combat phases (the opening enemy roll may resolve
    // beneath a ChoicePhase and never surface), so the stamp rides whichever
    // in-combat phase first speaks after the value changes.
    private String lastFight;
    private int lastTurn = -1;
    // The turn whose enemy intents were spoken: once per turn, however
    // often an undo rewinds targeting into rolling and back.
    private int intentsTurn = -1;
    private String intentsFight;

    PhaseWatcher(EventLog events) {
        this.events = events;
    }

    void tick() {
        Phase p = PhaseManager.get().getPhase();
        if (p == lastPhase) {
            return;
        }
        lastPhase = p;
        String line = lineFor(p);
        // The player's first phase of a turn: the enemy dice have landed.
        // What they threaten leads, since the roll about to be read is
        // judged against it.
        if (p instanceof PlayerRollingPhase || p instanceof TargetingPhase) {
            int turn = currentTurn();
            String fight = fightProgress();
            if (turn != intentsTurn || (fight != null && !fight.equals(intentsFight))) {
                intentsTurn = turn;
                intentsFight = fight;
                String intents = CombatScreen.enemyIntents();
                if (intents != null) {
                    events.say(intents, false);
                }
            }
        }
        if (line != null) {
            events.say(line, false);
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
            // The LevelEndScreen announces itself; only the turn stamps reset here.
            resetStamps();
            return null;
        }
        if (p instanceof RunEndPhase) {
            // The DialogPhaseScreen announces the victory/defeat band.
            resetStamps();
            return null;
        }
        // Decision phases (the rewardPhase package) are announced by their
        // own screens. Anything else is a phase this watcher has never met —
        // a game update or mod could add one — and a phase change the player
        // never hears is invisible, so leave a trace.
        if (!p.getClass().getName().contains(".rewardPhase.")
                && loggedUnknown.add(p.getClass().getName())) {
            SndLog.info("phase with no announcement: " + p.getClass().getName());
        }
        return null;
    }

    private final java.util.Set<String> loggedUnknown = new java.util.HashSet<String>();

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
            sb.append(GameText.t("Turn " + turn)).append(", "); // the game's own pattern string
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
