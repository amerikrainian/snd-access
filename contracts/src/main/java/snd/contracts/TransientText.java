package snd.contracts;

/**
 * One line of the game's transient text, as a hook queued it: which game
 * method showed it (its name, "showInfo", "addSpeechBubble"), the unit it
 * was shown over (null for text not over a unit), and the text.
 */
public final class TransientText {
    public final String method;
    public final String speaker;
    public final String text;

    public TransientText(String method, String speaker, String text) {
        this.method = method;
        this.speaker = speaker;
        this.text = text;
    }
}
