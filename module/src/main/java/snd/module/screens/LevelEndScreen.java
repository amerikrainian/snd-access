package snd.module.screens;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.tann.dice.gameplay.context.DungeonContext;
import com.tann.dice.gameplay.phase.Phase;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.levelEndPhase.LevelEndPanel;
import com.tann.dice.gameplay.phase.levelEndPhase.LevelEndPhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.MessagePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.challenge.ChallengePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.ChoicePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.misc.HeroChangePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.misc.ItemCombinePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.misc.PositionSwapPhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.reveal.RandomRevealPhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.trade.TradePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.resetPhase.ResetPhase;
import com.tann.dice.screens.dungeon.DungeonScreen;
import com.tann.dice.util.ui.TextWriter;

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
import snd.module.ChoicePhases;
import snd.module.GameKeys;
import snd.module.GameText;
import snd.module.GameUi;

/**
 * The between-fights hub (LevelEndPhase): pending reward phases as named
 * buttons instead of the panel's icon-only ones, the Inventory and Continue
 * buttons, any refusal/reminder text the panel shows beneath itself, and a
 * run-map readout standing in for the purely visual minimap. Everything
 * drives the panel's own click paths, so refusals, confirmation dialogs, and
 * phase starts behave exactly as a mouse click would.
 */
public class LevelEndScreen extends AccessScreen {
    private final HostServices host;

    public LevelEndScreen(HostServices host) {
        this.host = host;
    }

    @Override
    public String key() {
        return "level-end";
    }

    @Override
    public int layer() {
        return 10; // above the combat screen; pushed modals cover it
    }

    @Override
    public boolean isActive() {
        return phase() != null;
    }

    private static LevelEndPhase phase() {
        if (!(com.tann.dice.Main.getCurrentScreen() instanceof DungeonScreen)) {
            return null;
        }
        Phase p = PhaseManager.get().getPhase();
        return p instanceof LevelEndPhase ? (LevelEndPhase) p : null;
    }

    @Override
    public String screenName() {
        LevelEndPhase p = phase();
        if (p == null) {
            return null;
        }
        String progress = progressText();
        String won = Loc.get("ui", "levelend.won");
        return progress != null ? progress + " " + won : won;
    }

