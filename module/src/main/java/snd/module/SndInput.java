package snd.module;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;

import snd.core.nav.GraphNavigator;
import snd.core.nav.NavAction;
import snd.core.nav.ScreenManager;

/**
 * The mod's InputProcessor, kept at the HEAD of the game's InputMultiplexer
 * (reasserted every frame by the module tick, since the game rebuilds the
 * multiplexer in Main.setupScale). While an access screen is attached, nav
 * keys route to the navigator; everything unconsumed falls through to the
 * game — its own hotkeys (1-9, R, Z, Space in combat, Escape's cog menu) keep
 * working. Never claims Escape unless a search is live.
 */
final class SndInput implements InputProcessor {
    private final ScreenManager screens;
    private final GraphNavigator nav;

    SndInput(ScreenManager screens, GraphNavigator nav) {
        this.screens = screens;
        this.nav = nav;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (!screens.ownsKeyboard()) {
            return false;
        }
        boolean shift = Gdx.input.isKeyPressed(59) || Gdx.input.isKeyPressed(60);
        boolean ctrl = Gdx.input.isKeyPressed(129) || Gdx.input.isKeyPressed(130);
        NavAction action;
        switch (keycode) {
            case 19: // UP
                action = ctrl ? NavAction.REGION_PREV : NavAction.UP;
                break;
            case 20: // DOWN
                action = ctrl ? NavAction.REGION_NEXT : NavAction.DOWN;
                break;
            case 21: // LEFT
                action = NavAction.LEFT;
                break;
            case 22: // RIGHT
                action = NavAction.RIGHT;
                break;
            case 61: // TAB
                action = shift ? NavAction.PREV_STOP : NavAction.NEXT_STOP;
                break;
            case 3: // HOME
                action = NavAction.HOME;
                break;
            case 123: // END
                action = NavAction.END;
                break;
            case 66: // ENTER
            case 160: // NUMPAD_ENTER
                action = NavAction.ACTIVATE;
                break;
            case 67: // BACKSPACE
                action = NavAction.SECONDARY;
                break;
            case 62: // SPACE
                action = NavAction.TOOLTIP;
                break;
            case 111: // ESCAPE — consumed only when a live search needs cancelling
                action = NavAction.CANCEL;
                break;
            default:
                return false;
        }
        return nav.onAction(action);
    }

    @Override
    public boolean keyTyped(char character) {
        if (!screens.ownsKeyboard()) {
            return false;
        }
        boolean wasActive = nav.searchActive();
        nav.typeChar(character);
        return wasActive || nav.searchActive();
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }
}
