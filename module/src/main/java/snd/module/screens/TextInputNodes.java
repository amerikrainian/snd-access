package snd.module.screens;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.TextInputListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.screens.dungeon.panels.Explanel.EntPanelInventory;
import com.tann.dice.gameplay.mode.Mode;
import com.tann.dice.gameplay.mode.creative.custom.CustomMode;
import com.tann.dice.gameplay.mode.creative.pastey.PasteMode;
import com.tann.dice.screens.dungeon.panels.book.page.helpPage.HelpPage;
import com.tann.dice.util.listener.TannListener;
import com.tann.dice.util.online.BugReport;
import com.tann.dice.util.ui.TextInput;
import com.tann.dice.util.ui.TextInputField;
import com.tann.dice.util.ui.resolver.Resolver;

import snd.contracts.SndLog;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.core.text.CaretMove;
import snd.core.text.TextField;
import snd.module.Captured;
import snd.module.GameText;
import snd.module.GameUi;

/**
 * The game's text entry panel (DesktopControl.textInput, the only builder of
 * a TextInput): a title, the field, clear and ok. It is one control, the
 * field. While the field holds the keyboard every key is its own (typing,
 * the caret keys, Enter for ok, Escape to cancel), so nothing else on the
 * panel can be reached and none of it is listed. What the field is for comes
 * from the listener it reports to, which the game method that opened it
 * built: a hero's rename knows its hero, a search its resolver. The panel's
 * title is the game's argument to that method and kept nowhere; for a
 * builder read here, it is the game's own string for it.
 */
public final class TextInputNodes {
    private TextInputNodes() {
    }

    // TextInput.notify's caret keys: Home and End, Left and Right, which jump
    // words while the game's Ctrl (Ctrl-left or Sym) is down. Up and Down,
    // which the field ignores, read the whole line, as in any single-line edit.
    private static final int UP = 19;
    private static final int DOWN = 20;
    private static final int HOME = 3;
    private static final int END = 123;
    private static final int LEFT = 21;
    private static final int RIGHT = 22;
    private static final int CONTROL_LEFT = 129;
    private static final int SYM = 63;

    /** How the press of this key reads in a focused field, by the game's own reading of it. */
    public static CaretMove caretMove(int keycode) {
        if (keycode == UP || keycode == DOWN) {
            return CaretMove.LINE;
        }
        if (keycode == HOME || keycode == END) {
            return CaretMove.CHAR;
        }
        if (keycode == LEFT || keycode == RIGHT) {
            boolean ctrl = Gdx.input.isKeyPressed(CONTROL_LEFT) || Gdx.input.isKeyPressed(SYM);
            return ctrl ? CaretMove.WORD : CaretMove.CHAR;
        }
        return CaretMove.NONE;
    }

    /**
     * A word jump as Windows edits make it, onto the start of a word. The
     * field's own jump stops at every edge between a word and what is not
     * one: from the start of "Mah Boy" it stops on the space, and again at
     * "Boy". This presses the field's jump again (its own key handling, on
     * the stage's keyboard focus) while the caret stands on whitespace. True:
     * the press was answered here, and is not the game's again.
     */
    public static boolean jumpWord(TextInput field, int keycode) {
        com.badlogic.gdx.scenes.scene2d.Stage stage = com.tann.dice.Main.stage;
        stage.keyDown(keycode);
        int caret = caret(field);
        String text = field.getText();
        while (caret > 0 && caret < text.length() && Character.isWhitespace(text.charAt(caret))) {
            stage.keyDown(keycode);
            int next = caret(field);
            if (next == caret) {
                break;
            }
            caret = next;
        }
        return true;
    }

    private static int caret(TextInput field) {
        return (Integer) Captured.field(field, TextInput.class, "caretIndex");
    }

