package snd.core.buffers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import snd.contracts.speech.SpeechPipeline;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.GraphNode;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The review buffers: live sources, a cursor that survives re-reads, cycling
// that skips what is empty, and one spoken behavior for the four keys.
class BufferTest {
    @BeforeEach
    void installWording() {
        Map<String, String> ui = new HashMap<String, String>();
        ui.put("buffer.none", "nothing to review");
        ui.put("buffer.line", "{buffer}: {line}");
        Map<String, Map<String, String>> tables = new HashMap<String, Map<String, String>>();
        tables.put("ui", ui);
        Loc.installFallback(tables);
        Loc.install(Loc.FALLBACK_LANGUAGE, Collections.<String, Map<String, String>>emptyMap());
    }

    private static Supplier<String> name(final String n) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return n;
            }
        };
    }

    private static Buffer over(String key, final List<String> lines) {
        return new Buffer(key, name(key)).source(new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return lines;
            }
        });
    }

    private static List<String> list(String... lines) {
        return new ArrayList<String>(Arrays.asList(lines));
    }

    @Test
    void theSourceIsReadLiveAndTheCursorSurvivesWhileInRange() {
        List<String> lines = list("one", "", "two", null, "three");
        Buffer b = over("b", lines);
        assertEquals(Arrays.asList("one", "two", "three"), b.lines()); // blanks dropped
        assertTrue(b.moveNext());
        assertTrue(b.moveNext());
        assertFalse(b.moveNext()); // the edge
        assertEquals("three", b.currentLine());

        lines.add("four"); // the world moved on; the cursor holds its place
        assertEquals("three", b.currentLine());
        assertTrue(b.moveNext());
        assertEquals("four", b.currentLine());

        lines.subList(1, lines.size()).clear(); // shrank under the cursor: back to the top
        assertEquals("one", b.currentLine());
        assertFalse(b.movePrevious());
    }

    @Test
    void aSourceThatThrowsReadsAsEmpty() {
        Buffer b = new Buffer("b", name("b")).source(new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                throw new IllegalStateException("no fight");
            }
        });
        assertTrue(b.isEmpty());
        assertNull(b.currentLine());
        assertTrue(new Buffer("unbound", name("u")).isEmpty());
    }

    @Test
    void cyclingSkipsEmptyBuffersAndWraps() {
        BufferManager m = new BufferManager();
        List<String> hero = list();
        Buffer control = m.add(over("control", list("row")));
        m.add(over("hero", hero));
        Buffer log = m.add(over("log", list("first", "latest")));
        log.followLatest = true;

        assertSame(control, m.current());
        assertTrue(m.moveBuffer(1)); // hero is empty: straight to the log, at its latest line
        assertSame(log, m.current());
        assertEquals("latest", log.currentLine());
        assertTrue(m.moveBuffer(1)); // wraps
        assertSame(control, m.current());

        hero.add("Ranger");
        assertTrue(m.moveBuffer(1));
        assertEquals("hero", m.current().key);
        hero.clear(); // emptied under the cursor: review re-resolves to the first non-empty one
        assertSame(control, m.current());

        m.setCurrent("log");
        assertSame(log, m.current());
    }

    @Test
    void nothingToCycleToLeavesTheCursorWhereItIs() {
        BufferManager m = new BufferManager();
        Buffer only = m.add(over("only", list("row")));
        m.add(over("empty", list()));
        assertFalse(m.moveBuffer(1));
        assertFalse(m.moveBuffer(-1));
        assertSame(only, m.current());
        assertNull(new BufferManager().current());
    }

    @Test
    void theKeysSpeakTheBufferOnASwitchAndTheLineOnAStep() {
        SpeechPipeline pipeline = new SpeechPipeline();
        final List<String> spoken = new ArrayList<String>();
        pipeline.setTap(new SpeechPipeline.Tap() {
            @Override
            public void spoken(String cleanText, boolean interrupt, String source) {
                spoken.add((interrupt ? "!" : "") + cleanText);
            }
        });
        BufferManager m = new BufferManager();
        m.add(over("control", list("Reroll, 2 left", "Rolls every unlocked die")));
        m.add(over("hero", list("Ranger, 9 hp")));
        BufferControls keys = new BufferControls(m, pipeline);

        keys.nextLine();
        keys.nextLine(); // the edge re-reads rather than going silent
        keys.previousLine();
        keys.nextBuffer();
        keys.previousBuffer(); // back on control, where the cursor was left
        assertEquals(Arrays.asList("!Rolls every unlocked die", "!Rolls every unlocked die", "!Reroll, 2 left",
                "!hero: Ranger, 9 hp", "!control: Reroll, 2 left"), spoken);

        spoken.clear();
        BufferControls none = new BufferControls(new BufferManager(), pipeline);
        none.nextBuffer();
        none.nextLine();
        assertEquals(Arrays.asList("!nothing to review", "!nothing to review"), spoken);
    }

    @Test
    void aNodesLinesAreItsHeadThenItsDetailsWithRepeatsFolded() {
        GraphNode node = new GraphNode();
        node.vtable = new NodeVtable();
        node.vtable.announcements = Arrays.asList(
                NodeAnnouncement.kinded(name("used"), AnnouncementKinds.STATE),
                NodeAnnouncement.kinded(name("[orange]Ranger[cu]"), AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(name("button"), AnnouncementKinds.ROLE),
                NodeAnnouncement.kinded(name("9 hp"), AnnouncementKinds.VALUE),
                NodeAnnouncement.kinded(name("ranged: can hit the back row"), AnnouncementKinds.TOOLTIP),
                NodeAnnouncement.kinded(name("3 of 5"), AnnouncementKinds.POSITION),
                NodeAnnouncement.kinded(name(null), AnnouncementKinds.SELECTED));
        node.vtable.details = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return Arrays.asList("Ranger", "ranged: can hit the back row", "", "Weakened 1: all sides reduced by 1");
            }
        };
        // The role word and the position never; "Ranger" repeats the label
        // through its markup, and the second "ranged" an earlier detail.
        assertEquals(Arrays.asList("used, [orange]Ranger[cu], 9 hp", "ranged: can hit the back row",
                "Weakened 1: all sides reduced by 1"), NodeLines.lines(node));
        assertTrue(NodeLines.lines(null).isEmpty());
    }
}
