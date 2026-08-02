package snd.module.screens;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.ChoicePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.ChoiceType;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.Choosable;

import snd.core.SndLog;
import snd.core.loc.Loc;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.nav.AccessScreen;
import snd.module.GameText;

/**
 * The reward/decision offer (ChoicePhase): the starting difficulty modifier
 * pick, level-up and loot choices, curse and blessing picks. The offer is
 * drawn straight onto the dungeon screen rather than pushed as a modal, so it
 * gets its own screen rather than riding the generic modal reader.
 *
 * <p>Each option speaks its own calculated description — the game's
 * {@code Choosable.describe()}, the same text the visual panels render — and
 * activating one runs the phase's own toggle (the exact path its number-key
 * shortcut and a click both take, including the confirmation dialog, which
 * the modal screen then reads).</p>
 */
public class ChoiceScreen extends AccessScreen {
    @Override
    public String key() {
        return "choice";
    }

    @Override
    public int layer() {
        return 10; // above the (future) dungeon screen, below pushed modals
    }

    @Override
    public boolean isActive() {
        return phase() != null;
    }

    private static ChoicePhase phase() {
        try {
            if (com.tann.dice.Main.getCurrentScreen() == null) {
                return null;
            }
            com.tann.dice.gameplay.phase.Phase p = PhaseManager.get().getPhase();
            return p instanceof ChoicePhase ? (ChoicePhase) p : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String screenName() {
        ChoicePhase p = phase();
        if (p == null) {
            return null;
        }
        // The game's own header ("Choose a curse", "Choose 2 items", ...).
        ChoiceType type = choiceType(p);
        List<Choosable> options = options(p);
        if (type != null) {
            try {
                String description = type.getDescription(options);
                if (description != null && !description.trim().isEmpty()) {
                    return GameText.t(description);
                }
            } catch (Throwable t) {
                SndLog.error("ChoiceType.getDescription failed", t);
            }
        }
        return Loc.get("ui", "choice.header");
    }

    @Override
    public void build(GraphBuilder b) {
        final ChoicePhase p = phase();
        if (p == null) {
            return;
        }
        List<Choosable> options = options(p);
        for (int i = 0; i < options.size(); i++) {
            final Choosable option = options.get(i);
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.BUTTON;
            vt.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return nameOf(option);
                        }
                    }, AnnouncementKinds.LABEL),
                    // Choosable.describe() is only the TYPE word ("curse",
                    // "item"); the identity and effect come from the thing
                    // itself.
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            String type = safeDescribe(option);
                            int tier = option.getTier();
                            String tierText = tier == 0 ? null : Loc.get("ui", "choice.tier", "tier", tier);
                            if (type == null) {
                                return tierText;
                            }
                            return tierText == null ? type : type + ", " + tierText;
                        }
                    }, AnnouncementKinds.VALUE),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return effectOf(option);
                        }
                    }, AnnouncementKinds.TOOLTIP));
            vt.onActivate = new Runnable() {
                @Override
                public void run() {
                    choose(p, option);
                }
            };
            b.addItem(ControlId.referenced(option, CompositeKey.of("choice", i, option.getSaveString())), vt);
        }
    }

    // An option's real identity and effect. Every Choosable.describe() returns
    // only its type word, so read the underlying thing: modifiers and items
    // carry a name and a generated effect description (the same text their
    // panels render).
    private static String nameOf(Choosable option) {
        try {
            if (option instanceof com.tann.dice.gameplay.modifier.Modifier) {
                return GameText.t(((com.tann.dice.gameplay.modifier.Modifier) option).getName());
            }
            if (option instanceof com.tann.dice.gameplay.content.item.Item) {
                return GameText.t(((com.tann.dice.gameplay.content.item.Item) option).getName());
            }
            String save = option.getSaveString();
            if (save != null && !save.trim().isEmpty()) {
                return save;
            }
            return safeDescribe(option);
        } catch (Throwable t) {
            SndLog.error("failed to name a choice option", t);
            return Loc.get("ui", "choice.unreadable");
        }
    }

    private static String effectOf(Choosable option) {
        try {
            if (option instanceof com.tann.dice.gameplay.modifier.Modifier) {
                return GameText.t(((com.tann.dice.gameplay.modifier.Modifier) option).getFullDescription());
            }
            if (option instanceof com.tann.dice.gameplay.content.item.Item) {
                return GameText.t(((com.tann.dice.gameplay.content.item.Item) option).getDescription());
            }
            return null;
        } catch (Throwable t) {
            SndLog.error("failed to describe a choice option", t);
            return null;
        }
    }

    private static String safeDescribe(Choosable option) {
        try {
            return GameText.t(option.describe());
        } catch (Throwable t) {
            SndLog.error("Choosable.describe failed", t);
            return null;
        }
    }

    // The phase's own choose path — what its number-key shortcut and a click
    // on the option's panel both run (private, so reflected; the game's
    // number keys only reach the first nine options, and offers can be
    // larger).
    private static java.lang.reflect.Method tapForChoiceToggle;

    private static void choose(ChoicePhase p, Choosable option) {
        try {
            if (tapForChoiceToggle == null) {
                tapForChoiceToggle = ChoicePhase.class.getDeclaredMethod("tapForChoiceToggle", Choosable.class);
                tapForChoiceToggle.setAccessible(true);
            }
            tapForChoiceToggle.invoke(p, option);
        } catch (Throwable t) {
            SndLog.error("failed to choose option " + option.getSaveString(), t);
        }
    }

    // ChoicePhase keeps its offer in package-private fields; read them rather
    // than scraping the drawn panels, so every option is exact game data.
    private static Field optionsField;
    private static Field choiceTypeField;

    @SuppressWarnings("unchecked")
    private static List<Choosable> options(ChoicePhase p) {
        try {
            if (optionsField == null) {
                optionsField = ChoicePhase.class.getDeclaredField("options");
                optionsField.setAccessible(true);
            }
            List<Choosable> list = (List<Choosable>) optionsField.get(p);
            return list != null ? list : Collections.<Choosable>emptyList();
        } catch (Throwable t) {
            SndLog.error("failed to read ChoicePhase.options", t);
            return Collections.emptyList();
        }
    }

    private static ChoiceType choiceType(ChoicePhase p) {
        try {
            if (choiceTypeField == null) {
                choiceTypeField = ChoicePhase.class.getDeclaredField("choiceType");
                choiceTypeField.setAccessible(true);
            }
            return (ChoiceType) choiceTypeField.get(p);
        } catch (Throwable t) {
            SndLog.error("failed to read ChoicePhase.choiceType", t);
            return null;
        }
    }
}
