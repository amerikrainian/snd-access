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
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.Choosable;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.reveal.RandomRevealPhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.trade.TradePhase;

import snd.contracts.HostServices;
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
    private final HostServices host;

    public DialogPhaseScreen(HostServices host) {
        this.host = host;
    }

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
        Phase p = currentDialogPhase();
        // One phase's dialog often follows another's on this same screen (a
        // class reroll's reveal, a path's message): keyed on the phase, the
        // next one lands on its first line instead of the last one's place.
        b.beginStop(CompositeKey.of("dialog", p));
        // A dialog is a few lines and its answers, not a list to count.
        b.pushContext(Loc.get("ui", "modal.dialog"), null, false);
        if (p instanceof SeqPhase) {
            seqNodes(b, (SeqPhase) p);
        } else if (!modelNodes(b, p)) {
            ActorNodes.emit(b, dialog);
        }
        b.popContext();
        PartyNodes.build(b, host);
    }

    // ---- the event dialogs, read from their phase: what each one asks,
    // what it offers or costs, and its own answers. False for a phase read
    // by walking its dialog (the curse loop's reset panel). ----

    private boolean modelNodes(GraphBuilder b, Phase p) {
        if (p instanceof ItemCombinePhase) {
            anvil(b, (ItemCombinePhase) p);
        } else if (p instanceof ChallengePhase) {
            challenge(b, (ChallengePhase) p);
        } else if (p instanceof TradePhase) {
            cursedChest(b, (TradePhase) p);
        } else if (p instanceof HeroChangePhase) {
            classReroll(b, (HeroChangePhase) p);
        } else if (p instanceof PositionSwapPhase) {
            positionSwap(b, (PositionSwapPhase) p);
        } else if (p instanceof MessagePhase) {
            message(b, (MessagePhase) p);
        } else if (p instanceof RandomRevealPhase) {
            reveal(b, (RandomRevealPhase) p);
        } else if (p instanceof com.tann.dice.gameplay.phase.endPhase.runEnd.RunEndPhase) {
            runEnd(b, (com.tann.dice.gameplay.phase.endPhase.runEnd.RunEndPhase) p);
        } else {
            return false;
        }
        return true;
    }

    private static com.tann.dice.util.ui.choice.ChoiceDialog dialogOf(Phase p, String field) {
        return (com.tann.dice.util.ui.choice.ChoiceDialog) Captured.field(p, p.getClass(), field);
    }

    // The runnable a dialog's accept answer runs (index 1, its key route's).
    private static Object acceptOf(com.tann.dice.util.ui.choice.ChoiceDialog dialog) {
        Object[] choices = (Object[]) Captured.field(dialog, com.tann.dice.util.ui.choice.ChoiceDialog.class, "choices");
        return choices != null && choices.length > 1
                ? Captured.field(choices[1], com.tann.dice.util.ui.choice.CDChoice.class, "onClick") : null;
    }

    // ItemCombinePhase: its question, the items it takes (held by the accept
    // answer), what they become, then accept or decline.
    @SuppressWarnings("unchecked")
    private static void anvil(GraphBuilder b, ItemCombinePhase p) {
        com.tann.dice.util.ui.choice.ChoiceDialog cd = dialogOf(p, "cd");
        Object type = Captured.field(p, ItemCombinePhase.class, "combineType");
        DialogNodes.text(b, "prompt", GameText.t((String) Captured.field(type, type.getClass(), "description")));
        Object accept = acceptOf(cd);
        List<com.tann.dice.gameplay.content.item.Item> lost = Captured.value(accept, List.class);
        if (lost != null) {
            for (int i = 0; i < lost.size(); i++) {
                DialogNodes.choosable(b, CompositeKey.of("lost", i), lost.get(i));
            }
        }
        Choosable reward = Captured.value(accept, Choosable.class);
        if (reward != null) {
            DialogNodes.text(b, "reward", GameText.t(reward.describe()));
        }
        DialogNodes.answers(b, cd);
    }

    // ChallengePhase: the extra monsters, the rewards, then accept or decline.
    private static void challenge(GraphBuilder b, ChallengePhase p) {
        List<com.tann.dice.gameplay.content.ent.type.MonsterType> monsters = p.getChallengeType().getMonsterTypes();
        DialogNodes.text(b, "challenge", GameText.t("[orange]Challenge:"));
        DialogNodes.text(b, "extra", GameText.t("[text]Extra "
                + com.tann.dice.util.lang.Words.plural("monster", monsters.size())));
        for (int i = 0; i < monsters.size(); i++) {
            monster(b, CompositeKey.of("monster", i), monsters.get(i));
        }
        Object reward = Captured.field(p, ChallengePhase.class, "challengeReward");
        List<Choosable> rewards = reward != null
                ? ((com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.challenge.ChallengeReward) reward)
                        .getRewards()
                : java.util.Collections.<Choosable>emptyList();
        DialogNodes.text(b, "rewards", GameText.t("[green]"
                + com.tann.dice.util.lang.Words.plural("Reward", rewards.size()) + ":"));
        for (int i = 0; i < rewards.size(); i++) {
            DialogNodes.choosable(b, CompositeKey.of("reward", i), rewards.get(i));
        }
        DialogNodes.answers(b, dialogOf(p, "choiceDialog"));
    }

    private static void monster(GraphBuilder b, Object key,
            final com.tann.dice.gameplay.content.ent.type.MonsterType type) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TEXT;
        vt.subject = type;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return GameText.t(type.getName(true));
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return UnitLines.restHp(type);
                    }
                }, AnnouncementKinds.VALUE));
        b.addItem(ControlId.referenced(type, CompositeKey.of("dialog", key)), vt);
    }

    // TradePhase: the chest's question, what opening it gives, then answers.
    @SuppressWarnings("unchecked")
    private static void cursedChest(GraphBuilder b, TradePhase p) {
        DialogNodes.text(b, "prompt", GameText.t("[purple]Open cursed chest?"));
        List<Choosable> gain = (List<Choosable>) Captured.field(p, TradePhase.class, "gain");
        if (gain != null) {
            for (int i = 0; i < gain.size(); i++) {
                DialogNodes.choosable(b, CompositeKey.of("gain", i), gain.get(i));
            }
        }
        DialogNodes.answers(b, dialogOf(p, "cd"));
    }

    // HeroChangePhase: its question about the hero (held by the accept
    // answer), the hero as its panel reads, then yes or no. The game's arrow
    // to a question mark says only that the result is unknown, which the
    // question says already.
    private static void classReroll(GraphBuilder b, HeroChangePhase p) {
        com.tann.dice.util.ui.choice.ChoiceDialog cd = dialogOf(p, "cd");
        com.tann.dice.gameplay.content.ent.Hero hero =
                Captured.value(acceptOf(cd), com.tann.dice.gameplay.content.ent.Hero.class);
        Object type = Captured.field(p, HeroChangePhase.class, "type");
        if (hero != null && type != null) {
            String desc = (String) Captured.field(type, type.getClass(), "desc");
            DialogNodes.text(b, "prompt", GameText.t(desc.replaceAll("Z", hero.getName(true)) + "?"));
            EntPanelNodes.unit(b, hero, java.util.Collections.<String>emptyList(),
                    java.util.Collections.<String>emptyList());
        }
        DialogNodes.answers(b, cd);
    }

    // PositionSwapPhase: the two heroes it would swap, by their places among
    // the living (its own lookup), then yes or no.
    private static void positionSwap(GraphBuilder b, PositionSwapPhase p) {
        Object a = Captured.field(p, PositionSwapPhase.class, "swapA");
        Object c = Captured.field(p, PositionSwapPhase.class, "swapB");
        List<com.tann.dice.gameplay.content.ent.Hero> heroes = com.tann.dice.screens.dungeon.DungeonScreen.get()
                .getFightLog().getSnapshot(com.tann.dice.gameplay.fightLog.FightLog.Temporality.Present)
                .getAliveHeroEntities();
        if (a != null && c != null && (Integer) a < heroes.size() && (Integer) c < heroes.size()) {
            DialogNodes.text(b, "prompt", GameText.t("Swap " + heroes.get((Integer) a).getName(true) + " with "
                    + heroes.get((Integer) c).getName(true) + "?[n][n][purple](no side-effects)"));
        }
        DialogNodes.answers(b, dialogOf(p, "cd"));
    }

    // MessagePhase: its message and its one button (its key route).
    private static void message(GraphBuilder b, final MessagePhase p) {
        DialogNodes.text(b, "message", GameText.t("[text]" + Captured.field(p, MessagePhase.class, "msg")));
        DialogNodes.button(b, "ok", GameText.t((String) Captured.field(p, MessagePhase.class, "conf")), new Runnable() {
            @Override
            public void run() {
                p.keyPress(66);
            }
        });
    }

    // RandomRevealPhase: what was gained, a hero as its panel reads, then ok.
    private final java.util.Map<Object, com.tann.dice.gameplay.content.ent.Ent> revealedHeroes =
            new java.util.WeakHashMap<Object, com.tann.dice.gameplay.content.ent.Ent>();

    @SuppressWarnings("unchecked")
    private void reveal(GraphBuilder b, final RandomRevealPhase p) {
        DialogNodes.text(b, "title", GameText.t("[yellow]Gained:"));
        List<Choosable> gained = (List<Choosable>) Captured.field(p, RandomRevealPhase.class, "choosables");
        if (gained != null) {
            for (int i = 0; i < gained.size(); i++) {
                Choosable c = gained.get(i);
                if (c instanceof com.tann.dice.gameplay.content.ent.type.HeroType) {
                    // The game shows a fresh unit of the class: one per class
                    // per reveal, so the panel's lines keep their identity.
                    com.tann.dice.gameplay.content.ent.Ent unit = revealedHeroes.get(c);
                    if (unit == null) {
                        unit = ((com.tann.dice.gameplay.content.ent.type.HeroType) c).makeEnt();
                        revealedHeroes.put(c, unit);
                    }
                    EntPanelNodes.unit(b, unit, java.util.Collections.<String>emptyList(),
                            java.util.Collections.<String>emptyList());
                } else {
                    DialogNodes.choosable(b, CompositeKey.of("gained", i), c);
                }
            }
        }
        DialogNodes.button(b, "ok", GameText.t("[green][b]ok"), new Runnable() {
            @Override
            public void run() {
                p.onOk();
            }
        });
    }

    // RunEndPhase: the band's left column (the mode's own end text and
    // extras) and its buttons; its pictures are left out.
    @SuppressWarnings("unchecked")
    private static void runEnd(GraphBuilder b, com.tann.dice.gameplay.phase.endPhase.runEnd.RunEndPhase p) {
        Object panel = Captured.field(p, com.tann.dice.gameplay.phase.endPhase.runEnd.RunEndPhase.class, "endPanel");
        if (panel == null) {
            return;
        }
        Class<?> cls = com.tann.dice.gameplay.phase.endPhase.RunEndPanel.class;
        Actor left = (Actor) Captured.field(panel, cls, "left");
        if (left != null) {
            ActorNodes.emit(b, left);
        }
        List<Actor> buttons = (List<Actor>) Captured.field(panel, cls, "rightButtons");
        if (buttons != null) {
            for (Actor button : buttons) {
                ActorNodes.emit(b, button);
            }
        }
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
