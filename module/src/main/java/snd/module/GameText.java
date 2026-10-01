package snd.module;

/**
 * Speech-side bridge to the game's own Translator. Model-sourced text
 * (Mode.getName(), item and modifier descriptions, difficulty rules) is
 * English source that the game translates at DISPLAY time — so anything read
 * from the model rather than from a rendered actor goes through here before
 * speaking, and a non-English player hears their language. Actor text
 * (TextWriter.text) is already translated at set time; don't bridge it twice.
 */
public final class GameText {
    private GameText() {
    }

    public static String t(String text) {
        if (text == null) {
            return null;
        }
        com.tann.dice.Main main = com.tann.dice.Main.self();
        if (main == null || main.translator == null) {
            return text;
        }
        return main.translator.translate(text);
    }
}
