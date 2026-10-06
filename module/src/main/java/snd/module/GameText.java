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

    /**
     * An item's rules as its panel draws them (ItemPanel.getFullDescription),
     * built now and so in the current language — already translated, never
     * pass it through {@link #t}. Item.getDescription() is the same text fixed
     * when the item was built: in the language of that moment, or English for
     * one the game's pipes built with the translator disabled.
     */
    public static String itemDescription(com.tann.dice.gameplay.content.item.Item item) {
        return com.tann.dice.gameplay.trigger.Trigger.describeTriggers(
                new java.util.ArrayList<com.tann.dice.gameplay.trigger.personal.Personal>(item.getPersonals()));
    }
}
