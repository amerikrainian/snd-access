package snd.module.screens;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.tann.dice.gameplay.phase.Phase;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.MessagePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.challenge.ChallengePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.misc.HeroChangePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.misc.ItemCombinePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.meta.SeqPhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.misc.PositionSwapPhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.reveal.RandomRevealPhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.trade.TradePhase;

import snd.contracts.SndLog;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.core.nav.AccessScreen;
import snd.core.nav.KeyOffer;
import snd.module.Captured;
import snd.module.GameKeys;
import snd.module.GameText;

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
        // A choice of paths (custom and TextMod content): built from the phase.
        DIALOG_FIELDS.put(SeqPhase.class, "a");
    }

    /** Whether this screen reads the phase's dialog. */
    public static boolean reads(Phase p) {
        return p != null && DIALOG_FIELDS.containsKey(p.getClass());
    }

    private final Map<Class<?>, Field> fieldCache = new HashMap<Class<?>, Field>();

    @Override
    public String key() {
        return "dialog-phase";
    }

    // MessagePhase.keyPress / RandomRevealPhase.keyPress: Space is OK (their
    // Enter and Backspace are the navigator's here). The two-choice dialogs
    // answer only Enter and Backspace, so they offer nothing of their own.
    @Override
    public List<KeyOffer> keys() {
        List<KeyOffer> keys = new ArrayList<KeyOffer>();
        Phase p = currentDialogPhase();
        if (p instanceof MessagePhase || p instanceof RandomRevealPhase) {
            keys.add(GameKeys.key("ok", Loc.get("ui", "key.space"), GameText.t("ok"), GameKeys.SPACE));
        }
        keys.add(GameKeys.escape());
        return keys;
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
        if (com.tann.dice.Main.getCurrentScreen() == null) {
            return null;
        }
        Phase p = PhaseManager.get().getPhase();
        return p != null && DIALOG_FIELDS.containsKey(p.getClass()) ? p : null;
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
        if (p instanceof SeqPhase) {
            return null; // its own message leads the dialog
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
        // A dialog is a few lines and its answers, not a list to count.
        b.pushContext(Loc.get("ui", "modal.dialog"), null, false);
        Phase p = currentDialogPhase();
        if (p instanceof SeqPhase) {
            seqNodes(b, (SeqPhase) p);
        } else {
            ActorNodes.emit(b, dialog);
        }
        b.popContext();
    }

    private static java.lang.reflect.Method pickPhase;

    // SeqPhase: its message, then a button per path, which picks it as the
    // path's own button does (SeqPhase.pickPhase).
    private static void seqNodes(GraphBuilder b, final SeqPhase phase) {
        final String message = (String) Captured.field(phase, SeqPhase.class, "message");
        List<?> paths = (List<?>) Captured.field(phase, SeqPhase.class, "spps");
        if (message == null || paths == null) {
            return; // logged by the field read
        }
        b.addItem(ControlId.structural(CompositeKey.of("seq", "message")),
                textNode(GameText.t(message)));
        for (int i = 0; i < paths.size(); i++) {
            final Object path = paths.get(i);
            final String title = (String) Captured.field(path, path.getClass(), "title");
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.BUTTON;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return GameText.t(title);
                }
            }, AnnouncementKinds.LABEL));
            vt.onActivate = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (pickPhase == null) {
                            pickPhase = SeqPhase.class.getDeclaredMethod("pickPhase", path.getClass());
                            pickPhase.setAccessible(true);
                        }
                        pickPhase.invoke(phase, path);
                    } catch (Exception e) {
                        SndLog.error("SeqPhase.pickPhase failed", e);
                    }
                }
            };
            b.addItem(ControlId.structural(CompositeKey.of("seq", "path", i)), vt);
        }
    }

    private static NodeVtable textNode(final String text) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TEXT;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return text;
            }
        }, AnnouncementKinds.LABEL));
        return vt;
    }
}
