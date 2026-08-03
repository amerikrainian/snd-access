package snd.module;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.tann.dice.screens.Screen;
import com.tann.dice.util.Pair;
import com.tann.dice.util.listener.TannListener;
import com.tann.dice.util.ui.TextWriter;
import com.tann.dice.util.ui.standardButton.StandardButton;

import snd.core.SndLog;
import snd.core.loc.Loc;

/**
 * Scene2d bridge utilities: read and drive the game's own actors. Activation
 * goes through the game's listeners (TannListener.action — the exact code a
 * mouse click runs), so behavior can never diverge from the real UI.
 */
public final class GameUi {
    private GameUi() {
    }

    /**
     * The top pushed modal actor of the current game screen, or null. Only
     * blocker-backed pushes count: a null InputBlocker marks a "light" push —
     * inspection popups like the targeting Explanel that float over a still-
     * interactive screen — and reading those as modals would steal navigation
     * from the screen beneath. (The InventoryPanel is the one blockerless
     * push that IS a real panel; it gets its own screen when equip lands.)
     */
    public static Actor topModal() {
        com.badlogic.gdx.scenes.scene2d.Actor top = null;
        Screen screen;
        try {
            screen = com.tann.dice.Main.getCurrentScreen();
        } catch (Throwable t) {
            return null;
        }
        if (screen == null) {
            return null;
        }
        List<Pair<Actor, ?>> stack = castStack(screen.modalStack);
        for (Pair<Actor, ?> pair : stack) {
            if (pair.a != null && pair.a.getStage() != null && pair.b != null) {
                top = pair.a;
            }
        }
        return top;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Pair<Actor, ?>> castStack(List stack) {
        return (List<Pair<Actor, ?>>) stack;
    }

    /**
     * Left-click the actor the way the game does: TannListener.action on
     * EVERY gesture listener (a real tap reaches them all — StandardButtons
     * carry an internal runnable listener plus any added handler), else a
     * synthesized touchDown/touchUp pair for plain InputListeners.
     */
    public static boolean activate(Actor actor) {
        if (actor == null) {
            return false;
        }
        boolean any = false;
        for (com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener l : gestureListeners(actor)) {
            if (l instanceof TannListener) {
                any = true;
                ((TannListener) l).action(0, 0, actor.getWidth() / 2f, actor.getHeight() / 2f);
            }
        }
        return any || fireClick(actor);
    }

    /** Right-click equivalent: the game's info() surface (details panels). */
    public static boolean info(Actor actor) {
        if (actor == null) {
            return false;
        }
        for (ActorGestureListener l : gestureListeners(actor)) {
            if (l instanceof TannListener) {
                return ((TannListener) l).info(1, actor.getWidth() / 2f, actor.getHeight() / 2f);
            }
        }
        return false;
    }

    public static boolean hasTannListener(Actor actor) {
        return !gestureListeners(actor).isEmpty();
    }

    private static List<ActorGestureListener> gestureListeners(Actor actor) {
        List<ActorGestureListener> result = new ArrayList<ActorGestureListener>();
        for (com.badlogic.gdx.scenes.scene2d.EventListener l : actor.getListeners()) {
            if (l instanceof ActorGestureListener) {
                result.add((ActorGestureListener) l);
            }
        }
        return result;
    }

    private static boolean fireClick(Actor actor) {
        if (actor.getStage() == null) {
            return false;
        }
        com.badlogic.gdx.math.Vector2 pos =
                actor.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(
                        actor.getWidth() / 2f, actor.getHeight() / 2f));
        InputEvent down = new InputEvent();
        down.setType(InputEvent.Type.touchDown);
        down.setStage(actor.getStage());
        down.setStageX(pos.x);
        down.setStageY(pos.y);
        down.setPointer(0);
        down.setButton(0);
        boolean handled = actor.fire(down);
        InputEvent up = new InputEvent();
        up.setType(InputEvent.Type.touchUp);
        up.setStage(actor.getStage());
        up.setStageX(pos.x);
        up.setStageY(pos.y);
        up.setPointer(0);
        up.setButton(0);
        actor.fire(up);
        return handled;
    }

    /** All raw TextWriter texts under an actor, in child order. */
    public static List<String> textsUnder(Actor actor) {
        List<String> texts = new ArrayList<String>();
        collectTexts(actor, texts);
        return texts;
    }

