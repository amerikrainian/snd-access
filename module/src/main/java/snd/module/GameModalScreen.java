package snd.module;

import java.util.Arrays;
import java.util.function.Supplier;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.tann.dice.screens.dungeon.panels.book.Book;
import com.tann.dice.util.ui.TextWriter;
import com.tann.dice.util.ui.standardButton.StandardButton;

import snd.core.graph.AnnouncementKinds;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.nav.AccessScreen;

/**
 * A generic reader for the game's pushed modals — choice dialogs, the cog
 * menu, unlock-requirement panels, the party-layout picker, mode info. Covers
 * whatever the game pushes onto its modal stack by walking the top modal's
 * actor tree in child (visual) order: interactive actors (StandardButton, or
 * anything carrying a TannListener) become buttons labeled by their own text;
 * plain TextWriters become readable lines. Activation fires the game's own
 * listener, and Escape falls through to the game's own modal-pop handling.
 * Screens with dedicated readers (the Book, for now a placeholder) are
 * special-cased above this generic floor.
 */
class GameModalScreen extends AccessScreen {
    @Override
    public String key() {
        return "game-modal";
    }

    @Override
    public int layer() {
        return 20; // covers whichever base screen the modal floats over
    }

    @Override
    public boolean isActive() {
        return GameUi.topModal() != null;
    }

    @Override
    public void build(GraphBuilder b) {
        Actor modal = GameUi.topModal();
        if (modal == null) {
            return;
        }
        if (modal instanceof Book) {
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.TEXT;
            vt.announcements = Arrays.asList(NodeAnnouncement.of(
                    "Almanac book. A dedicated reader is coming; press Escape to close."));
            b.addItem(actorId(modal), vt);
            return;
        }
        b.pushContext("Dialog");
        emit(b, modal);
        b.popContext();
    }

    // Stable per-actor-instance identity that prints cleanly (game groups
    // override toString with whole-tree dumps, so the actor can't be its own
    // structural key).
    private static ControlId actorId(Actor actor) {
        return ControlId.referenced(actor, System.identityHashCode(actor));
    }

    // Walk in child order (Pixl layouts add in reading order). The INNERMOST
    // interactive actor wins: containers often carry their own gesture
    // listeners (self-pop, drag), so an actor only becomes a button when
    // nothing beneath it is interactive — its TextWriters are then its label.
    // A plain TextWriter becomes a readable line.
    private void emit(GraphBuilder b, Actor actor) {
        if (actor == null || !actor.isVisible()) {
            return;
        }
        if (interactiveLeaf(actor)) {
            b.addItem(actorId(actor), buttonFor(actor));
            return;
        }
        if (actor instanceof TextWriter) {
            final TextWriter tw = (TextWriter) actor;
            if (tw.text == null || tw.text.trim().isEmpty()) {
                return;
            }
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.TEXT;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return tw.text;
                }
            }, AnnouncementKinds.LABEL));
            b.addItem(actorId(actor), vt);
            return;
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                emit(b, child);
            }
        }
    }

    private static boolean interactiveLeaf(Actor actor) {
        if (actor instanceof StandardButton) {
            return true;
        }
        return GameUi.hasTannListener(actor) && !hasInteractiveDescendant(actor);
    }

    private static boolean hasInteractiveDescendant(Actor actor) {
        if (!(actor instanceof Group)) {
            return false;
        }
        for (Actor child : ((Group) actor).getChildren()) {
            if (child instanceof StandardButton || GameUi.hasTannListener(child)
                    || hasInteractiveDescendant(child)) {
                return true;
            }
        }
        return false;
    }

    private NodeVtable buttonFor(final Actor actor) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                String label = GameUi.labelOf(actor);
                return label != null ? label : "unlabeled " + actor.getClass().getSimpleName();
            }
        }, AnnouncementKinds.LABEL));
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                GameUi.activate(actor);
            }
        };
        vt.onSecondary = new Runnable() {
            @Override
            public void run() {
                GameUi.info(actor);
            }
        };
        return vt;
    }
}
