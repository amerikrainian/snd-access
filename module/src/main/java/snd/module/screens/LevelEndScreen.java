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

import snd.core.HostServices;
import snd.core.SndLog;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.core.nav.AccessScreen;
import snd.module.ChoicePhases;
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
        try {
            if (!(com.tann.dice.Main.getCurrentScreen() instanceof DungeonScreen)) {
                return null;
            }
            Phase p = PhaseManager.get().getPhase();
            return p instanceof LevelEndPhase ? (LevelEndPhase) p : null;
        } catch (Throwable t) {
            return null;
        }
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
            b.addItem(ControlId.referenced(tw, CompositeKey.of("levelend", "under", i)), vt);
        }

        // The minimap, as words: fight progress, zone, next boss.
        if (dc.getContextConfig().mode.showMinimap()) {
            NodeVtable map = new NodeVtable();
            map.controlType = ControlTypes.TEXT;
            map.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return Loc.get("ui", "levelend.map");
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return mapText(dc);
                        }
                    }, AnnouncementKinds.VALUE));
            b.addItem(ControlId.structural(CompositeKey.of("levelend", "map")), map);
        }
    }

    // "Fight 5/20, zone Dungeon, next boss at fight 8" — what the minimap's
    // node icons and zone backgrounds paint.
    private static String mapText(DungeonContext dc) {
        StringBuilder sb = new StringBuilder();
        String progress = GameText.t(dc.getLevelProgressString(false));
        if (progress != null) {
            sb.append(progress);
        }
        int levelNumber = dc.getCurrentMod20LevelNumber();
        try {
            int cumulative = 0;
            for (com.tann.dice.util.tp.TP<com.tann.dice.gameplay.battleTest.Zone, Integer> zone
                    : dc.getLevelTypes()) {
                cumulative += zone.b;
                if (levelNumber <= cumulative) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(Loc.get("ui", "levelend.map_zone", "zone", zone.a.name()));
                    break;
                }
            }
        } catch (Throwable t) {
            SndLog.error("minimap zone read failed", t);
        }
        int total = dc.getContextConfig().getTotalDifferentLevels();
        for (int level = levelNumber; level <= total; level++) {
            if (dc.getContextConfig().isBoss(level)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(level == levelNumber
                        ? Loc.get("ui", "levelend.map_boss_now")
                        : Loc.get("ui", "levelend.map_boss", "n",
                                dc.getCurrentLevelNumber() + (level - levelNumber)));
                break;
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
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
            String header = ChoicePhases.header((ChoicePhase) phase);
            if (header != null) {
                return header;
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
