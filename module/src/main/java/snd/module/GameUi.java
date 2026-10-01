package snd.module;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.tann.dice.screens.Screen;
import com.tann.dice.util.Pair;
import com.tann.dice.util.listener.TannListener;
import com.tann.dice.util.ui.TextWriter;
import com.tann.dice.util.ui.standardButton.StandardButton;

import snd.contracts.SndLog;
import snd.core.loc.Loc;

/**
 * Scene2d bridge utilities: read and drive the game's own actors. Activation
 * goes through the game's listeners (TannListener.action — the exact code a
 * mouse click runs), so behavior can never diverge from the real UI.
 */
public final class GameUi {
    private GameUi() {
    }

    /**
     * The top pushed modal actor of the current game screen, or null. Only
     * blocker-backed pushes count: a null InputBlocker marks a "light" push —
     * inspection popups like the targeting Explanel that float over a still-
     * interactive screen — and reading those as modals would steal navigation
     * from the screen beneath. Exception: a light-pushed ChoiceDialog IS a
     * real question (the end-turn confirmation ships blockerless) and must
     * read.
     */
    public static Actor topModal() {
        List<Actor> modals = modals();
        return modals.isEmpty() ? null : modals.get(modals.size() - 1);
    }

    /** Every modal on the current game screen's stack, bottom first, by {@link #topModal}'s rule. */
    public static List<Actor> modals() {
        List<Actor> modals = new ArrayList<Actor>();
        Screen screen = com.tann.dice.Main.getCurrentScreen();
        if (screen == null) {
            return modals;
        }
        for (Pair<Actor, ?> pair : castStack(screen.modalStack)) {
            if (pair.a != null && pair.a.getStage() != null
                    && (pair.b != null || pair.a instanceof com.tann.dice.util.ui.choice.ChoiceDialog)) {
                modals.add(pair.a);
            }
        }
        return modals;
    }

