package snd.module.screens;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.tann.dice.gameplay.effect.eff.keyword.Keyword;
import com.tann.dice.gameplay.phase.levelEndPhase.rewardPhase.decisionPhase.choice.choosable.Choosable;
import com.tann.dice.screens.dungeon.panels.Explanel.EntPanelInventory;
import com.tann.dice.screens.dungeon.panels.book.page.ledgerPage.LedgerUtils;
import com.tann.dice.screens.dungeon.panels.entPanel.choosablePanel.ConcisePanel;
import com.tann.dice.util.listener.TannListener;
import com.tann.dice.util.tp.TP;

import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.module.Captured;
import snd.module.GameText;
import snd.module.GameUi;

/**
 * The almanac's Pin tab (LedgerUtils.makePinsGroup): its search and paste
 * buttons, then each pinned thing as the game draws it, a close button over
 * the thing's own panel, then the copy-button option. A pin is a container
 * named for what it pins; its close button reads "unpin", and the thing reads
 * from the panel it holds: an item's or modifier's card, a unit's panel, a
 * keyword's rules. The close button's listener holds the pin (its name and
 * panel), which is how a pin is told from the rest of the page.
 */
final class PinNodes {
    private PinNodes() {
    }

    static void emit(GraphBuilder b, Actor actor, ActorNodes.Place place) {
        if (!actor.isVisible()) {
            return;
        }
        if (!(actor instanceof Group) || !holdsPin((Group) actor)) {
            ActorNodes.emit(b, actor, place);
            return;
        }
        Group group = (Group) actor;
        for (Actor child : group.getChildren()) {
            TP<?, ?> pin = pinOf(child);
            if (pin != null) {
                pin(b, child, pin, place);
                return; // the pin's group holds its close button and its panel
            }
        }
        for (Actor child : group.getChildren()) {
            emit(b, child, place);
        }
    }

    // The pin a close button removes, or null for any other actor.
    private static TP<?, ?> pinOf(Actor actor) {
        TannListener close = Captured.listenerBuiltBy(actor, LedgerUtils.class, "makePinsGroup");
        return close != null ? Captured.value(close, TP.class) : null;
    }

    private static boolean holdsPin(Group group) {
        for (Actor child : group.getChildren()) {
            if (pinOf(child) != null || child instanceof Group && holdsPin((Group) child)) {
                return true;
            }
        }
        return false;
    }

    private static void pin(GraphBuilder b, final Actor close, TP<?, ?> pin, ActorNodes.Place place) {
        final String pinName = (String) pin.a;
        final Actor panel = (Actor) pin.b;
        final Object thing = thingOf(panel, pinName);
        b.pushContext(CompositeKey.of("pin", pinName), new Supplier<String>() {
            @Override
            public String get() {
                return nameOf(thing, pinName);
            }
        });

        NodeVtable unpin = new NodeVtable();
        unpin.controlType = ControlTypes.BUTTON;
        unpin.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", "pin.unpin");
            }
        }, AnnouncementKinds.LABEL));
        unpin.onActivate = new Runnable() {
            @Override
            public void run() {
                GameUi.activate(close);
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("pin", pinName, "unpin")), unpin);

        if (thing instanceof Keyword) {
            keyword(b, (Keyword) thing, pinName);
        } else {
            ActorNodes.emit(b, panel, place);
        }
        b.popContext();
    }

    // What a pin's panel shows: the card's choosable, the panel's unit, or
    // (the keyword panel holds nothing) the keyword the pin is named for.
    private static Object thingOf(Actor panel, String pinName) {
        ConcisePanel card = find(panel, ConcisePanel.class);
        if (card != null) {
            Choosable choosable = ChoosablePanelNodes.choosable(card);
            if (choosable != null) {
                return choosable;
            }
        }
        EntPanelInventory unit = find(panel, EntPanelInventory.class);
        if (unit != null) {
            return unit.ent;
        }
        return Keyword.byName(pinName);
    }

    private static String nameOf(Object thing, String pinName) {
        if (thing instanceof Choosable) {
            return ChoiceScreen.nameOf((Choosable) thing);
        }
        if (thing instanceof com.tann.dice.gameplay.content.ent.Ent) {
            return GameUi.entName((com.tann.dice.gameplay.content.ent.Ent) thing);
        }
        if (thing instanceof Keyword) {
            return GameText.t(((Keyword) thing).getColourTaggedString());
        }
        return pinName;
    }

    // A pinned keyword: its rules (KUtils.makeActor), the first line its
    // definition, the rest its extra rules, in the buffer.
    private static void keyword(GraphBuilder b, Keyword k, String pinName) {
        final List<String> lines = Terms.keywordLines(Collections.singletonList(k), null);
        if (lines.isEmpty()) {
            return;
        }
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TEXT;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return lines.get(0);
            }
        }, AnnouncementKinds.LABEL));
        vt.details = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return lines.subList(1, lines.size());
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("pin", pinName, "rules")), vt);
    }

    private static <T> T find(Actor actor, Class<T> type) {
        if (type.isInstance(actor)) {
            return type.cast(actor);
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
