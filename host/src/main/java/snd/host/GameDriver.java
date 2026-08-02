package snd.host;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import snd.core.SndLog;

/**
 * The host's few direct touches on the game, all render-thread-only (callers
 * marshal via Dispatcher.post). Compiled against dice.jar; these classes are
 * always present at runtime (the game is the app).
 */
public final class GameDriver {
    private GameDriver() {
    }

    private static final Map<String, Integer> VERBS = new HashMap<String, Integer>();

    static {
        VERBS.put("up", 19);
        VERBS.put("down", 20);
        VERBS.put("left", 21);
        VERBS.put("right", 22);
        VERBS.put("enter", 66);
        VERBS.put("escape", 111);
        VERBS.put("space", 62);
        VERBS.put("tab", 61);
        VERBS.put("backspace", 67);
        VERBS.put("home", 3);
        VERBS.put("end", 123);
        VERBS.put("z", 54);
        VERBS.put("r", 46);
        VERBS.put("i", 37);
        for (int d = 1; d <= 9; d++) {
            VERBS.put(String.valueOf(d), 7 + d); // Input.Keys.NUM_1..NUM_9 = 8..16
        }
    }

    /**
     * Drives the FULL input chain — the installed InputProcessor (our
     * navigator's processor at the multiplexer head, then the game's stage) —
     * exactly what a physical keypress reaches. "type:<text>" feeds keyTyped
     * (the type-ahead path).
     */
    public static String input(String verb) {
        verb = verb.trim();
        InputProcessor proc = Gdx.input.getInputProcessor();
        if (proc == null) {
            return "no input processor yet";
        }
        if (verb.startsWith("type:")) {
            String text = verb.substring(5);
            for (int i = 0; i < text.length(); i++) {
                proc.keyTyped(text.charAt(i));
            }
            return "typed: " + text;
        }
        Integer code;
        if (verb.startsWith("key:")) {
            try {
                code = Integer.parseInt(verb.substring(4).trim());
            } catch (NumberFormatException e) {
                return "bad keycode: " + verb;
            }
        } else {
            code = VERBS.get(verb.toLowerCase());
        }
        if (code == null) {
            return "unknown verb '" + verb + "' (use " + VERBS.keySet() + ", type:<text>, or key:<code>)";
        }
        boolean consumed = proc.keyDown(code);
        proc.keyUp(code);
        return "ok: " + verb + " -> keycode " + code + " consumed=" + consumed;
    }

    public static String screenshot(String path) {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, w, h);
        try {
            PixmapIO.writePNG(Gdx.files.absolute(path), pixmap, -1, true);
        } finally {
            pixmap.dispose();
        }
        return path;
    }

    /**
     * The game defaults to non-continuous rendering (frames only on input /
     * requestRendering), which would starve dev-server jobs and long-poll
     * clients. While the dev server is up, keep the loop rendering; the game
     * re-applies its own option during load, so re-assert every frame.
     */
    public static void ensureContinuousRendering() {
        try {
            if (Gdx.graphics != null && !Gdx.graphics.isContinuousRendering()) {
                Gdx.graphics.setContinuousRendering(true);
                SndLog.info("dev: forced continuous rendering");
            }
        } catch (Throwable t) {
            SndLog.error("ensureContinuousRendering failed", t);
        }
    }

    /** Wakes a possibly-idle non-continuous render loop; safe from any thread. */
    public static void requestRender() {
        try {
            if (Gdx.graphics != null) {
                Gdx.graphics.requestRendering();
            }
        } catch (Throwable ignored) {
            // pre-boot; the forced-continuous frame hook takes over shortly
        }
    }

    /** Fallback /gui when the module doesn't answer: name the current screen. */
    public static String describeScreen() {
        com.tann.dice.screens.Screen screen = com.tann.dice.Main.getCurrentScreen();
        return screen == null ? "no screen" : "screen: " + screen.getClass().getName();
    }
}
