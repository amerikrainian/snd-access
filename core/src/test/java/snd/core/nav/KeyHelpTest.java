package snd.core.nav;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import snd.contracts.speech.SpeechPipeline;
import snd.core.graph.ControlId;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.input.InputAction;
import snd.core.input.InputRegistry;
import snd.core.input.KeyChord;
import snd.core.loc.Loc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The context-sensitive key help: a key is listed only where it would do
// something, asked of the navigator without acting and of the attached
// screen's offers.
class KeyHelpTest {
    @BeforeEach
    void installWording() {
        Map<String, String> ui = new HashMap<String, String>();
        for (NavAction a : NavAction.values()) {
            ui.put("help.nav." + a.name(), "do " + a.name());
            ui.put("key." + a.name(), "key " + a.name());
        }
        ui.put("key.digits", "1 to {n}");
        ui.put("key.shift", "Shift+{key}");
        ui.put("key.ctrl", "Ctrl+{key}");
        ui.put("key.f1", "F1");
        ui.put("key.m", "M");
        ui.put("help.title", "Keys here");
        ui.put("help.glance", "hero hp");
        ui.put("help.mute", "mute");
        Map<String, Map<String, String>> tables = new HashMap<String, Map<String, String>>();
        tables.put("ui", ui);
        Loc.installFallback(tables);
        Loc.install(Loc.FALLBACK_LANGUAGE, Collections.<String, Map<String, String>>emptyMap());
    }

    private boolean inFight = true;
    private int glanced = -2;
    private int muted;

    // The navigator keys in the help's order, a glance that applies only in a
    // fight, an always-on key, and the help's own key, which stays unlisted.
    private InputRegistry registry() {
        InputRegistry r = new InputRegistry();
        r.register(InputAction.of("help", "help.title").bind(KeyChord.of(131, "key.f1")).unlisted()
                .handle(new java.util.function.IntConsumer() {
                    @Override
                    public void accept(int digit) {
                    }
                }));
        r.register(InputAction.of("mute", "help.mute").bind(KeyChord.of(41, "key.m"))
                .handle(new java.util.function.IntConsumer() {
                    @Override
                    public void accept(int digit) {
                        muted++;
                    }
                }));
        NavAction[] order = {
            NavAction.ACTIVATE, NavAction.SECONDARY, NavAction.UP, NavAction.DOWN,
            NavAction.LEFT, NavAction.RIGHT, NavAction.NEXT_STOP, NavAction.PREV_STOP, NavAction.HOME,
            NavAction.END, NavAction.REGION_PREV, NavAction.REGION_NEXT,
        };
        for (NavAction a : order) {
            r.register(InputAction.nav(a, "help.nav." + a.name()).bind(KeyChord.of(200 + a.ordinal(), "key." + a.name())));
        }
        r.register(InputAction.of("glance", "help.glance").bind(KeyChord.digits().ctrl())
                .when(new java.util.function.BooleanSupplier() {
                    @Override
                    public boolean getAsBoolean() {
                        return inFight;
                    }
                })
                .digitCount(new java.util.function.IntSupplier() {
                    @Override
                    public int getAsInt() {
                        return 5;
                    }
                })
                .handle(new java.util.function.IntConsumer() {
                    @Override
                    public void accept(int digit) {
                        glanced = digit;
                    }
                }));
        return r;
    }

    static final class RollScreen extends AccessScreen {
        boolean rolling = true;
        int rolled;

        @Override
        public String key() {
            return "test.roll";
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public String screenName() {
            return "Fight";
        }

        @Override
        public void build(GraphBuilder b) {
            b.beginStop("heroes");
            b.addItem(ControlId.structural("a"), node("a", true));
            b.addItem(ControlId.structural("b"), node("b", false));
        }

        private static NodeVtable node(String name, boolean activates) {
            NodeVtable vt = new NodeVtable();
            vt.announcements = Arrays.asList(NodeAnnouncement.of(name));
            if (activates) {
                vt.onActivate = new Runnable() {
                    @Override
                    public void run() {
                    }
                };
            }
            return vt;
        }

        @Override
        public List<KeyOffer> keys() {
            if (!rolling) {
                return Collections.emptyList();
            }
            return Arrays.asList(
                    new KeyOffer("roll", "R", "roll dice", new Runnable() {
                        @Override
                        public void run() {
                            rolled++;
                        }
                    }),
                    new KeyOffer("digits", "1 to 5", "lock/unlock dice", null));
        }
    }

    static final class Frames implements GraphNavigator.FrameClock {
        long now = 1;

        @Override
        public long frame() {
            return now;
        }
    }

    private static List<String> capture(SpeechPipeline pipeline) {
        final List<String> lines = new ArrayList<String>();
        pipeline.setTap(new SpeechPipeline.Tap() {
            @Override
            public void spoken(String cleanText, boolean interrupt, String source) {
                lines.add((interrupt ? "!" : "") + cleanText);
            }
        });
        return lines;
    }