    /** Whether a panel of this kind is anywhere on the current screen's modal stack, covered or not. */
    public static boolean modalOpen(Class<? extends Actor> kind) {
        Screen screen = com.tann.dice.Main.getCurrentScreen();
        if (screen == null) {
            return false;
        }
        for (Pair<Actor, ?> pair : castStack(screen.modalStack)) {
            if (kind.isInstance(pair.a) && pair.a.getStage() != null) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Pair<Actor, ?>> castStack(List stack) {
        return (List<Pair<Actor, ?>>) stack;
    }

    /**
     * Left-click the actor the way the game does: TannListener.action on
     * EVERY gesture listener (a real tap reaches them all — StandardButtons
     * carry an internal runnable listener plus any added handler), else a
     * synthesized touchDown/touchUp pair for plain InputListeners.
     */
    public static boolean activate(Actor actor) {
        if (actor == null) {
            return false;
        }
        Actor top = stackTop();
        boolean done = activateQuietly(actor);
        readInfoPopup(actor, top);
        return done;
    }

    private static boolean activateQuietly(Actor actor) {
        boolean any = false;
        for (com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener l : gestureListeners(actor)) {
            if (l instanceof TannListener) {
                any = true;
                TannListener tl = (TannListener) l;
                // The game's own tap path: info() is the fallback when action()
                // leaves the tap unhandled (info-only rows like "UI size").
                if (!tl.action(0, 0, actor.getWidth() / 2f, actor.getHeight() / 2f)) {
                    tl.info(0, actor.getWidth() / 2f, actor.getHeight() / 2f);
                }
            }
        }
        return any || fireClick(actor);
    }

    /**
     * Right-click equivalent: the game's info() surface (details panels), on
     * EVERY gesture listener — like a real right-click reaches them all. A
     * real right-click also BUBBLES: an unhandled info walks up the ancestor
     * chain (the cog menu's option panels carry their help listener on the
     * panel group, not the rows).
     */
    public static boolean info(Actor actor) {
        Actor top = stackTop();
        boolean done = infoQuietly(actor);
        readInfoPopup(actor, top);
        return done;
    }

    private static boolean infoQuietly(Actor actor) {
        for (Actor a = actor; a != null; a = a.getParent()) {
            boolean handled = false;
            for (ActorGestureListener l : gestureListeners(a)) {
                if (l instanceof TannListener) {
                    handled |= ((TannListener) l).info(1, a.getWidth() / 2f, a.getHeight() / 2f);
                }
            }
            if (handled) {
                return true;
            }
        }
        return false;
    }

    // ---- info popups: the game answers many gestures by pushing a little
    // bordered panel of text over everything (Screen.pushAndCenter — "UI
    // scaling factor" on the settings' UI size row, an achievement's
    // description in the almanac). There is nothing in one to operate: a
    // sighted player glances at it and clicks it away. Read as a modal it
    // would be a one-line "dialog" that takes the focus and has to be
    // escaped, so it is spoken where the player stands and dismissed, and the
    // control that produced it keeps its lines for the control buffer. ----

    private static snd.contracts.speech.SpeechPipeline speech;

    // The popup each control last answered with. The panel is a live game
    // object, read again whenever the buffer is; keyed weakly, so an entry
    // goes when the game rebuilds the control.
    private static final java.util.Map<Actor, Actor> INFO_POPUPS = new java.util.WeakHashMap<Actor, Actor>();

    static void bind(snd.contracts.speech.SpeechPipeline pipeline) {
        speech = pipeline;
    }

    private static Actor stackTop() {
        Screen screen = com.tann.dice.Main.getCurrentScreen();
        if (screen == null || screen.modalStack.isEmpty()) {
            return null;
        }
        return screen.modalStack.get(screen.modalStack.size() - 1).a;
    }

    private static void readInfoPopup(Actor source, Actor topBefore) {
        Actor top = stackTop();
        if (top == null || top == topBefore || !isTextOnly(top)) {
            return; // nothing new came up, or something to operate: a real modal, read as one
        }
        List<String> lines = popupLines(top);
        if (lines.isEmpty()) {
            return;
        }
        INFO_POPUPS.put(source, top);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) {
                sb.append(". ");
            }
            sb.append(line);
        }
        speech.speak(sb.toString(), false);
        com.tann.dice.Main.getCurrentScreen().pop(top);
    }

    /** The lines of the info popup this control last answered with, for its control buffer. */
    public static List<String> infoLines(Actor source) {
        Actor popup = INFO_POPUPS.get(source);
        return popup != null ? popupLines(popup) : new ArrayList<String>();
    }

    // A popup's lines: its drawn text — except an item's or modifier's big
    // panel, which reads from the model like everywhere else (name with its
    // tier, not a bare "1"; the description whole), followed by whatever the
    // game drew on it besides (the almanac's "chosen 1/1 (100%)").
    private static List<String> popupLines(Actor popup) {
        List<String> drawn = textsUnder(popup);
        if (popup instanceof com.tann.dice.screens.dungeon.panels.Explanel.Explanel) {
            // A panel opened with its keyword boxes on already draws the rules.
            StringBuilder shown = new StringBuilder();
            for (String line : drawn) {
                shown.append(snd.contracts.speech.TextFilter.clean(line).toLowerCase()).append('\n');
            }
            for (String extra : explanelExtras((com.tann.dice.screens.dungeon.panels.Explanel.Explanel) popup)) {
                String clean = snd.contracts.speech.TextFilter.clean(extra).toLowerCase();
                String rules = clean.substring(clean.indexOf(": ") + 1).trim();
                if (shown.indexOf(rules) < 0) {
                    drawn.add(extra);
                }
            }
            return drawn;
        }
        if (!(popup instanceof com.tann.dice.screens.dungeon.panels.entPanel.choosablePanel.ConcisePanel)) {
            return drawn;
        }
        List<String> lines = snd.module.screens.ChoosablePanelNodes.lines(
                (com.tann.dice.screens.dungeon.panels.entPanel.choosablePanel.ConcisePanel) popup);
        if (lines == null) {
            return drawn;
        }
        StringBuilder said = new StringBuilder();
        for (String line : lines) {
            said.append(snd.contracts.speech.TextFilter.clean(line).toLowerCase()).append('\n');
        }
        for (String extra : drawn) {
            String clean = snd.contracts.speech.TextFilter.clean(extra).toLowerCase();
            if (!clean.isEmpty() && said.indexOf(clean) < 0) {
                lines.add(extra);
            }
        }
        return lines;
    }

