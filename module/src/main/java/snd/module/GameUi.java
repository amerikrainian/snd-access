package snd.module;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.utils.Array;
import com.tann.dice.screens.Screen;
import com.tann.dice.util.Pair;
import com.tann.dice.util.listener.TannListener;
import com.tann.dice.util.ui.TextWriter;
import com.tann.dice.util.ui.standardButton.StandardButton;

import snd.core.SndLog;

/**
 * Scene2d bridge utilities: read and drive the game's own actors. Activation
 * goes through the game's listeners (TannListener.action — the exact code a
 * mouse click runs), so behavior can never diverge from the real UI.
 */
final class GameUi {
    private GameUi() {
    }

    /** The top pushed modal actor of the current game screen, or null. */
    static Actor topModal() {
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
            if (pair.a != null && pair.a.getStage() != null) {
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
     * Left-click the actor the way the game does: TannListener.action first
     * (the game's own handler), else a synthesized touchDown/touchUp pair for
     * plain InputListeners.
     */
    static boolean activate(Actor actor) {
        if (actor == null) {
            return false;
        }
        for (com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener l : gestureListeners(actor)) {
            if (l instanceof TannListener) {
                ((TannListener) l).action(0, 0, actor.getWidth() / 2f, actor.getHeight() / 2f);
                return true;
            }
        }
        return fireClick(actor);
    }

    /** Right-click equivalent: the game's info() surface (details panels). */
    static boolean info(Actor actor) {
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

    static boolean hasTannListener(Actor actor) {
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
    static List<String> textsUnder(Actor actor) {
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
    static String labelOf(Actor actor) {
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

    /** The (package-private) ModesPanel on the live title screen, or null. */
    static com.tann.dice.screens.titleScreen.ModesPanel modesPanel() {
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

    /** Non-null, game-markup-preserving array helper for gdx Array iteration. */
    static <T> List<T> toList(Array<T> array) {
        List<T> list = new ArrayList<T>(array.size);
        for (T t : array) {
            list.add(t);
        }
        return list;
    }
}
