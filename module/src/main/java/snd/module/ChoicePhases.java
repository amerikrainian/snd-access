package snd.module;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.ChoicePhase;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.ChoiceType;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.Choosable;

import snd.core.SndLog;

/**
 * Reflective access to ChoicePhase / ChoiceType internals. The offer, the
 * selection, and the choose/clear/end entry points are all package-private in
 * the game; every mutation here drives the phase's own code path — the same
 * one its number keys and clicks run — so behavior can never diverge from the
 * real UI.
 */
public final class ChoicePhases {
    private ChoicePhases() {
    }

    private static Field optionsField;
    private static Field choiceTypeField;
    private static Field topMessageField;
    private static Field choiceGroupField;
    private static Field styleField;
    private static Field targetValueField;
    private static Field currentChoicesField;
    private static Method tapForChoiceToggle;
    private static Method chooseMethod;
    private static Method clearChoicesMethod;
    private static Method endPhaseMethod;

    @SuppressWarnings("unchecked")
    public static List<Choosable> options(ChoicePhase p) {
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

    public static ChoiceType choiceType(ChoicePhase p) {
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

    public static String topMessage(ChoicePhase p) {
        try {
            if (topMessageField == null) {
                topMessageField = ChoicePhase.class.getDeclaredField("topMessage");
                topMessageField.setAccessible(true);
            }
            return (String) topMessageField.get(p);
        } catch (Throwable t) {
            SndLog.error("failed to read ChoicePhase.topMessage", t);
            return null;
        }
    }

    /** The offer's on-screen group — where the anticheese reroll button lives. */
    public static com.badlogic.gdx.scenes.scene2d.Group choiceGroup(ChoicePhase p) {
        try {
            if (choiceGroupField == null) {
                choiceGroupField = ChoicePhase.class.getDeclaredField("choiceGroup");
                choiceGroupField.setAccessible(true);
            }
            return (com.badlogic.gdx.scenes.scene2d.Group) choiceGroupField.get(p);
        } catch (Throwable t) {
            SndLog.error("failed to read ChoicePhase.choiceGroup", t);
            return null;
        }
    }

    /** The style enum's name: "Number", "UpToNumber", "PointBuy", "Optional". */
    public static String style(ChoiceType ct) {
        try {
            if (styleField == null) {
                styleField = ChoiceType.class.getDeclaredField("cs");
                styleField.setAccessible(true);
            }
            Object cs = styleField.get(ct);
            return cs != null ? cs.toString() : null;
        } catch (Throwable t) {
            SndLog.error("failed to read ChoiceType.cs", t);
            return null;
        }
    }

    /** The style's N: pick-count, up-to limit, or point-buy target tier. */
    public static int targetValue(ChoiceType ct) {
        try {
            if (targetValueField == null) {
                targetValueField = ChoiceType.class.getDeclaredField("v");
                targetValueField.setAccessible(true);
            }
            return targetValueField.getInt(ct);
        } catch (Throwable t) {
            SndLog.error("failed to read ChoiceType.v", t);
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Choosable> currentChoices(ChoiceType ct) {
        try {
            if (currentChoicesField == null) {
                currentChoicesField = ChoiceType.class.getDeclaredField("currentChoices");
                currentChoicesField.setAccessible(true);
            }
            List<Choosable> list = (List<Choosable>) currentChoicesField.get(ct);
            return list != null ? list : Collections.<Choosable>emptyList();
        } catch (Throwable t) {
            SndLog.error("failed to read ChoiceType.currentChoices", t);
            return Collections.emptyList();
        }
    }

    /** The digit-key/click route: toggles selection, auto-confirms Number style. */
    public static void toggle(ChoicePhase p, Choosable option) {
        try {
            if (tapForChoiceToggle == null) {
                tapForChoiceToggle = ChoicePhase.class.getDeclaredMethod("tapForChoiceToggle", Choosable.class);
                tapForChoiceToggle.setAccessible(true);
            }
            tapForChoiceToggle.invoke(p, option);
        } catch (Throwable t) {
            SndLog.error("failed to toggle option " + option.getSaveString(), t);
        }
    }

    /** The Confirm route: what the Confirm/tick buttons and dialogs run. */
    public static void choose(ChoicePhase p, List<Choosable> chosen, boolean confirm) {
        try {
            if (chooseMethod == null) {
                chooseMethod = ChoicePhase.class.getDeclaredMethod("choose", List.class, boolean.class);
                chooseMethod.setAccessible(true);
            }
            chooseMethod.invoke(p, chosen, confirm);
        } catch (Throwable t) {
            SndLog.error("ChoicePhase.choose failed", t);
        }
    }

    /** The point-buy reset button's route. */
    public static void clearChoices(ChoicePhase p) {
        try {
            if (clearChoicesMethod == null) {
                clearChoicesMethod = ChoicePhase.class.getDeclaredMethod("clearChoices");
                clearChoicesMethod.setAccessible(true);
            }
            clearChoicesMethod.invoke(p);
        } catch (Throwable t) {
            SndLog.error("ChoicePhase.clearChoices failed", t);
        }
    }

    /** The decline route of an Optional offer. */
    public static void endPhase(ChoicePhase p) {
        try {
            if (endPhaseMethod == null) {
                endPhaseMethod = ChoicePhase.class.getDeclaredMethod("endPhase");
                endPhaseMethod.setAccessible(true);
            }
            endPhaseMethod.invoke(p);
        } catch (Throwable t) {
            SndLog.error("ChoicePhase.endPhase failed", t);
        }
    }

    /**
     * The game's own header for an offer ("Choose a level-up", "Choose up to
     * 2 items", ...), translated. Null when the type has none (Optional).
     */
    public static String header(ChoicePhase p) {
        ChoiceType ct = choiceType(p);
        if (ct == null) {
            return null;
        }
        List<Choosable> options = options(p);
        // Hero level-up offers render with no header at all (makeTopText
        // returns null for them) and getDescription degrades to "Choose a
        // thing" — name them ourselves.
        if (!options.isEmpty() && options.get(0)
                instanceof com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.special.LevelupHeroChoosable) {
            return snd.core.loc.Loc.get("ui", "choice.levelup_header");
        }
        try {
            String description = ct.getDescription(options);
            if (description != null && !description.trim().isEmpty()) {
                return GameText.t(description);
            }
        } catch (Throwable t) {
            SndLog.error("ChoiceType.getDescription failed", t);
        }
        return null;
    }
}