    private static java.lang.reflect.Field explanelShowingField;

    // What an ability's or a die side's explanation panel draws as pictures or
    // leaves out: a spell's mana cost (pips), a tactic's (die faces), and the
    // rules of the keywords it names — the almanac opens these panels with
    // the keyword boxes off.
    private static List<String> explanelExtras(com.tann.dice.screens.dungeon.panels.Explanel.Explanel panel) {
        List<String> lines = new ArrayList<String>();
        try {
            if (explanelShowingField == null) {
                explanelShowingField = com.tann.dice.screens.dungeon.panels.Explanel.Explanel.class
                        .getDeclaredField("showing");
                explanelShowingField.setAccessible(true);
            }
            Object showing = explanelShowingField.get(panel);
            if (showing instanceof com.tann.dice.gameplay.effect.targetable.ability.Ability) {
                com.tann.dice.gameplay.effect.targetable.ability.Ability ability =
                        (com.tann.dice.gameplay.effect.targetable.ability.Ability) showing;
                String cost = snd.module.screens.CombatScreen.baseCostText(ability);
                if (cost != null) {
                    lines.add(cost);
                }
                lines.addAll(snd.module.screens.Terms.forEff(ability.getDerivedEffects()));
            } else if (showing instanceof com.tann.dice.gameplay.content.ent.die.side.EntSide) {
                lines.addAll(snd.module.screens.Terms.forEff(
                        ((com.tann.dice.gameplay.content.ent.die.side.EntSide) showing).getBaseEffect()));
            }
        } catch (Throwable t) {
            SndLog.error("explanation panel read failed", t);
        }
        return lines;
    }