    private static String progressText() {
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null) {
            return null;
        }
        return GameText.t(ds.getDungeonContext().getLevelProgressString(false));
    }

    // LevelEndPhase.keyPress: a digit starts that reward, I opens the
    // inventory. Enter (its Continue) is the navigator's here.
    @Override
    public List<KeyOffer> keys() {
        List<KeyOffer> keys = new ArrayList<KeyOffer>();
        LevelEndPhase p = phase();
        if (p == null) {
            return keys;
        }
        int pending = 0;
        for (Phase nested : p.getNestedPhases()) {
            if (!nested.hasActivated()) {
                pending++;
            }
        }
        if (pending > 0) {
            keys.add(GameKeys.range("digits", GameKeys.digits(pending), Loc.get("ui", "help.start_reward")));
        }
        if (DungeonScreen.get().getDungeonContext().allowInventory()) {
            keys.add(GameKeys.key("inventory", "I", GameText.t("Inventory"), GameKeys.I));
        }
        keys.add(GameKeys.escape());
        return keys;
    }

    @Override
    public void build(GraphBuilder b) {
        final LevelEndPhase p = phase();
        if (p == null) {
            return;
        }
        final LevelEndPanel panel = p.levelEndPanel;
        DungeonScreen ds = DungeonScreen.get();
        final DungeonContext dc = ds.getDungeonContext();

        // Pending rewards, mirroring the panel's own pruning of started ones.
        List<Phase> pending = new ArrayList<Phase>();
        for (Phase nested : p.getNestedPhases()) {
            if (!nested.hasActivated()) {
                pending.add(nested);
            }
        }
        if (!pending.isEmpty()) {
            b.pushContext(Loc.get("ui", "levelend.rewards"), Loc.get("ui", "role.list"));
            for (int i = 0; i < pending.size(); i++) {
                final Phase reward = pending.get(i);
                NodeVtable vt = new NodeVtable();
                vt.controlType = ControlTypes.BUTTON;
                vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return rewardName(reward);
                    }
                }, AnnouncementKinds.LABEL));
                vt.onActivate = new Runnable() {
                    @Override
                    public void run() {
                        clickPhaseStart(panel, reward);
                    }
                };
                b.addItem(ControlId.referenced(reward, CompositeKey.of("reward", i)), vt);
            }
            b.popContext();
        }

        if (dc.allowInventory()) {
            NodeVtable inv = new NodeVtable();
            inv.controlType = ControlTypes.BUTTON;
            inv.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t("Inventory");
                        }
                    }, AnnouncementKinds.LABEL),
                    // The pulsing glow on new items, as a word.
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return dc.getParty().anyNewItems()
                                    ? Loc.get("ui", "levelend.new_items") : null;
                        }
                    }, AnnouncementKinds.VALUE));
            inv.onActivate = new Runnable() {
                @Override
                public void run() {
                    panel.inventoryClick();
                }
            };
            inv.speaksOwnPosition = true;
            b.addItem(ControlId.structural(CompositeKey.of("levelend", "inventory")), inv);
        }

        NodeVtable cont = new NodeVtable();
        cont.controlType = ControlTypes.BUTTON;
        cont.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return GameText.t("Continue");
            }
        }, AnnouncementKinds.LABEL));
        cont.onActivate = new Runnable() {
            @Override
            public void run() {
                // The panel's refusal reasons only render as text below it —
                // speak the reason, then run the same click (error sound,
                // text, or the confirm dialog / actual continue).
                String reason = noContinueReason(panel, p);
                if (reason != null) {
                    host.speech().speak(GameText.t(reason), true);
                }
                panel.continueClick();
            }
        };
        cont.speaksOwnPosition = true;
        b.addItem(ControlId.structural(CompositeKey.of("levelend", "continue")), cont);

        // Refusal / "items unequipped" reminder lines under the panel.
        List<TextWriter> under = underneaths(panel);
        for (int i = 0; i < under.size(); i++) {
            final TextWriter tw = under.get(i);
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.TEXT;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return tw.text;
                }
            }, AnnouncementKinds.LABEL));
            vt.speaksOwnPosition = true;
            b.addItem(ControlId.referenced(tw, CompositeKey.of("levelend", "under", i)), vt);
        }

        if (dc.getContextConfig().mode.showMinimap()) {
            ControlId above = under.isEmpty() ? ControlId.structural(CompositeKey.of("levelend", "continue"))
                    : ControlId.referenced(under.get(under.size() - 1),
                            CompositeKey.of("levelend", "under", under.size() - 1));
            buildMap(b, dc, above);
        }

        PartyNodes.build(b, host);
        TutorialNodes.build(b, ds);
    }

    // The minimap (MiniMap) as the strip it is: a row of the fights its
    // window shows, left to right, each by what its icon paints — boss or not,
    // done or current — under the zone whose background it stands on. Down
    // from the panel lands on the current fight.
    private static void buildMap(GraphBuilder b, DungeonContext dc, ControlId above) {
        int current = dc.getCurrentMod20LevelNumber();
        // MiniMap's own scroll: at most 8 fights, the current one fifth.
        int first = Math.min(Math.max(0, current - 4), 12) + 1;
        int last = first + Math.min(8, dc.getTotalLength()) - 1;

        b.pushContext(Loc.get("ui", "levelend.map"), null, false);
        b.startRow("levelend-map");
        int level = 0;
        for (com.tann.dice.util.tp.TP<com.tann.dice.gameplay.battleTest.Zone, Integer> zone : dc.getLevelTypes()) {
            b.pushContext(GameText.t(zone.a.name()), null, false);
            for (int i = 0; i < zone.b; i++) {
                level++;
                if (level < first || level > last) {
                    continue;
                }
                final int fight = dc.getCurrentLevelNumber() + (level - current);
                final boolean boss = dc.getContextConfig().isBoss(level);
                final String state = level < current ? Loc.get("ui", "levelend.map_done")
                        : level == current ? Loc.get("ui", "levelend.map_current") : null;
                NodeVtable vt = new NodeVtable();
                vt.controlType = ControlTypes.TEXT;
                vt.speaksOwnPosition = true;
                vt.announcements = Arrays.asList(
                        NodeAnnouncement.kinded(new Supplier<String>() {
                            @Override
                            public String get() {
                                return Loc.get("ui", "levelend.map_fight", "n", fight);
                            }
                        }, AnnouncementKinds.LABEL),
                        NodeAnnouncement.kinded(new Supplier<String>() {
                            @Override
                            public String get() {
                                return boss ? Loc.get("ui", "levelend.map_boss") : null;
                            }
                        }, AnnouncementKinds.VALUE),
                        NodeAnnouncement.kinded(new Supplier<String>() {
                            @Override
                            public String get() {
                                return state;
                            }
                        }, AnnouncementKinds.STATE));
                ControlId id = ControlId.structural(CompositeKey.of("levelend", "map", level));
                b.addItem(id, vt);
                if (level == current) {
                    b.connect(above, snd.core.graph.GraphDir.DOWN, id);
                }
            }
            b.popContext();
        }
        b.endRow();
        b.popContext();
    }

    // A pending reward's spoken name. ChoicePhases carry the game's own offer
    // header; the fixed event phases get role words; anything else falls back
    // to the text of the button the panel would draw for it.
    static String rewardName(Phase phase) {
        if (phase instanceof com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.meta.PhaseGeneratorTransformPhase) {
            // The generator wrapper resolves (and caches) its real phase for
            // its own hub button; name that phase the same way.
            Phase inner = resolveGeneratorPhase(
                    (com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.meta.PhaseGeneratorTransformPhase) phase);
            if (inner != null && inner != phase) {
                return rewardName(inner);
            }
        }
        if (phase instanceof ChoicePhase) {
            ChoicePhase choice = (ChoicePhase) phase;
            String header = ChoicePhases.header(choice);
            if (header != null) {
                return header;
            }
            // An optional offer has no header and an icon for a button: its
            // own message, else what it offers.
            String top = ChoicePhases.topMessage(choice);
            if (top != null && !top.trim().isEmpty()) {
                return GameText.t(top);
            }
            List<com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.Choosable> options =
                    ChoicePhases.options(choice);
            if (!options.isEmpty()) {
                return ChoiceScreen.nameOf(options.get(0));
            }
        }
        if (phase instanceof com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.meta.SeqPhase) {
            // A choice of paths: its message, where its button says "chain".
            Object message = snd.module.Captured.field(phase,
                    com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.meta.SeqPhase.class, "message");
            if (message != null) {
                return GameText.t((String) message);
            }
        }
        String key = fixedRewardKey(phase);
        if (key != null) {
            return Loc.get("ui", key);
        }
        try {
            String label = GameUi.labelOf(phase.getLevelEndButton());
            if (label != null && !label.trim().isEmpty() && !"??".equals(label.trim())) {
                return label;
            }
        } catch (Throwable t) {
            SndLog.error("getLevelEndButton label failed for " + phase.getClass().getSimpleName(), t);
        }
        return phase.getClass().getSimpleName();
    }

    private static String fixedRewardKey(Phase phase) {
        if (phase instanceof ChallengePhase) {
            return "reward.challenge";
        }
        if (phase instanceof TradePhase) {
            return "reward.cursed_chest";
        }
        if (phase instanceof ItemCombinePhase) {
            return "reward.anvil";
        }
        if (phase instanceof HeroChangePhase) {
            return "reward.class_reroll";
        }
        if (phase instanceof PositionSwapPhase) {
            return "reward.position_swap";
        }
        if (phase instanceof MessagePhase) {
            return "reward.message";
        }
        if (phase instanceof RandomRevealPhase) {
            return "reward.reveal";
        }
        if (phase instanceof ResetPhase) {
            return "reward.reset";
        }
        return null;
    }

    private static Method makePhaseCachedMethod;

    private static Phase resolveGeneratorPhase(
            com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.meta.PhaseGeneratorTransformPhase phase) {
        try {
            if (makePhaseCachedMethod == null) {
                makePhaseCachedMethod = phase.getClass().getDeclaredMethod("makePhaseCached", DungeonContext.class);
                makePhaseCachedMethod.setAccessible(true);
            }
            DungeonScreen ds = DungeonScreen.get();
            if (ds == null) {
                return null;
            }
            return (Phase) makePhaseCachedMethod.invoke(phase, ds.getDungeonContext());
        } catch (Throwable t) {
            SndLog.error("failed to resolve generator phase", t);
            return null;
        }
    }

    // ---- the panel's non-public internals: the digit-key phase-start route,
    // the continue-refusal reason, and the text lines under the panel ----

    private static Method clickPhaseStartMethod;

    private static void clickPhaseStart(LevelEndPanel panel, Phase phase) {
        try {
            if (clickPhaseStartMethod == null) {
                clickPhaseStartMethod = LevelEndPanel.class.getDeclaredMethod("clickPhaseStart", Phase.class);
                clickPhaseStartMethod.setAccessible(true);
            }
            clickPhaseStartMethod.invoke(panel, phase);
        } catch (Throwable t) {
            SndLog.error("clickPhaseStart failed", t);
        }
    }

    private static Method noContinueReasonMethod;

    private static String noContinueReason(LevelEndPanel panel, LevelEndPhase phase) {
        if (!phase.getNestedPhases().isEmpty()) {
            return null; // the game starts the first pending reward instead
        }
        try {
            if (noContinueReasonMethod == null) {
                noContinueReasonMethod = LevelEndPanel.class.getDeclaredMethod("getNoContinueReason");
                noContinueReasonMethod.setAccessible(true);
            }
            return (String) noContinueReasonMethod.invoke(panel);
        } catch (Throwable t) {
            SndLog.error("getNoContinueReason failed", t);
            return null;
        }
    }

    private static Field underneathsField;

    @SuppressWarnings("unchecked")
    private static List<TextWriter> underneaths(LevelEndPanel panel) {
        try {
            if (underneathsField == null) {
                underneathsField = LevelEndPanel.class.getDeclaredField("underneaths");
                underneathsField.setAccessible(true);
            }
            List<TextWriter> list = (List<TextWriter>) underneathsField.get(panel);
            return list != null ? list : Collections.<TextWriter>emptyList();
        } catch (Throwable t) {
            SndLog.error("failed to read LevelEndPanel.underneaths", t);
            return Collections.emptyList();
        }
    }
}
