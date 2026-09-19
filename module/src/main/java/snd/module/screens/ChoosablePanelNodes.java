package snd.module.screens;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.tann.dice.gameplay.effect.eff.Eff;
import com.tann.dice.gameplay.effect.eff.keyword.Keyword;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.Choosable;
import com.tann.dice.screens.dungeon.panels.entPanel.choosablePanel.ConcisePanel;

import snd.contracts.SndLog;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.module.GameText;

/**
 * The pushed big version of a choosable's panel (items, modifiers, curses),
 * read from the model instead of the actor walk: name with its tier, the full
 * description, and one node per referenced keyword with its rules — the
 * panel's own accessors supply each part, so the readout can't drift from
 * what the panel renders (the keyword rules use the panel's representative
 * effect, so parameterized wordings match the visual boxes). The accessors
 * are protected, reached reflectively once.
 */
public final class ChoosablePanelNodes {
    private ChoosablePanelNodes() {
    }

    private static Field choosableField;
    private static Method fullDescription;
    private static Method referencedKeywords;
    private static Method singleEffOrNull;

    /**
     * The same panel as lines — name with its tier, the description, a line
     * per referenced keyword — for reading it where the player stands when it
     * comes up as an info popup. Null when the panel's choosable can't be
     * read; the caller falls back to the panel's drawn text.
     */
    public static List<String> lines(ConcisePanel panel) {
        Choosable choosable = choosable(panel);
        if (choosable == null) {
            return null;
        }
        List<String> lines = new java.util.ArrayList<String>();
        String name = ChoiceScreen.nameOf(choosable);
        String tier = tierText(choosable);
        lines.add(tier != null ? name + ", " + tier : name);
        String desc = fullDescription(panel);
        if (desc != null && !desc.trim().isEmpty()) {
            lines.add(GameText.t(desc));
        }
        List<Keyword> keywords = referencedKeywords(panel);
        if (keywords != null) {
            for (Keyword k : keywords) {
                lines.add(GameText.t(k.getColourTaggedString()) + ": " + GameText.t(k.getRules(singleEffOrNull(panel, k))));
            }
        }
        return lines;
    }

    /**
     * "tier 3", or "tier -2" for a curse tier: the game draws both as a bare
     * number and tells them apart by colour alone (purple), so the tier is
     * read from the model, sign and all, in the game's own uncoloured form.
     * Null where the game draws its "/" for no tier at all (an unrated
     * modifier, a tierless item).
     */
    static String tierText(Choosable choosable) {
        try {
            String shown = snd.contracts.speech.TextFilter.clean(choosable.getTierString());
            if (!shown.matches(".*[0-9IVXLC].*")) {
                return null;
            }
            return Loc.get("ui", "choice.tier", "tier",
                    GameText.t(com.tann.dice.util.lang.Words.getTierString(choosable.getTier())));
        } catch (Throwable t) {
            snd.contracts.SndLog.error("tier read failed", t);
            return null;
        }
    }

    static void emit(GraphBuilder b, final ConcisePanel panel) {
        final Choosable choosable = choosable(panel);
        if (choosable == null) {
            ActorNodes.emit(b, panel); // the generic floor still reads
            return;
        }

        NodeVtable name = new NodeVtable();
        name.controlType = ControlTypes.TEXT;
        name.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return ChoiceScreen.nameOf(choosable);
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return tierText(choosable);
                    }
                }, AnnouncementKinds.VALUE));
        b.addItem(ControlId.structural(CompositeKey.of("choosable-panel", "name")), name);

        final String desc = fullDescription(panel);
        if (desc != null && !desc.trim().isEmpty()) {
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.TEXT;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return GameText.t(desc);
                }
            }, AnnouncementKinds.LABEL));
            b.addItem(ControlId.structural(CompositeKey.of("choosable-panel", "desc")), vt);
        }

        List<Keyword> keywords = referencedKeywords(panel);
        if (keywords != null) {
            for (int i = 0; i < keywords.size(); i++) {
                final Keyword k = keywords.get(i);
                final ConcisePanel pan = panel;
                NodeVtable vt = new NodeVtable();
                vt.controlType = ControlTypes.TEXT;
                vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        Eff eff = singleEffOrNull(pan, k);
                        return GameText.t(k.getColourTaggedString())
                                + ": " + GameText.t(k.getRules(eff));
                    }
                }, AnnouncementKinds.LABEL));
                b.addItem(ControlId.structural(CompositeKey.of("choosable-panel", "kw", i)), vt);
            }
        }
    }

    private static Choosable choosable(ConcisePanel panel) {
        try {
            if (choosableField == null) {
                choosableField = ConcisePanel.class.getDeclaredField("choosable");
                choosableField.setAccessible(true);
            }
            return (Choosable) choosableField.get(panel);
        } catch (Throwable t) {
            SndLog.error("failed to read ConcisePanel.choosable", t);
            return null;
        }
    }

    private static String fullDescription(ConcisePanel panel) {
        try {
            if (fullDescription == null) {
                fullDescription = ConcisePanel.class.getDeclaredMethod("getFullDescription");
                fullDescription.setAccessible(true);
            }
            return (String) fullDescription.invoke(panel);
        } catch (Throwable t) {
            SndLog.error("failed to read the panel's description", t);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Keyword> referencedKeywords(ConcisePanel panel) {
        try {
            if (referencedKeywords == null) {
                referencedKeywords = ConcisePanel.class.getDeclaredMethod("getReferencedKeywords");
                referencedKeywords.setAccessible(true);
            }
            return (List<Keyword>) referencedKeywords.invoke(panel);
        } catch (Throwable t) {
            SndLog.error("failed to read the panel's keywords", t);
            return null;
        }
    }

    private static Eff singleEffOrNull(ConcisePanel panel, Keyword k) {
        try {
            if (singleEffOrNull == null) {
                singleEffOrNull = ConcisePanel.class.getDeclaredMethod("getSingleEffOrNull", Keyword.class);
                singleEffOrNull.setAccessible(true);
            }
            return (Eff) singleEffOrNull.invoke(panel, k);
        } catch (Throwable t) {
            SndLog.error("failed to read the panel's keyword effect", t);
            return null;
        }
    }
}
