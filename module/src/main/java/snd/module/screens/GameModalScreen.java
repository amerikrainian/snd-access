package snd.module.screens;

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
import snd.core.speech.TextFilter;
import snd.module.GameUi;

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
public class GameModalScreen extends AccessScreen {
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
        if (actor instanceof com.tann.dice.util.Slider) {
            b.addItem(actorId(actor), sliderFor((com.tann.dice.util.Slider) actor));
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
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        String label = GameUi.labelOf(actor);
                        if (label == null) {
                            label = GameUi.iconNameUnder(actor); // icon-only buttons
                        }
                        return label != null ? label : "unlabeled " + actor.getClass().getSimpleName();
                    }
                }, AnnouncementKinds.LABEL),
                // Party-layout picker options: the visual squares' colour
                // composition, resolved from the enum the label names.
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return partyLayoutColours(GameUi.labelOf(actor));
                    }
                }, AnnouncementKinds.VALUE));
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

    // "Basic" (or "Basic, r: 0.1") → "orange, yellow, grey, red, blue" from
    // PartyLayoutType; null for any label that isn't a layout name.
    private static String partyLayoutColours(String label) {
        if (label == null) {
            return null;
        }
        String name = TextFilter.clean(label);
        int comma = name.indexOf(',');
        if (comma >= 0) {
            name = name.substring(0, comma);
        }
        name = name.trim();
        for (com.tann.dice.gameplay.content.ent.group.PartyLayoutType plt
                : com.tann.dice.gameplay.content.ent.group.PartyLayoutType.values()) {
            if (plt.name().equals(name)) {
                StringBuilder sb = new StringBuilder();
                for (com.tann.dice.gameplay.content.ent.type.HeroCol col : plt.getColsInstance()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(col == null ? "random" : col.name());
                }
                return sb.length() > 0 ? sb.toString() : null;
            }
        }
        return null;
    }

    private NodeVtable sliderFor(final com.tann.dice.util.Slider slider) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.SLIDER;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return GameUi.sliderTitle(slider);
                    }
                }, AnnouncementKinds.LABEL),
                new NodeAnnouncement(new Supplier<String>() {
                    @Override
                    public String get() {
                        return GameUi.sliderPercent(slider) + " percent";
                    }
                }, true, AnnouncementKinds.VALUE));
        vt.onAdjust = new NodeVtable.Adjust() {
            @Override
            public void adjust(int sign, boolean large) {
                GameUi.sliderAdjust(slider, sign, large);
            }
        };
        vt.stateText = new Supplier<String>() {
            @Override
            public String get() {
                return GameUi.sliderPercent(slider) + " percent";
            }
        };
        return vt;
    }
}