    private static void collectTexts(Actor actor, List<String> out) {
        if (actor instanceof TextWriter) {
            String text = ((TextWriter) actor).text;
            if (text != null && !text.trim().isEmpty()) {
                out.add(text);
            }
            return;
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                collectTexts(child, out);
            }
        }
    }

    /** A button-ish actor's label: its own text, else its TextWriters joined. */
    public static String labelOf(Actor actor) {
        if (actor instanceof StandardButton) {
            String text = ((StandardButton) actor).getText();
            if (text != null && !text.trim().isEmpty()) {
                return text;
            }
        }
        List<String> texts = textsUnder(actor);
        if (!texts.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String t : texts) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(t);
            }
            return sb.toString();
        }
        return null;
    }

    // ---- icon naming: the game's icon-only buttons, named by their texture.
    // The map holds locale KEYS; the word resolves at read time so it follows
    // the live language. ----

    private static java.util.Map<Object, String> knownIcons;

    private static java.util.Map<Object, String> knownIcons() {
        if (knownIcons == null) {
            java.util.Map<Object, String> m = new java.util.IdentityHashMap<Object, String>();
            m.put(com.tann.dice.statics.Images.almanac, "icon.almanac");
            m.put(com.tann.dice.statics.Images.cog, "icon.menu");
            m.put(com.tann.dice.statics.Images.back, "icon.back");
            m.put(com.tann.dice.statics.Images.globe, "icon.language");
            m.put(com.tann.dice.statics.Images.padlock, "icon.locked");
            m.put(com.tann.dice.statics.Images.searchIcon, "icon.search");
            m.put(com.tann.dice.statics.Images.zoom2, "icon.expand");
            m.put(com.tann.dice.statics.Images.singleDie, "icon.reroll");
            m.put(com.tann.dice.statics.Images.reroll, "icon.reroll");
            m.put(com.tann.dice.statics.Images.ui_crossAlmanac, "icon.close");
            knownIcons = m;
        }
        return knownIcons;
    }

    /** The name of a known game icon under this actor, or null. */
    public static String iconNameUnder(Actor actor) {
        if (actor instanceof com.tann.dice.util.ImageActor) {
            String key = knownIcons().get(((com.tann.dice.util.ImageActor) actor).tr);
            return key != null ? Loc.get("ui", key) : null;
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                String name = iconNameUnder(child);
                if (name != null) {
                    return name;
                }
            }
        }
        return null;
    }

    // ---- confirm button: its state enum owns the game's "Done Rolling" /
    // "End turn" text in a package-private field; read it reflectively so the
    // spoken label is exactly the game's, translated ----

    private static Field confirmTextField;

    public static String confirmLabel(com.tann.dice.screens.dungeon.panels.ConfirmButton button) {
        try {
            if (confirmTextField == null) {
                Field f = com.tann.dice.screens.dungeon.panels.ConfirmButton.ConfirmState.class
                        .getDeclaredField("confirmText");
                f.setAccessible(true);
                confirmTextField = f;
            }
            String text = (String) confirmTextField.get(button.getConfirmState());
            return text != null ? com.tann.dice.Main.t(text) : null;
        } catch (Throwable t) {
            SndLog.error("confirm label read failed", t);
            return null;
        }
    }

    // ---- slider bridge: the game's Slider is drag-only; drive its own
    // update path (private setValue + the slideAction that feeds the option
    // and saves) so keyboard adjust behaves exactly like a drag ----

    private static Field sliderTitleField;
    private static Field sliderSlideActionField;
    private static java.lang.reflect.Method sliderSetValue;

    private static void initSliderReflection() throws Exception {
        if (sliderSetValue == null) {
            Class<?> cls = com.tann.dice.util.Slider.class;
            sliderTitleField = cls.getDeclaredField("title");
            sliderTitleField.setAccessible(true);
            sliderSlideActionField = cls.getDeclaredField("slideAction");
            sliderSlideActionField.setAccessible(true);
            sliderSetValue = cls.getDeclaredMethod("setValue", float.class);
            sliderSetValue.setAccessible(true);
        }
    }

    public static String sliderTitle(com.tann.dice.util.Slider slider) {
        try {
            initSliderReflection();
            Object title = sliderTitleField.get(slider);
            return title != null ? title.toString() : Loc.get("ui", "role.slider");
        } catch (Throwable t) {
            SndLog.error("slider title read failed", t);
            return Loc.get("ui", "role.slider");
        }
    }

    /** 0..100, the spoken value. */
    public static int sliderPercent(com.tann.dice.util.Slider slider) {
        return Math.round(slider.getValue() * 100f);
    }

    public static void sliderAdjust(com.tann.dice.util.Slider slider, int sign, boolean large) {
        try {
            initSliderReflection();
            float step = large ? 0.2f : 0.05f;
            float value = Math.max(0f, Math.min(1f, slider.getValue() + sign * step));
            sliderSetValue.invoke(slider, value);
            Runnable slideAction = (Runnable) sliderSlideActionField.get(slider);
            if (slideAction != null) {
                slideAction.run(); // the game's own path: option value + save
            }
        } catch (Throwable t) {
            SndLog.error("slider adjust failed", t);
        }
    }

    /** The (package-private) ModesPanel on the live title screen, or null. */
    public static com.tann.dice.screens.titleScreen.ModesPanel modesPanel() {
        try {
            Screen screen = com.tann.dice.Main.getCurrentScreen();
            if (!(screen instanceof com.tann.dice.screens.titleScreen.TitleScreen)) {
                return null;
            }
            Field field = modesPanelField();
            return (com.tann.dice.screens.titleScreen.ModesPanel) field.get(screen);
        } catch (Throwable t) {
            SndLog.error("failed to read TitleScreen.modesPanel", t);
            return null;
        }
    }

    private static Field modesPanelFieldCache;

    private static Field modesPanelField() throws NoSuchFieldException {
        if (modesPanelFieldCache == null) {
            Field field = com.tann.dice.screens.titleScreen.TitleScreen.class.getDeclaredField("modesPanel");
            field.setAccessible(true);
            modesPanelFieldCache = field;
        }
        return modesPanelFieldCache;
    }

}
