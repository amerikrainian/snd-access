package snd.module.screens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.tann.dice.gameplay.content.ent.type.EntType;
import com.tann.dice.gameplay.content.item.Item;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.Choosable;
import com.tann.dice.util.ui.choice.CDChoice;
import com.tann.dice.util.ui.choice.ChoiceDialog;

import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.module.Captured;
import snd.module.GameText;

/**
 * The parts the game's event dialogs are made of, read from what the dialog
 * shows rather than how it is drawn: a line of text, a choosable (an item, a
 * modifier, a hero) read as the choice screen reads its options, and the
 * dialog's own answers.
 */
final class DialogNodes {
    private DialogNodes() {
    }

    static void text(GraphBuilder b, Object key, final String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TEXT;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return text;
            }
        }, AnnouncementKinds.LABEL));
        b.addItem(ControlId.structural(CompositeKey.of("dialog", key)), vt);
    }

    /**
     * A choosable as an offer reads: its name, its kind and tier, its effect,
     * with the rules of the keywords it uses to step through. It concerns
     * what it is (an item, a hero or monster type), so the buffers follow it.
     */
    static void choosable(GraphBuilder b, Object key, final Choosable option) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TEXT;
        vt.subject = option instanceof Item || option instanceof EntType ? option : null;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return ChoiceScreen.nameOf(option);
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return ChoiceScreen.valueOf(option, -1);
                    }
                }, AnnouncementKinds.VALUE),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return ChoiceScreen.effectOf(option, -1);
                    }
                }, AnnouncementKinds.TOOLTIP));
        vt.details = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return new ArrayList<String>(ChoiceScreen.termsOf(option));
            }
        };
        b.addItem(ControlId.referenced(option, CompositeKey.of("dialog", key)), vt);
    }

    /** The dialog's answers in its order, each pressing the answer's own runnable. */
    static void answers(GraphBuilder b, ChoiceDialog dialog) {
        Object[] choices = (Object[]) Captured.field(dialog, ChoiceDialog.class, "choices");
        if (choices == null) {
            return; // logged by the field read
        }
        for (int i = 0; i < choices.length; i++) {
            Object name = Captured.field(choices[i], CDChoice.class, "name");
            Object onClick = Captured.field(choices[i], CDChoice.class, "onClick");
            if (name != null && onClick != null) {
                button(b, CompositeKey.of("answer", i), GameText.t((String) name), (Runnable) onClick);
            }
        }
    }

    static void button(GraphBuilder b, Object key, final String label, Runnable run) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return label;
            }
        }, AnnouncementKinds.LABEL));
        vt.onActivate = run;
        b.addItem(ControlId.structural(CompositeKey.of("dialog", key)), vt);
    }
}
