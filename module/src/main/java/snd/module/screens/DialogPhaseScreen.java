package snd.module.screens;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.tann.dice.gameplay.phase.Phase;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.MessagePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.challenge.ChallengePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.misc.HeroChangePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.misc.ItemCombinePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.misc.PositionSwapPhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.reveal.RandomRevealPhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.trade.TradePhase;

import snd.core.SndLog;
import snd.core.graph.GraphBuilder;
import snd.core.loc.Loc;
import snd.core.nav.AccessScreen;

/**
 * The dialog-based event phases: challenge offers, the cursed chest, the
 * anvil, class rerolls, position swaps, message and reward-reveal panels.
 * Their dialogs are added straight to the dungeon screen — never pushed onto
 * the modal stack — so the generic modal reader can't see them; each phase
 * keeps its dialog in a private field, read here and walked with the shared
 * actor reader. Buttons fire the game's own listeners (accept/decline, ok),
 * and the game's own keys (Enter, Backspace, Space) keep working underneath.
 */
public class DialogPhaseScreen extends AccessScreen {

    // Phase class → the field holding its on-screen dialog actor.
    private static final Map<Class<?>, String> DIALOG_FIELDS = new HashMap<Class<?>, String>();
    static {
        DIALOG_FIELDS.put(ChallengePhase.class, "choiceDialog");
        DIALOG_FIELDS.put(TradePhase.class, "cd");
        DIALOG_FIELDS.put(ItemCombinePhase.class, "cd");
        DIALOG_FIELDS.put(HeroChangePhase.class, "cd");
        DIALOG_FIELDS.put(PositionSwapPhase.class, "cd");
        DIALOG_FIELDS.put(MessagePhase.class, "messageActor");
        DIALOG_FIELDS.put(RandomRevealPhase.class, "g");
        // The victory/defeat band: left column text, right column buttons
        // (Quit, Stats, mode extras), all readable once the slide-in reveals
        // them.
        DIALOG_FIELDS.put(com.tann.dice.gameplay.phase.endPhase.runEnd.RunEndPhase.class, "endPanel");
        // The cursed-family loop boundary: purple text + a single "never" button.
        DIALOG_FIELDS.put(com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.resetPhase.ResetPhase.class,
                "resetPanel");
    }

    private final Map<Class<?>, Field> fieldCache = new HashMap<Class<?>, Field>();

    @Override
    public String key() {
        return "dialog-phase";
    }

    @Override
    public int layer() {
        return 15; // over the hub/choice screens; pushed modals still win
    }

    @Override
    public boolean isActive() {
        return dialog() != null;
    }

    private Phase currentDialogPhase() {
        try {
            if (com.tann.dice.Main.getCurrentScreen() == null) {
                return null;
            }
            Phase p = PhaseManager.get().getPhase();
            return p != null && DIALOG_FIELDS.containsKey(p.getClass()) ? p : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The phase's live dialog actor, or null before activate / after removal. */
    private Actor dialog() {
        Phase p = currentDialogPhase();
        if (p == null) {
            return null;
        }
        try {
            Field field = fieldCache.get(p.getClass());
            if (field == null) {
                field = p.getClass().getDeclaredField(DIALOG_FIELDS.get(p.getClass()));
                field.setAccessible(true);
                fieldCache.put(p.getClass(), field);
            }
            Actor actor = (Actor) field.get(p);
            return actor != null && actor.getStage() != null ? actor : null;
        } catch (Throwable t) {
            SndLog.error("failed to read dialog actor for " + p.getClass().getSimpleName(), t);
            return null;
        }
    }

    @Override
    public String screenName() {
        Phase p = currentDialogPhase();
        if (p == null) {
            return null;
        }
        if (p instanceof ChallengePhase) {
            return Loc.get("ui", "reward.challenge");
        }
        if (p instanceof TradePhase) {
            return Loc.get("ui", "reward.cursed_chest");
        }
        if (p instanceof ItemCombinePhase) {
            return Loc.get("ui", "reward.anvil");
        }
        if (p instanceof HeroChangePhase) {
            return Loc.get("ui", "reward.class_reroll");
        }
        if (p instanceof PositionSwapPhase) {
            return Loc.get("ui", "reward.position_swap");
        }
        if (p instanceof MessagePhase) {
            return Loc.get("ui", "reward.message");
        }
        if (p instanceof com.tann.dice.gameplay.phase.endPhase.runEnd.RunEndPhase) {
            return runEndTitle((com.tann.dice.gameplay.phase.endPhase.runEnd.RunEndPhase) p);
        }
        if (p instanceof com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.resetPhase.ResetPhase) {
            return Loc.get("ui", "reward.reset");
        }
        return Loc.get("ui", "reward.reveal");
    }

    // "Classic - Victory": the stats header's own composition.
    static String runEndTitle(com.tann.dice.gameplay.phase.endPhase.runEnd.RunEndPhase p) {
        String word = snd.module.GameText.t(isVictory(p) ? "Victory" : "Defeat");
        try {
            com.tann.dice.screens.dungeon.DungeonScreen ds = com.tann.dice.screens.dungeon.DungeonScreen.get();
            String title = snd.module.GameText.t(
                    ds.getDungeonContext().getContextConfig().getEndTitle());
            return title + ", " + word;
        } catch (Throwable t) {
            SndLog.error("run end title failed", t);
            return word;
        }
    }

    private static Field victoryField;

    static boolean isVictory(com.tann.dice.gameplay.phase.endPhase.runEnd.RunEndPhase p) {
        try {
            if (victoryField == null) {
                victoryField = com.tann.dice.gameplay.phase.endPhase.runEnd.RunEndPhase.class
                        .getDeclaredField("victory");
                victoryField.setAccessible(true);
            }
            return victoryField.getBoolean(p);
        } catch (Throwable t) {
            SndLog.error("failed to read RunEndPhase.victory", t);
            return false;
        }
    }

    @Override
    public void build(GraphBuilder b) {
        Actor dialog = dialog();
        if (dialog == null) {
            return;
        }
        b.pushContext(Loc.get("ui", "modal.dialog"));
        ActorNodes.emit(b, dialog);
        b.popContext();
    }
}
