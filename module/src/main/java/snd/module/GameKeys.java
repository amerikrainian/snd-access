package snd.module;

import java.util.List;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.tann.dice.screens.Screen;
import com.tann.dice.screens.generalPanels.InventoryPanel;
import com.tann.dice.util.InputBlocker;
import com.tann.dice.util.Pair;

import snd.core.input.KeyChord;
import snd.core.loc.Loc;
import snd.core.nav.KeyOffer;

/**
 * The game's own hotkeys, as the key help offers them: screens list the ones
 * live in the current game state, labelled with the game's words for them
 * (its Tips page: "roll dice", "undo", "menu"). Running one presses the key
 * through {@code Screen.mainKeyPress} — the stage listener's route.
 */
public final class GameKeys {
    private GameKeys() {
    }

    public static final int ESCAPE = 111;
    public static final int SPACE = 62;
    public static final int I = 37;
    public static final int R = 46;
    public static final int Z = 54;

    // TargetingPhase.spellInts: the ability bar's slot keys, in slot order.
    private static final String[] ABILITY_KEYS = {"Q", "W", "E", "R", "T", "Y", "U", "I"};

    /** The ability bar holds as many as the game has keys for (TargetingPhase.spellInts). */
    public static final int ABILITY_SLOTS = ABILITY_KEYS.length;

    public static void press(int keycode) {
        com.tann.dice.Main.getCurrentScreen().mainKeyPress(keycode);
    }

    /** A single key: listed, and runnable from the help. */
    public static KeyOffer key(String id, String keys, String label, final int keycode) {
        return new KeyOffer(id, keys, label, new Runnable() {
            @Override
            public void run() {
                press(keycode);
            }
        });
    }

    /** A key range (the digits, the ability letters): listed only. */
    public static KeyOffer range(String id, String keys, String label) {
        return new KeyOffer(id, keys, label, null);
    }

    /** "1 to n", capped at the nine digit keys; "1" for a single one. */
    public static String digits(int n) {
        return KeyChord.digitsName(n);
    }

    public static String shiftDigits(int n) {
        return Loc.get("ui", "key.shift", "key", digits(n));
    }

    /** For a screen whose one game key is Escape. */
    public static List<KeyOffer> escapeOnly() {
        return java.util.Collections.singletonList(escape());
    }

    /** The slot keys of the given ability-bar slots. */
    public static String abilityKeys(List<Integer> slots) {
        StringBuilder sb = new StringBuilder();
        for (int slot : slots) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(ABILITY_KEYS[slot]);
        }
        return sb.toString();
    }

    /**
     * Escape, by what it does now ({@code Screen.genericKeyPress}): it pops
     * the light and medium modals when any is up, else opens the cog menu.
     */
    public static KeyOffer escape() {
        String label = escapePops() ? Loc.get("ui", "help.close") : GameText.t("menu");
        return key("escape", Loc.get("ui", "key.escape"), label, ESCAPE);
    }

    // Screen.popSingleMedium's own test, asked without popping.
    private static boolean escapePops() {
        Screen screen = com.tann.dice.Main.getCurrentScreen();
        List<Pair<Actor, InputBlocker>> stack = screen.modalStack;
        if (stack.isEmpty()) {
            return false;
        }
        Pair<Actor, InputBlocker> top = stack.get(stack.size() - 1);
        return top.b == null ? !(top.a instanceof InventoryPanel) : top.b.isMedium();
    }
}
