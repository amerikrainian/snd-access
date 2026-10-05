package snd.module.screens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.tann.dice.gameplay.content.gen.pipe.Pipe;
import com.tann.dice.screens.dungeon.panels.book.page.stuffPage.APIUtils;
import com.tann.dice.util.Colours;
import com.tann.dice.util.lang.Words;
import com.tann.dice.util.listener.TannListener;
import com.tann.dice.util.ui.TextWriter;
import com.tann.dice.util.ui.standardButton.StandardButton;

import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.core.text.MarkedSyntax;
import snd.module.Captured;
import snd.module.GameUi;

/**
 * The almanac's TextMod tab (APIUtils): its api section lists every way of
 * making something as a syntax line under the type it makes, and api-2 shows
 * one of them, picked by letter, with generated examples. The game tells the
 * text typed exactly from the tokens to replace by colour alone, so a syntax
 * reads from the pipe it documents with its tokens bracketed
 * ({@link MarkedSyntax}), and the extra syntaxes the "+more" option reveals,
 * drawn purple, say so. A syntax line names its pipe through its click
 * listener (api) or its button's runnable (api-2); the line api-2 shows is
 * the one the game saved as shown. Everything else is the generic walk's.
 */
final class TextModNodes {
    private TextModNodes() {
    }

    private static final Predicate<String> IS_COLOUR = new Predicate<String>() {
        @Override
        public boolean test(String tag) {
            return TextWriter.getMapCol(tag) != null;
        }
    };
    // PRNS, a part typed as-is, draws in the light colour.
    private static final Predicate<String> IS_LITERAL = new Predicate<String>() {
        @Override
        public boolean test(String tag) {
            return Colours.light.equals(TextWriter.getMapCol(tag));
        }
    };

    static String syntax(String doc) {
        return MarkedSyntax.spoken(doc, IS_COLOUR, IS_LITERAL);
    }

    /**
     * A pipe's syntax, each of its parts (PipeRegexNamed.parts) marked on its
     * own, so two tokens side by side stay two ("&lt;herocol&gt;&lt;#&gt;") while a
     * token drawn in several colours stays one ("&lt;any&gt;"). The api section
     * draws "any" for "hero" in a texture syntax; {@code anyEntity} does the
     * same. A pipe documenting itself otherwise reads from its document.
     */
    static String syntax(Pipe pipe, boolean anyEntity) {
        if (documentsByParts(pipe)) {
            com.tann.dice.gameplay.content.gen.pipe.regex.prnPart.PRNPart[] parts =
                    (com.tann.dice.gameplay.content.gen.pipe.regex.prnPart.PRNPart[]) Captured.field(pipe,
                            com.tann.dice.gameplay.content.gen.pipe.regex.PipeRegexNamed.class, "parts");
            if (parts != null) {
                StringBuilder sb = new StringBuilder();
                for (com.tann.dice.gameplay.content.gen.pipe.regex.prnPart.PRNPart part : parts) {
                    sb.append(syntax(any(part.getColDesc(), anyEntity)));
                }
                return sb.toString();
            }
        }
        return syntax(any(pipe.document(), anyEntity));
    }

    private static boolean documentsByParts(Pipe pipe) {
        try {
            return pipe.getClass().getMethod("document").getDeclaringClass()
                    == com.tann.dice.gameplay.content.gen.pipe.regex.PipeRegexNamed.class;
        } catch (NoSuchMethodException e) {
            snd.contracts.SndLog.error("Pipe.document is gone", e);
            return false;
        }
    }

    private static String any(String doc, boolean anyEntity) {
        return anyEntity ? doc.replaceAll("hero", APIUtils.ANY) : doc;
    }

    // APIUtils.getPipe: the pipe whose tag (its document) the page saved.
    private static Pipe pipeTagged(String tag) {
        for (com.tann.dice.screens.dungeon.panels.book.page.stuffPage.PipeType type
                : com.tann.dice.screens.dungeon.panels.book.page.stuffPage.PipeType.values()) {
            for (Object content : type.contents) {
                Pipe pipe = (Pipe) content;
                if (pipe.getIdTag().equalsIgnoreCase(tag)) {
                    return pipe;
                }
            }
        }
        return null;
    }

    // The api help line that opens with a syntax ("hero.i.item is powerful").
    private static Pipe heroItem;

    private static Pipe heroItem() {
        if (heroItem == null) {
            heroItem = new com.tann.dice.gameplay.content.gen.pipe.entity.hero.PipeHeroItem();
        }
        return heroItem;
    }

    private static boolean opensWithHeroItem(Actor actor) {
        return actor instanceof TextWriter
                && ((TextWriter) actor).text.startsWith("[notranslateall]" + heroItem().document());
    }

