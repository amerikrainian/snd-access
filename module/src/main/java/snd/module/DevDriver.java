package snd.module;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;

import snd.core.SndLog;

/**
 * Game-touching dev-driver verbs, module-side: the host routes /input and
 * /screenshot here via devCommand because the shipped shim loads the game
 * outside the system loader — only module code (parented on the game's
 * loader) can link against it. Render-thread-only; the dev server marshals.
 */
final class DevDriver {
    private DevDriver() {
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
        VERBS.put("f1", 131);
        for (int d = 1; d <= 9; d++) {
            VERBS.put(String.valueOf(d), 7 + d); // Input.Keys.NUM_1..NUM_9 = 8..16
        }
    }

    /**
     * Drives the FULL input chain — our navigator's processor at the
     * multiplexer head, then the game's stage — exactly what a physical
     * keypress reaches. "type:<text>" feeds keyTyped (the type-ahead path).
     */
    static String input(String verb, SndInput input) {
        verb = verb.trim();
        InputProcessor proc = Gdx.input.getInputProcessor();
        if (proc == null) {
            return "no input processor yet";
        }
        // "ctrl+", "shift+" prefixes (any order): the processor path reads
        // live key state and can't fake held modifiers, so a modified verb
        // goes to the mod's own key path with the modifiers given. A chord
        // the mod leaves unconsumed stops there — the game reads its
        // modifiers from the live keyboard too.
        boolean shift = false;
        boolean ctrl = false;
        String rest = verb.toLowerCase();
        while (rest.startsWith("shift+") || rest.startsWith("ctrl+")) {
            if (rest.startsWith("shift+")) {
                shift = true;
                rest = rest.substring(6);
            } else {
                ctrl = true;
                rest = rest.substring(5);
            }
        }
        if (shift || ctrl) {
            Integer chordCode = VERBS.get(rest);
            if (chordCode == null) {
                return "unknown key '" + rest + "' in " + verb;
            }
            return "ok: " + verb + " -> keycode " + chordCode + " consumed=" + input.key(chordCode, shift, ctrl)
                    + " (mod key path only)";
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

    static String screenshot(String path) {
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
    static void ensureContinuousRendering() {
        try {
            if (Gdx.graphics != null && !Gdx.graphics.isContinuousRendering()) {
                Gdx.graphics.setContinuousRendering(true);
                SndLog.info("dev: forced continuous rendering");
            }
        } catch (Throwable t) {
            SndLog.error("ensureContinuousRendering failed", t);
        }
    }
}