    /** False when the panel holds no field: someone else reads it. */
    static boolean emit(GraphBuilder b, Actor modal) {
        final TextInput field = find(modal);
        if (field == null) {
            return false;
        }
        final String label = label(field, modal);
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TEXT_FIELD;
        vt.excludeFromSearch = true;
        vt.textField = new TextField() {
            @Override
            public String text() {
                return field.getText();
            }

            @Override
            public int caret() {
                return TextInputNodes.caret(field);
            }

            @Override
            public int anchor() {
                return (Integer) Captured.field(field, TextInput.class, "caretEndIndex");
            }
        };
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return label;
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        String text = field.getText();
                        return text.isEmpty() ? Loc.get("ui", "text.blank") : text;
                    }
                }, AnnouncementKinds.VALUE));
        b.addItem(ControlId.referenced(field, CompositeKey.of("text-field")), vt);
        return true;
    }

    private static TextInput find(Actor actor) {
        if (actor instanceof TextInput) {
            return (TextInput) actor;
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                TextInput found = find(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // What the field is for, from the listener the game handed it: the game
    // method that built it, and what that captured.
    private static String label(TextInput field, Actor modal) {
        TextInputListener listener = (TextInputListener) Captured.field(field, TextInput.class, "listener");
        // The control the field was opened from, for a listener built inside one.
        Object opener = Captured.value(listener, TannListener.class);
        if (opener == null) {
            opener = Captured.value(listener, Runnable.class);
        }
        // EntPanelInventory.addNameChangeListener: renames the panel's hero.
        if (Captured.builtBy(opener, EntPanelInventory.class, "addNameChangeListener")) {
            Ent hero = Captured.value(opener, EntPanelInventory.class).ent;
            return Loc.get("ui", "inv.rename_hero", "hero", GameText.t(hero.getName(true)));
        }
        // Resolver.activate: a search for one kind of thing, or a wish.
        Resolver<?> resolver = Captured.value(listener, Resolver.class);
        if (resolver != null && Captured.builtBy(listener, Resolver.class, "activate")) {
            return resolverLabel(resolver);
        }
        // The high-score name (TextInputField, SubmitHighscorePanel's field).
        if (Captured.value(opener, TextInputField.class) != null) {
            return GameText.t("enter highscore name");
        }
        if (Captured.builtBy(opener, CustomMode.class, "makeSave")) {
            return GameText.t("Name this stored custom mode");
        }
        // The scenario Store button (PasteMode.makeStartGameCard).
        if (Captured.builtBy(opener, PasteMode.class, "makeStartGameCard")) {
            return GameText.t("Scenario title");
        }
        if (Captured.builtBy(listener, BugReport.class, "initiateAutomatedReport")) {
            return GameText.t("Description");
        }
        // The almanac help's hidden incantation field (HelpPage.populateList):
        // the game titles it nothing.
        if (Captured.builtBy(opener, HelpPage.class, "populateList")) {
            return null;
        }
        // A field no reader knows yet: the title its panel draws.
        String opened = listener != null ? listener.getClass().getName() : "null";
        if (UNKNOWN.add(opened)) {
            SndLog.info("text field with no reader for its listener " + opened + "; labelled by its panel's title");
        }
        List<String> texts = GameUi.textsUnder(modal);
        return texts.isEmpty() ? null : texts.get(0);
    }

    // Resolver.activate's title: a wish (the creative mode's resolvers, one
    // declared in each of these game classes, activated with
    // Mode.WISH.wishFor), else the resolver's own description or its search.
    private static String resolverLabel(Resolver<?> resolver) {
        Class<?> declaredIn = resolver.getClass();
        while (declaredIn.getEnclosingClass() != null) {
            declaredIn = declaredIn.getEnclosingClass();
        }
        String wish = declaredIn == EntPanelInventory.class ? "levelup"
                : declaredIn == com.tann.dice.gameplay.context.DungeonContext.class ? "modifier"
                : declaredIn == com.tann.dice.screens.generalPanels.InventoryPanel.class ? "item"
                : null;
        if (wish != null) {
            return GameText.t(Mode.WISH.wishFor(wish));
        }
        Object desc = Captured.call(resolver, Resolver.class, "getOtherOverrideDesc");
        if (desc != null) {
            return GameText.t((String) desc);
        }
        return GameText.t("Search for " + Captured.call(resolver, Resolver.class, "getTypeName"));
    }

    // Listener classes already logged (builds run every frame).
    private static final Set<String> UNKNOWN = new HashSet<String>();
}