    private static void heroItemLine(GraphBuilder b, final TextWriter tw) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TEXT;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                String rest = tw.text.substring(("[notranslateall]" + heroItem().document()).length());
                return syntax(heroItem(), false) + rest;
            }
        }, AnnouncementKinds.LABEL));
        b.addItem(ActorNodes.actorId(tw), vt);
    }

    static void emit(GraphBuilder b, Actor actor, ActorNodes.Place place) {
        emit(b, actor, place, shownDoc());
    }

    private static void emit(GraphBuilder b, Actor actor, ActorNodes.Place place, String shown) {
        if (!actor.isVisible()) {
            return;
        }
        if (!special(actor, shown)) {
            ActorNodes.emit(b, actor, place);
            return;
        }
        Pipe letter = letterPipe(actor);
        if (letter != null) {
            letterButton(b, actor, letter, place);
            return;
        }
        if (isShownDoc(actor, shown)) {
            shownLine(b, shown);
            return;
        }
        if (opensWithHeroItem(actor)) {
            heroItemLine(b, (TextWriter) actor);
            return;
        }
        Group group = (Group) actor;
        if (holdsEntries(group)) {
            section(b, group);
            return;
        }
        for (Actor child : group.getChildren()) {
            emit(b, child, place, shown);
        }
    }

    // ---- the api section: a titled list of syntax lines per type ----

    private static Pipe entryPipe(Actor actor) {
        TannListener listener = Captured.listenerBuiltBy(actor, APIUtils.class, "makeDocumentation");
        return listener != null ? Captured.value(listener, Pipe.class) : null;
    }

    private static boolean holdsEntries(Group group) {
        for (Actor child : group.getChildren()) {
            if (entryPipe(child) != null) {
                return true;
            }
        }
        return false;
    }

    // APIUtils.makeDocumentation: the type's title, then its lines lettered
    // in order (Words.DOUBLE_ALPHABET), each opening its api-2 page.
    private static void section(GraphBuilder b, Group group) {
        String title = null;
        for (Actor child : group.getChildren()) {
            if (child instanceof TextWriter) {
                title = ((TextWriter) child).text;
                break;
            }
        }
        Object key = CompositeKey.of("textmod-section", title);
        b.setRegion(key);
        b.pushContext(title != null ? title : "", Loc.get("ui", "role.group"));
        int index = 0;
        for (Actor child : group.getChildren()) {
            final Pipe pipe = entryPipe(child);
            if (pipe == null) {
                continue;
            }
            final Actor entry = child;
            final char letter = Words.DOUBLE_ALPHABET.charAt(index);
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.BUTTON;
            vt.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return letter + ", " + syntax(pipe, pipe.isTexturey());
                        }
                    }, AnnouncementKinds.LABEL),
                    more(pipe));
            vt.onActivate = new Runnable() {
                @Override
                public void run() {
                    GameUi.activate(entry);
                }
            };
            b.addItem(ControlId.referenced(pipe, CompositeKey.of("textmod-entry", title, index)), vt);
            index++;
        }
        b.popContext();
        b.setRegion(null);
    }

    // ---- api-2: the letter row, and the shown syntax ----

    private static Pipe letterPipe(Actor actor) {
        if (!(actor instanceof StandardButton)) {
            return null;
        }
        Runnable run = Captured.runnable((StandardButton) actor);
        return Captured.builtBy(run, APIUtils.class, "makeTMPageAPI2") ? Captured.value(run, Pipe.class) : null;
    }

    // The button's own caption and state, then the syntax it opens.
    private static void letterButton(GraphBuilder b, Actor actor, final Pipe pipe, ActorNodes.Place place) {
        NodeVtable vt = ActorNodes.buttonFor(actor, place);
        List<NodeAnnouncement> parts = new ArrayList<NodeAnnouncement>(vt.announcements);
        parts.add(1, NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return syntax(pipe, false);
            }
        }, AnnouncementKinds.VALUE));
        parts.add(2, more(pipe));
        vt.announcements = parts;
        b.addItem(ActorNodes.actorId(actor), vt);
    }

    // The syntax api-2 shows: the page the game saved as shown is "3" and
    // the pipe's tag, its documentation.
    private static String shownDoc() {
        String page = com.tann.dice.Main.getSettings().getLastTextmodPage();
        return page != null && page.startsWith("3") && page.length() > 1 ? page.substring(1) : null;
    }

    private static boolean isShownDoc(Actor actor, String shown) {
        return shown != null && actor instanceof TextWriter
                && ((TextWriter) actor).text.equals("[notranslate]" + shown);
    }

    private static void shownLine(GraphBuilder b, final String shown) {
        final Pipe pipe = pipeTagged(shown);
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TEXT;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return pipe != null ? syntax(pipe, false) : syntax(shown);
            }
        }, AnnouncementKinds.LABEL));
        b.addItem(ControlId.structural(CompositeKey.of("textmod-shown", shown)), vt);
    }

    // ---- shared ----

    // Drawn purple once the option shows them; the option calls them "+more".
    private static NodeAnnouncement more(final Pipe pipe) {
        return NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return pipe.isComplexAPI() ? Loc.get("ui", "textmod.more") : null;
            }
        }, AnnouncementKinds.VALUE);
    }

    private static boolean special(Actor actor, String shown) {
        if (entryPipe(actor) != null || letterPipe(actor) != null || isShownDoc(actor, shown)
                || opensWithHeroItem(actor)) {
            return true;
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                if (special(child, shown)) {
                    return true;
                }
            }
        }
        return false;
    }
}
