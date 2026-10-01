package snd.module;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;

import snd.core.input.InputAction;
import snd.core.input.InputRegistry;
import snd.core.nav.GraphNavigator;
import snd.core.nav.ScreenManager;
import snd.module.screens.HelpScreen;

/**
 * The mod's InputProcessor, kept at the HEAD of the game's InputMultiplexer
 * (reasserted every frame by the module tick, since the game rebuilds the
 * multiplexer in Main.setupScale). A press resolves through the key table
 * ({@link SndKeys}): navigator keys route to the navigator while an access
 * screen is attached, handler keys (the key help, the glances) run where
 * they apply, and everything unmatched or unconsumed falls through to the
 * game — its own hotkeys (1-9, R, Z, Space in combat, Escape's cog menu) keep
 * working. Escape is claimed only by a live search or a screen that takes it
 * ({@code AccessScreen.onCancel}).
 */
final class SndInput implements InputProcessor {
    private final ScreenManager screens;
    private final GraphNavigator nav;
    private final HelpScreen help;
    private final InputRegistry keys;

    SndInput(ScreenManager screens, GraphNavigator nav, HelpScreen help, InputRegistry keys) {
        this.screens = screens;
        this.nav = nav;
        this.help = help;
        this.keys = keys;
    }

    // While the game's own text input holds stage keyboard focus, every key
    // belongs to the field (typing, caret keys, Enter submit, Escape cancel)
    // — the navigator steps aside and TextEntryWatcher echoes what happens.
    static boolean textEntryActive() {
        return com.tann.dice.Main.stage != null
                && com.tann.dice.Main.stage.getKeyboardFocus()
                        instanceof com.tann.dice.util.ui.TextInput;
    }

    @Override
    public boolean keyDown(int keycode) {
        boolean shift = Gdx.input.isKeyPressed(59) || Gdx.input.isKeyPressed(60);
        boolean ctrl = Gdx.input.isKeyPressed(129) || Gdx.input.isKeyPressed(130);
        boolean alt = Gdx.input.isKeyPressed(57) || Gdx.input.isKeyPressed(58);
        return key(keycode, shift, ctrl, alt);
    }

    /** A key press with its modifier state given (the dev driver fakes held modifiers here). */
    boolean key(int keycode, boolean shift, boolean ctrl, boolean alt) {
        if (textEntryActive()) {
            return false;
        }
        int digit = digitOf(keycode);
        return run(keys.match(keycode, digit, shift, ctrl, alt), digit);
    }

    /**
     * The press of whichever chord is bound to this action, by the action's
     * id (the dev driver: no modifiers to fake). Null = no such action.
     */
    Boolean act(String id) {
        InputAction action = keys.find(id);
        if (action == null) {
            return null;
        }
        return !textEntryActive() && run(action, -1);
    }

    // One resolved press; action is null when no chord of ours matched.
    private boolean run(InputAction action, int digit) {
        help.keyPressed();
        boolean owns = screens.ownsKeyboard();
        // An overlay of the mod's own keeps every key from the game beneath.
        boolean exclusive = owns && screens.current().exclusive();
        if (action == null) {
            return exclusive;
        }
        if (action.nav != null) {
            return owns && (nav.onAction(action.nav) || exclusive);
        }
        if (exclusive && !action.worksOverOverlay()) {
            return true;
        }
        if (!action.isAvailable()) {
            // Does nothing where it does not apply, and stays ours: fallen
            // through, Ctrl+1 is the game's 1. A key the game binds where
            // ours does not apply is the game's there.
            return !action.isYieldingToGame();
        }
        action.perform(digit);
        return true;
    }

    // The game's own digit mapping (Tann.getDigit): NUM_1.. and NUMPAD_1..
    // are digit 0.., so "1" is the first combatant.
    private static int digitOf(int keycode) {
        if (keycode >= 8 && keycode <= 16) {
            return keycode - 8;
        }
        if (keycode >= 145 && keycode <= 153) {
            return keycode - 145;
        }
        return -1;
    }

    @Override
    public boolean keyTyped(char character) {
        if (textEntryActive() || !screens.ownsKeyboard()) {
            return false;
        }
        boolean wasActive = nav.searchActive();
        nav.typeChar(character);
        return wasActive || nav.searchActive() || screens.current().exclusive();
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
