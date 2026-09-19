package snd.core.nav;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import snd.contracts.speech.SpeechPipeline;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.ControlId;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphNavigatorTest {
    // The navigator's own wording comes from Loc; install the English lines
    // the assertions read.
    @org.junit.jupiter.api.BeforeEach
    void installWording() {
        java.util.Map<String, String> ui = new java.util.HashMap<String, String>();
        ui.put("nav.no_match", "no match for {text}");
        ui.put("nav.search_cleared", "search cleared");
        ui.put("nav.no_details", "no details");
        java.util.Map<String, java.util.Map<String, String>> tables =
                new java.util.HashMap<String, java.util.Map<String, String>>();
        tables.put("ui", ui);
        snd.core.loc.Loc.installFallback(tables);
        snd.core.loc.Loc.install(snd.core.loc.Loc.FALLBACK_LANGUAGE,
                java.util.Collections.<String, java.util.Map<String, String>>emptyMap());
    }

    static final class Capture {
        final SpeechPipeline pipeline = new SpeechPipeline();
        final List<String> lines = new ArrayList<String>();

        Capture() {
            pipeline.setTap(new SpeechPipeline.Tap() {
                @Override
                public void spoken(String cleanText, boolean interrupt, String source) {
                    lines.add((interrupt ? "!" : "") + cleanText);
                }
            });
        }
    }

    static class ListScreen extends AccessScreen {
        final List<String> items;
        boolean active = true;

        ListScreen(String... items) {
            this.items = new ArrayList<String>(Arrays.asList(items));
        }

        @Override
        public String key() {
            return "list";
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public String screenName() {
            return "Test list";
        }

        @Override
        public void build(GraphBuilder b) {
            for (String i : items) {
                NodeVtable vt = new NodeVtable();
                vt.announcements = Arrays.asList(NodeAnnouncement.of(i));
                b.addItem(ControlId.structural(i), vt);
            }
        }
    }

    @Test
    void landingAnnouncesOnceThenStaysQuiet() {
        Capture cap = new Capture();
        GraphNavigator nav = new GraphNavigator(cap.pipeline);
        nav.attach(new ListScreen("alpha", "beta"));
        nav.ensureFocus();
        nav.ensureFocus();
        nav.ensureFocus();
        assertEquals(Arrays.asList("alpha"), cap.lines); // queued landing, exactly once
    }

    @Test
    void theFocusedSubjectIsWhatTheFocusedControlConcerns() {
        GraphNavigator nav = new GraphNavigator(new Capture().pipeline);
        final Object ranger = new Object();
        nav.attach(new ListScreen("plain", "about ranger") {
            @Override
            public void build(GraphBuilder b) {
                for (String i : items) {
                    NodeVtable vt = new NodeVtable();
                    vt.announcements = Arrays.asList(NodeAnnouncement.of(i));
                    vt.subject = i.startsWith("about") ? ranger : null;
                    b.addItem(ControlId.structural(i), vt);
                }
            }
        });
        org.junit.jupiter.api.Assertions.assertNull(nav.focusedSubject()); // nothing focused yet
        nav.ensureFocus();
        org.junit.jupiter.api.Assertions.assertNull(nav.focusedSubject());
        nav.onAction(NavAction.DOWN);
        org.junit.jupiter.api.Assertions.assertSame(ranger, nav.focusedSubject());
        assertEquals(ControlId.structural("about ranger"), nav.focusedNode().id);
    }

    // Three tabs and a content line; opening a tab is remembered as the screen's own state.
    static final class TabbedScreen extends ListScreen {
        final List<String> opened = new ArrayList<String>();
        String open = "Dice";

        TabbedScreen() {
            super("Basics", "Dice", "Rolling");
        }

        @Override
        public void build(GraphBuilder b) {
            b.beginStop("tabs");
            for (final String tab : items) {
                NodeVtable vt = new NodeVtable();
                vt.controlType = snd.core.graph.ControlTypes.TAB;
                vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return tab;
                    }
                }, AnnouncementKinds.LABEL));
                vt.selected = new java.util.function.BooleanSupplier() {
                    @Override
                    public boolean getAsBoolean() {
                        return tab.equals(open);
                    }
                };
                vt.onActivate = new Runnable() {
                    @Override
                    public void run() {
                        open = tab;
                        opened.add(tab);
                    }
                };
                b.addItem(ControlId.structural(tab), vt);
            }
            b.beginStop("content");
            NodeVtable line = new NodeVtable();
            line.announcements = Arrays.asList(NodeAnnouncement.of("about " + open));
            b.addItem(ControlId.structural("content"), line);
        }
    }

    @Test
    void movingOntoATabOpensItAndNoTabSaysSelected() {
        Capture cap = new Capture();
        GraphNavigator nav = new GraphNavigator(cap.pipeline);
        TabbedScreen screen = new TabbedScreen();
        nav.attach(screen);
        nav.ensureFocus();

        // Focus lands on the tab that is open, silently selected; landing opens nothing.
        assertEquals(ControlId.structural("Dice"), nav.focusedId());
        assertTrue(screen.opened.isEmpty());

        // Arrowing onto a tab opens it; it is read as a tab, never as "selected".
        nav.onAction(NavAction.DOWN);
        assertEquals(Arrays.asList("Rolling"), screen.opened);
        nav.onAction(NavAction.HOME);
        assertEquals(Arrays.asList("Rolling", "Basics"), screen.opened);
        for (String line : cap.lines) {
            assertTrue(!line.contains("selected"), line);
        }

        // The content under it follows, and the tab is not read a second time for the rebuild.
        int said = cap.lines.size();
        nav.ensureFocus();
        assertEquals(said, cap.lines.size());
        nav.onAction(NavAction.NEXT_STOP);
        assertEquals("!about Basics", cap.lines.get(cap.lines.size() - 1));

        // Tab back: lands on the open tab, and landing on it opens nothing again.
        nav.onAction(NavAction.NEXT_STOP);
        assertEquals(ControlId.structural("Basics"), nav.focusedId());
        assertEquals(Arrays.asList("Rolling", "Basics"), screen.opened);
    }

    @Test
    void moveAnnouncesInterrupting() {
        Capture cap = new Capture();
        GraphNavigator nav = new GraphNavigator(cap.pipeline);
        nav.attach(new ListScreen("alpha", "beta"));
        nav.ensureFocus();
        assertTrue(nav.onAction(NavAction.DOWN));
        assertEquals(Arrays.asList("alpha", "!beta"), cap.lines);
    }

    @Test
    void contentChangeUnderFocusAnnouncesTheNewLanding() {
        Capture cap = new Capture();
        GraphNavigator nav = new GraphNavigator(cap.pipeline);
        ListScreen screen = new ListScreen("alpha", "beta", "gamma");
        nav.attach(screen);
        nav.ensureFocus();
        nav.onAction(NavAction.END); // on gamma
        cap.lines.clear();

        screen.items.remove("gamma"); // the game yanks the focused row
        nav.ensureFocus();
        assertEquals(Arrays.asList("beta"), cap.lines); // survivor announced once
        nav.ensureFocus();
        assertEquals(Arrays.asList("beta"), cap.lines); // and only once
    }

    @Test
    void livePartSpeaksOnlyTheChange() {
        Capture cap = new Capture();
        GraphNavigator nav = new GraphNavigator(cap.pipeline);
        final String[] value = {"off"};
        AccessScreen screen = new AccessScreen() {
            @Override
            public String key() {
                return "live";
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void build(GraphBuilder b) {
                NodeVtable vt = new NodeVtable();
                vt.announcements = Arrays.asList(
                        NodeAnnouncement.of("Power"),
                        new NodeAnnouncement(new Supplier<String>() {
                            @Override
                            public String get() {
                                return value[0];
                            }
                        }, true, AnnouncementKinds.VALUE));
                b.addItem(ControlId.structural("power"), vt);
            }
        };
        nav.attach(screen);
        nav.ensureFocus();
        assertEquals(Arrays.asList("Power, off"), cap.lines);

        value[0] = "on"; // the game flips the state under focus
        nav.ensureFocus();
        assertEquals(Arrays.asList("Power, off", "on"), cap.lines); // just the part
    }

    @Test
    void perScreenStateRestoresOnReturn() {
        Capture cap = new Capture();
        GraphNavigator nav = new GraphNavigator(cap.pipeline);
        ListScreen a = new ListScreen("a1", "a2");
        ListScreen b = new ListScreen("b1", "b2");
        nav.attach(a);
        nav.ensureFocus();
        nav.onAction(NavAction.DOWN); // a2

        nav.attach(b);
        nav.ensureFocus(); // b1
        nav.attach(a);
        cap.lines.clear();
        nav.ensureFocus();
        assertEquals(Arrays.asList("a2"), cap.lines); // restored, re-announced once
    }

    @Test
    void typeaheadLandsOnMatchAndArrowsCycleResults() {
        Capture cap = new Capture();
        GraphNavigator nav = new GraphNavigator(cap.pipeline);
        nav.attach(new ListScreen("Continue", "Load Game", "License"));
        nav.ensureFocus();
        cap.lines.clear();

        nav.typeChar('l');
        assertEquals(Arrays.asList("!Load Game"), cap.lines);
        assertTrue(nav.searchActive());

        assertTrue(nav.onAction(NavAction.DOWN)); // next result, not a list move
        assertEquals(Arrays.asList("!Load Game", "!License"), cap.lines);

        assertTrue(nav.onAction(NavAction.CANCEL));
        assertEquals("!search cleared", cap.lines.get(cap.lines.size() - 1));
    }

    @Test
    void screenManagerAttachesTopAndDropsClosedState() {
        Capture cap = new Capture();
        GraphNavigator nav = new GraphNavigator(cap.pipeline);
        ScreenManager mgr = new ScreenManager(nav, cap.pipeline);
        ListScreen base = new ListScreen("base1", "base2");
        ListScreen modal = new ListScreen("m1", "m2") {
            @Override
            public String key() {
                return "modal";
            }

            @Override
            public int layer() {
                return 10;
            }

            @Override
            public String screenName() {
                return "Modal";
            }
        };
        modal.active = false;
        mgr.register(base);
        mgr.register(modal);

        mgr.tick();
        assertEquals(base, mgr.current());
        nav.onAction(NavAction.DOWN); // base2

        modal.active = true;
        mgr.tick(); // modal covers base
        assertEquals(modal, mgr.current());

        modal.active = false;
        cap.lines.clear();
        mgr.tick(); // base uncovered — state kept, focus restored on base2
        assertEquals(base, mgr.current());
        assertTrue(cap.lines.contains("base2"), "expected base2 restore in " + cap.lines);
    }
}