    private static List<String> ids(List<KeyHelp.Entry> entries) {
        List<String> ids = new ArrayList<String>();
        for (KeyHelp.Entry e : entries) {
            ids.add(e.id);
        }
        return ids;
    }

    @Test
    void onlyTheKeysThatWouldDoSomethingAreListedTheScreensOwnFirst() {
        GraphNavigator nav = new GraphNavigator(new SpeechPipeline());
        RollScreen screen = new RollScreen();
        nav.attach(screen);
        nav.ensureFocus();

        // Focus on "a", the first of two in one stop: Down and End have a
        // way, Up and Home none; Enter activates; nothing answers the
        // Backspace tiers; one stop, so no Tab. The screen's keys lead.
        List<KeyHelp.Entry> listed = KeyHelp.collect(nav, registry());
        assertEquals(Arrays.asList("key:roll", "key:digits", "glance", "nav.ACTIVATE", "nav.DOWN", "nav.END", "mute"),
                ids(listed));
        // A key the screen offers reads by the screen's label: what it does HERE.
        assertEquals("roll dice", listed.get(0).label);
        assertEquals("R", listed.get(0).keys);
        assertEquals("do DOWN", listed.get(4).label);
        assertEquals("key DOWN", listed.get(4).keys);
        // A digits family reads as its range and has nothing single to run.
        assertEquals("Ctrl+1 to 5", listed.get(2).keys);
        assertNull(listed.get(2).run);
        // A key range is listed with nothing to run.
        assertNull(listed.get(1).run);

        // The screen stops offering: its keys leave the list.
        screen.rolling = false;
        assertEquals(Arrays.asList("glance", "nav.ACTIVATE", "nav.DOWN", "nav.END", "mute"),
                ids(KeyHelp.collect(nav, registry())));

        // A handler key that says it does not apply here is not listed.
        inFight = false;
        assertEquals(Arrays.asList("nav.ACTIVATE", "nav.DOWN", "nav.END", "mute"), ids(KeyHelp.collect(nav, registry())));
    }

    @Test
    void anEntryRunsItsKey() {
        GraphNavigator nav = new GraphNavigator(new SpeechPipeline());
        RollScreen screen = new RollScreen();
        nav.attach(screen);
        nav.ensureFocus();

        List<KeyHelp.Entry> listed = KeyHelp.collect(nav, registry());
        listed.get(0).run.run();
        assertEquals(1, screen.rolled);
        listed.get(4).run.run(); // Navigate down
        assertEquals(ControlId.structural("b"), nav.focusedId());
        listed.get(listed.size() - 1).run.run(); // the always-on handler key
        assertEquals(1, muted);
    }

    @Test
    void theDryRunFollowsTheFocusedControl() {
        GraphNavigator nav = new GraphNavigator(new SpeechPipeline());
        nav.attach(new RollScreen());
        nav.ensureFocus();
        assertTrue(nav.wouldHandle(NavAction.ACTIVATE));
        assertTrue(nav.onAction(NavAction.DOWN));
        // "b": Up now has the way, Down has none, and Enter does nothing here.
        assertTrue(nav.wouldHandle(NavAction.UP));
        assertTrue(nav.wouldHandle(NavAction.HOME));
        assertFalse(nav.wouldHandle(NavAction.DOWN));
        assertFalse(nav.wouldHandle(NavAction.END));
        assertFalse(nav.wouldHandle(NavAction.ACTIVATE));
        assertFalse(nav.wouldHandle(NavAction.NEXT_STOP));
        // Asking moved nothing.
        assertEquals(ControlId.structural("b"), nav.focusedId());
    }