    // Nothing to click, drag or type into anywhere in it.
    private static boolean isTextOnly(Actor actor) {
        if (actor instanceof StandardButton || actor instanceof com.tann.dice.util.Slider
                || actor instanceof com.tann.dice.util.ui.TextInput || isClickable(actor)) {
            return false;
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                if (!isTextOnly(child)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Escape for a panel pushed over another panel: close the top one only,
     * as a click outside it does. The game's own Escape pops EVERYTHING
     * (Screen.popAllMedium), which from a details panel over the settings
     * menu lands back on the dungeon. False = one level or none is up, or
     * the top panel is not the kind Escape closes; the game's Escape applies.
     */
    public static boolean popTopModalOnly() {
        Screen screen = com.tann.dice.Main.getCurrentScreen();
        if (screen == null || screen.modalStack.size() < 2 || !screen.popSingleMedium()) {
            return false;
        }
        com.tann.dice.statics.sound.Sounds.playSound(com.tann.dice.statics.sound.Sounds.pop);
        return true;
    }

    /**
     * "Wolf 2" — a combatant's spoken name, numbered among same-name entities
     * in its visible column (heroes keep dead slots, dead monsters vanish) so
     * attack attribution matches what column navigation reads. Plain name
     * when unique or no fight is live.
     */
    public static String entName(com.tann.dice.gameplay.content.ent.Ent ent) {
        String name = ent.getName(true);
        try {
            com.tann.dice.screens.dungeon.DungeonScreen ds = com.tann.dice.screens.dungeon.DungeonScreen.get();
            com.tann.dice.gameplay.fightLog.FightLog fightLog = ds != null ? ds.getFightLog() : null;
            if (fightLog != null) {
                List<com.tann.dice.gameplay.content.ent.Ent> column = fightLog
                        .getSnapshot(com.tann.dice.gameplay.fightLog.FightLog.Temporality.Present)
                        .getEntities(ent.isPlayer(), ent.isPlayer() ? null : Boolean.FALSE);
                int count = 0;
                int index = 0;
                for (com.tann.dice.gameplay.content.ent.Ent other : column) {
                    if (name.equals(other.getName(true))) {
                        count++;
                        if (other == ent) {
                            index = count;
                        }
                    }
                }
                if (count > 1 && index > 0) {
                    return Loc.get("combat", "numbered_name", "name", GameText.t(name), "n", index);
                }
            }
        } catch (Throwable t) {
            SndLog.error("ent name numbering failed", t);
        }
        return GameText.t(name);
    }

    // Specifically the game's own listener type: plain ActorGestureListeners
    // (a ScrollPane's built-in scroll handling) are not activation surfaces.
    public static boolean hasTannListener(Actor actor) {
        for (ActorGestureListener l : gestureListeners(actor)) {
            if (l instanceof TannListener) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the actor answers a click at all: the game's own listener type,
     * or libGDX's plain ClickListener — which the almanac hangs on exactly
     * the monster and item tiles that are NOT locked (LedgerUtils), the ones
     * worth opening. {@link #activate} reaches a ClickListener with its
     * synthesized click.
     */
    public static boolean isClickable(Actor actor) {
        if (hasTannListener(actor)) {
            return true;
        }
        for (com.badlogic.gdx.scenes.scene2d.EventListener l : actor.getListeners()) {
            if (l instanceof com.badlogic.gdx.scenes.scene2d.utils.ClickListener) {
                return true;
            }
        }
        return false;
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
    public static List<String> textsUnder(Actor actor) {
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
    public static String labelOf(Actor actor) {
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

    // ---- icon naming: the game's icon-only buttons, named by their texture.
    // The map holds locale KEYS; the word resolves at read time so it follows
    // the live language. ----

    private static java.util.Map<Object, String> knownIcons;

    private static java.util.Map<Object, String> knownIcons() {
        if (knownIcons == null) {
            java.util.Map<Object, String> m = new java.util.IdentityHashMap<Object, String>();
            m.put(com.tann.dice.statics.Images.almanac, "icon.almanac");
            m.put(com.tann.dice.statics.Images.cog, "icon.menu");
            m.put(com.tann.dice.statics.Images.back, "icon.back");
            m.put(com.tann.dice.statics.Images.globe, "icon.language");
            m.put(com.tann.dice.statics.Images.padlock, "icon.locked");
            m.put(com.tann.dice.statics.Images.searchIcon, "icon.search");
            m.put(com.tann.dice.statics.Images.zoom2, "icon.expand");
            m.put(com.tann.dice.statics.Images.esc_display, "icon.display");
            m.put(com.tann.dice.statics.Images.esc_sound, "icon.sound");
            m.put(com.tann.dice.statics.Images.singleDie, "icon.reroll");
            m.put(com.tann.dice.statics.Images.reroll, "icon.reroll");
            m.put(com.tann.dice.statics.Images.ui_crossAlmanac, "icon.close");
            knownIcons = m;
        }
        return knownIcons;
    }

    /** The name of a known game icon under this actor, or null. */
    public static String iconNameUnder(Actor actor) {
        if (actor instanceof com.tann.dice.util.ImageActor) {
            String key = knownIcons().get(((com.tann.dice.util.ImageActor) actor).tr);
            return key != null ? Loc.get("ui", key) : null;
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                String name = iconNameUnder(child);
                if (name != null) {
                    return name;
                }
            }
        }
        return null;
    }

    // ---- confirm button: its state enum owns the game's "Done Rolling" /
    // "End turn" text in a package-private field; read it reflectively so the
    // spoken label is exactly the game's, translated ----

    private static Field confirmTextField;

    public static String confirmLabel(com.tann.dice.screens.dungeon.panels.ConfirmButton button) {
        try {
            if (confirmTextField == null) {
                Field f = com.tann.dice.screens.dungeon.panels.ConfirmButton.ConfirmState.class
                        .getDeclaredField("confirmText");
                f.setAccessible(true);
                confirmTextField = f;
            }
            String text = (String) confirmTextField.get(button.getConfirmState());
            return text != null ? com.tann.dice.Main.t(text) : null;
        } catch (Throwable t) {
            SndLog.error("confirm label read failed", t);
            return null;
        }
    }

    // ---- slider bridge: the game's Slider is drag-only; drive its own
    // update path (private setValue + the slideAction that feeds the option
    // and saves) so keyboard adjust behaves exactly like a drag ----

    private static Field sliderTitleField;
    private static Field sliderSlideActionField;
    private static java.lang.reflect.Method sliderSetValue;

    private static void initSliderReflection() throws Exception {
        if (sliderSetValue == null) {
            Class<?> cls = com.tann.dice.util.Slider.class;
            sliderTitleField = cls.getDeclaredField("title");
            sliderTitleField.setAccessible(true);
            sliderSlideActionField = cls.getDeclaredField("slideAction");
            sliderSlideActionField.setAccessible(true);
            sliderSetValue = cls.getDeclaredMethod("setValue", float.class);
            sliderSetValue.setAccessible(true);
        }
    }

    public static String sliderTitle(com.tann.dice.util.Slider slider) {
        try {
            initSliderReflection();
            Object title = sliderTitleField.get(slider);
            return title != null ? title.toString() : Loc.get("ui", "role.slider");
        } catch (Throwable t) {
            SndLog.error("slider title read failed", t);
            return Loc.get("ui", "role.slider");
        }
    }

    /** 0..100, the spoken value. */
    public static int sliderPercent(com.tann.dice.util.Slider slider) {
        return Math.round(slider.getValue() * 100f);
    }

    public static void sliderAdjust(com.tann.dice.util.Slider slider, int sign, boolean large) {
        try {
            initSliderReflection();
            float step = large ? 0.2f : 0.05f;
            float value = Math.max(0f, Math.min(1f, slider.getValue() + sign * step));
            sliderSetValue.invoke(slider, value);
            Runnable slideAction = (Runnable) sliderSlideActionField.get(slider);
            if (slideAction != null) {
                slideAction.run(); // the game's own path: option value + save
            }
        } catch (Throwable t) {
            SndLog.error("slider adjust failed", t);
        }
    }

    /**
     * Depth-first search for a StandardButton whose cleaned text matches —
     * for driving card buttons the game builds inline without keeping fields
     * (Paste!/Store, stored scenarios). Null when absent; callers speak the
     * failure.
     */
    public static StandardButton findButtonByText(Group root, String needle) {
        String clean = snd.contracts.speech.TextFilter.clean(needle);
        for (Actor child : root.getChildren()) {
            if (child instanceof StandardButton) {
                String text = ((StandardButton) child).getText();
                if (text != null && snd.contracts.speech.TextFilter.clean(text).equalsIgnoreCase(clean)) {
                    return (StandardButton) child;
                }
            }
            if (child instanceof Group) {
                StandardButton found = findButtonByText((Group) child, needle);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // ---- hero rename: the title-bar group of a hero's inventory panel
    // carries the game's rename listener (opens its text input). It's the
    // panel's only plain-Group direct child with a TannListener besides the
    // portrait (which only gains one in Wish mode) ----

    private static Field portraitGroupField;

    public static Actor heroRenameTarget(com.tann.dice.screens.dungeon.panels.Explanel.EntPanelInventory panel) {
        try {
            if (portraitGroupField == null) {
                Field f = com.tann.dice.screens.dungeon.panels.Explanel.EntPanelInventory.class
                        .getDeclaredField("portraitGroup");
                f.setAccessible(true);
                portraitGroupField = f;
            }
            Object portrait = portraitGroupField.get(panel);
            for (Actor child : panel.getChildren()) {
                if (child != portrait && child instanceof Group && hasTannListener(child)) {
                    return child;
                }
            }
            return null;
        } catch (Throwable t) {
            SndLog.error("hero rename target lookup failed", t);
            return null;
        }
    }

    /** The (package-private) ModesPanel on the live title screen, or null. */
    public static com.tann.dice.screens.titleScreen.ModesPanel modesPanel() {
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

}
