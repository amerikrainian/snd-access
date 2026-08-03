package snd.module.screens;

import java.util.Arrays;
import java.util.function.Supplier;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.tann.dice.util.ui.TextWriter;
import com.tann.dice.util.ui.standardButton.StandardButton;

import snd.core.loc.Loc;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.speech.TextFilter;
import snd.module.GameText;
import snd.module.GameUi;

/**
 * Turns a game actor tree into graph nodes, in child (visual) order:
 * interactive actors (StandardButton, or anything carrying a TannListener)
 * become buttons labeled by their own text; plain TextWriters become readable
 * lines. Serves both the pushed-modal reader and the dialog-phase screens —
 * the game builds its dialogs the same way in both places.
 */
final class ActorNodes {
    private ActorNodes() {
    }

    // Stable per-actor-instance identity that prints cleanly (game groups
    // override toString with whole-tree dumps, so the actor can't be its own
    // structural key).
    static ControlId actorId(Actor actor) {
        return ControlId.referenced(actor, System.identityHashCode(actor));
    }

    // Walk in child order (Pixl layouts add in reading order). The INNERMOST
    // interactive actor wins: containers often carry their own gesture
    // listeners (self-pop, drag), so an actor only becomes a button when
    // nothing beneath it is interactive — its TextWriters are then its label.
    // A plain TextWriter becomes a readable line.
    static void emit(GraphBuilder b, Actor actor) {
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

    static NodeVtable buttonFor(final Actor actor) {
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
                        if (label == null && actor instanceof com.tann.dice.screens.dungeon.panels.DieSidePanel) {
                            // Die-net previews in dialogs (level-ups, sheets).
                            label = GameText.t(((com.tann.dice.screens.dungeon.panels.DieSidePanel) actor)
                                    .side.getBaseEffect().describe());
                        }
                        if (label == null && actor instanceof com.tann.dice.screens.dungeon.panels.entPanel.ItemHeroPanel) {
                            // A sheet's item slot: the item's name, or an empty slot.
                            com.tann.dice.gameplay.content.item.Item item =
                                    ((com.tann.dice.screens.dungeon.panels.entPanel.ItemHeroPanel) actor).item;
                            label = item != null ? GameText.t(item.getName())
                                    : Loc.get("ui", "modal.empty_slot");
                        }
                        if (label == null) {
                            label = monsterTileName(actor); // challenge dialogs' monster tiles
                        }
                        return label != null ? label
                                : Loc.get("ui", "modal.unlabeled", "type", actor.getClass().getSimpleName());
                    }
                }, AnnouncementKinds.LABEL),
                // Item/modifier cards (ConcisePanel) draw their effect text as
                // side views; read it from the model instead.
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return choosableEffect(actor);
                    }
                }, AnnouncementKinds.TOOLTIP),
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
                    sb.append(col == null ? Loc.get("ui", "value.random") : col.name());
                }
                return sb.length() > 0 ? sb.toString() : null;
            }
        }
        return null;
    }

    private static java.lang.reflect.Field monsterTypeField;

    private static String monsterTileName(Actor actor) {
        if (!(actor instanceof com.tann.dice.screens.dungeon.panels.book.views.MonsterLedgerView)) {
            return null;
        }
        try {
            if (monsterTypeField == null) {
                monsterTypeField = com.tann.dice.screens.dungeon.panels.book.views.MonsterLedgerView.class
                        .getDeclaredField("type");
                monsterTypeField.setAccessible(true);
            }
            com.tann.dice.gameplay.content.ent.type.MonsterType type =
                    (com.tann.dice.gameplay.content.ent.type.MonsterType) monsterTypeField.get(actor);
            return type != null ? GameText.t(type.getName(true)) : null;
        } catch (Throwable t) {
            snd.core.SndLog.error("monster tile name failed", t);
            return null;
        }
    }

    private static java.lang.reflect.Field choosableField;

    private static String choosableEffect(Actor actor) {
        if (!(actor instanceof com.tann.dice.screens.dungeon.panels.entPanel.choosablePanel.ConcisePanel)) {
            return null;
        }
        try {
            if (choosableField == null) {
                choosableField = com.tann.dice.screens.dungeon.panels.entPanel.choosablePanel.ConcisePanel.class
                        .getDeclaredField("choosable");
                choosableField.setAccessible(true);
            }
            Object choosable = choosableField.get(actor);
            if (choosable instanceof com.tann.dice.gameplay.content.item.Item) {
                return GameText.t(((com.tann.dice.gameplay.content.item.Item) choosable).getDescription());
            }
            if (choosable instanceof com.tann.dice.gameplay.modifier.Modifier) {
                return GameText.t(((com.tann.dice.gameplay.modifier.Modifier) choosable).getFullDescription());
            }
            return null;
        } catch (Throwable t) {
            snd.core.SndLog.error("choosable panel effect failed", t);
            return null;
        }
    }

    static NodeVtable sliderFor(final com.tann.dice.util.Slider slider) {
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
                        return Loc.get("ui", "value.percent", "value", GameUi.sliderPercent(slider));
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
                return Loc.get("ui", "value.percent", "value", GameUi.sliderPercent(slider));
            }
        };
        return vt;
    }
}