    @Test
    void tabIsListedOnlyTowardAnotherStop() {
        GraphNavigator nav = new GraphNavigator(new SpeechPipeline());
        final boolean[] wraps = { false };
        nav.attach(new AccessScreen() {
            @Override
            public String key() {
                return "test.stops";
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public boolean wrap() {
                return wraps[0];
            }

            @Override
            public void build(GraphBuilder b) {
                for (String stop : new String[] { "first", "second" }) {
                    b.beginStop(stop);
                    NodeVtable vt = new NodeVtable();
                    vt.announcements = Arrays.asList(NodeAnnouncement.of(stop));
                    b.addItem(ControlId.structural(stop), vt);
                }
            }
        });
        nav.ensureFocus();

        // On the first of two stops: Tab has somewhere to go, Shift+Tab none.
        assertTrue(nav.wouldHandle(NavAction.NEXT_STOP));
        assertFalse(nav.wouldHandle(NavAction.PREV_STOP));
        nav.onAction(NavAction.NEXT_STOP);
        assertFalse(nav.wouldHandle(NavAction.NEXT_STOP));
        assertTrue(nav.wouldHandle(NavAction.PREV_STOP));
        // A wrapping screen goes round either way.
        wraps[0] = true;
        assertTrue(nav.wouldHandle(NavAction.NEXT_STOP));
    }

    @Test
    void onlyHandlerKeysAreListedWithNoScreenAttached() {
        GraphNavigator nav = new GraphNavigator(new SpeechPipeline());
        // Only the handler keys remain: no screen offers, no control to navigate.
        assertEquals(Arrays.asList("glance", "mute"), ids(KeyHelp.collect(nav, registry())));
    }

    @Test
    void aQuietLandingIsRecordedNotSpokenAndOnlyOnce() {
        SpeechPipeline pipeline = new SpeechPipeline();
        List<String> spoken = capture(pipeline);
        GraphNavigator nav = new GraphNavigator(pipeline);
        Frames clock = new Frames();
        nav.setFrameClock(clock);
        RollScreen screen = new RollScreen();
        nav.attach(screen);
        nav.ensureFocus();
        assertEquals("a", spoken.get(spoken.size() - 1));

        // An overlay closes over the screen. Left alone, the landing it
        // brings is spoken.
        int count = spoken.size();
        nav.attach(null);
        nav.attach(screen);
        nav.ensureFocus();
        assertEquals(count + 1, spoken.size());

        // Asked to be quiet, it is recorded without a word.
        nav.attach(null);
        nav.quietNextLanding();
        nav.attach(screen);
        nav.ensureFocus();
        nav.ensureFocus();
        assertEquals(count + 1, spoken.size());
        assertEquals(ControlId.structural("a"), nav.focusedId());

        // Once only: the next landing speaks as ever.
        nav.attach(null);
        nav.attach(screen);
        nav.ensureFocus();
        assertEquals(count + 2, spoken.size());

        // And a request nothing consumed goes stale instead of swallowing a
        // later landing.
        nav.quietNextLanding();
        clock.now += 600;
        nav.attach(null);
        nav.attach(screen);
        nav.ensureFocus();
        assertEquals(count + 3, spoken.size());
    }

    @Test
    void aQuietLandingCoversTheReturningScreensName() {
        SpeechPipeline pipeline = new SpeechPipeline();
        List<String> spoken = capture(pipeline);
        GraphNavigator nav = new GraphNavigator(pipeline);
        nav.setFrameClock(new Frames());
        ScreenManager screens = new ScreenManager(nav, pipeline);
        screens.register(new RollScreen());
        screens.tick();
        assertEquals(Arrays.asList("!Fight", "a"), spoken);

        final boolean[] open = { true };
        screens.register(new AccessScreen() {
            @Override
            public String key() {
                return "test.overlay";
            }

            @Override
            public boolean isActive() {
                return open[0];
            }

            @Override
            public int layer() {
                return 50;
            }

            @Override
            public void build(GraphBuilder b) {
                NodeVtable vt = new NodeVtable();
                vt.announcements = Arrays.asList(NodeAnnouncement.of("row"));
                b.addItem(ControlId.structural("row"), vt);
            }
        });
        screens.tick();
        spoken.clear();

        nav.quietNextLanding();
        open[0] = false;
        screens.tick();
        assertTrue(spoken.isEmpty());
        // The focus is read back on request, queued, and the differ stays quiet after.
        nav.readFocus();
        screens.tick();
        assertEquals(1, spoken.size());
        assertTrue(spoken.get(0).startsWith("a"));
    }

    @Test
    void aHoldQueuesWhatWouldInterruptAndRemembersIt() {
        SpeechPipeline pipeline = new SpeechPipeline();
        List<String> spoken = capture(pipeline);
        final long[] now = { 0 };
        pipeline.setClock(new SpeechPipeline.Clock() {
            @Override
            public long nanos() {
                return now[0];
            }
        });

        // The first line of a hold interrupts, whatever it asked for: it cuts
        // off the help row that was being read when Enter was pressed. What
        // follows queues behind it, in order.
        pipeline.speak("roll dice: R", true);
        pipeline.beginHold();
        pipeline.speak("rolled", false);
        pipeline.speak("2 rerolls left", true);
        pipeline.speak("Fighter", false);
        assertEquals(Arrays.asList("!roll dice: R", "!rolled", "2 rerolls left", "Fighter"), spoken);
        assertEquals(Arrays.asList("rolled", "2 rerolls left", "Fighter"), pipeline.held());

        pipeline.endHold();
        pipeline.speak("moved", true);
        assertEquals("!moved", spoken.get(spoken.size() - 1));

        // A hold nobody ends lapses by itself.
        pipeline.beginHold();
        now[0] += 10000000000L;
        pipeline.speak("later", true);
        assertEquals("!later", spoken.get(spoken.size() - 1));
        assertFalse(pipeline.holding());
    }
}
