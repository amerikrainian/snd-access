package snd.core.input;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import snd.core.loc.Loc;
import snd.core.nav.NavAction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The mod's key table: a press resolves to the most specific chord that
// matches, held modifiers nothing asks for are ignored, and what no chord
// matches is not ours.
class InputRegistryTest {
    private static final int UP = 19;
    private static final int TAB = 61;
    private static final int BACKSPACE = 67;
    private static final int ENTER = 66;
    private static final int NUMPAD_ENTER = 160;
    private static final int NUM_1 = 8;

    @BeforeEach
    void installWording() {
        Map<String, String> ui = new HashMap<String, String>();
        ui.put("key.up", "Up arrow");
        ui.put("key.tab", "Tab");
        ui.put("key.enter", "Enter");
        ui.put("key.digits", "1 to {n}");
        ui.put("key.shift", "Shift+{key}");
        ui.put("key.ctrl", "Ctrl+{key}");
        Map<String, Map<String, String>> tables = new HashMap<String, Map<String, String>>();
        tables.put("ui", ui);
        Loc.installFallback(tables);
        Loc.install(Loc.FALLBACK_LANGUAGE, Collections.<String, Map<String, String>>emptyMap());
    }

    private final InputAction up = InputAction.nav(NavAction.UP, "l").bind(KeyChord.of(UP, "key.up"));
    private final InputAction regionPrev = InputAction.nav(NavAction.REGION_PREV, "l").bind(KeyChord.of(UP, "key.up").ctrl());
    private final InputAction next = InputAction.nav(NavAction.NEXT_STOP, "l").bind(KeyChord.of(TAB, "key.tab"));
    private final InputAction prev = InputAction.nav(NavAction.PREV_STOP, "l").bind(KeyChord.of(TAB, "key.tab").shift());
    private final InputAction activate = InputAction.nav(NavAction.ACTIVATE, "l")
            .bind(KeyChord.of(ENTER, "key.enter")).alias(KeyChord.of(NUMPAD_ENTER, "key.enter"));
    private final InputAction heroGlance = InputAction.of("glance.hero", "l").bind(KeyChord.digits().ctrl());
    private final InputAction enemyGlance = InputAction.of("glance.enemy", "l").bind(KeyChord.digits().ctrl().shift());

    private InputRegistry registry() {
        InputRegistry r = new InputRegistry();
        r.register(up);
        r.register(regionPrev);
        r.register(next);
        r.register(prev);
        r.register(activate);
        r.register(heroGlance);
        r.register(enemyGlance);
        return r;
    }

    @Test
    void theMostSpecificChordWinsAndUnaskedModifiersAreIgnored() {
        InputRegistry r = registry();
        assertSame(up, r.match(UP, -1, false, false));
        assertSame(up, r.match(UP, -1, true, false)); // Shift+Up still moves up
        assertSame(regionPrev, r.match(UP, -1, false, true));
        assertSame(regionPrev, r.match(UP, -1, true, true));
        assertSame(next, r.match(TAB, -1, false, false));
        assertSame(next, r.match(TAB, -1, false, true)); // Ctrl+Tab is Tab
        assertSame(prev, r.match(TAB, -1, true, false));
    }

    @Test
    void whatNoChordMatchesIsNotOurs() {
        InputRegistry r = registry();
        assertNull(r.match(BACKSPACE, -1, false, false));
        // The plain digits and their Shift tier are the game's: only the
        // Ctrl tiers are bound.
        assertNull(r.match(NUM_1, 0, false, false));
        assertNull(r.match(NUM_1, 0, true, false));
        assertSame(heroGlance, r.match(NUM_1, 0, false, true));
        assertSame(enemyGlance, r.match(NUM_1, 0, true, true));
        // A digits chord answers digit keys only.
        assertNull(r.match(BACKSPACE, -1, false, true));
    }

    @Test
    void aNamedKeyBeatsTheDigitsFamilyAtTheSameModifiers() {
        InputRegistry r = new InputRegistry();
        // Registered family first: the order must not decide it.
        InputAction reserved = r.register(InputAction.of("reserved", "l")
                .bind(KeyChord.digits().ctrl()).bind(KeyChord.digits().ctrl().shift()));
        InputAction vitals = r.register(InputAction.of("glance.vitals", "l").bind(KeyChord.of(NUM_1, "key.1").ctrl()));
        assertSame(vitals, r.match(NUM_1, 0, false, true));
        assertSame(reserved, r.match(NUM_1 + 1, 1, false, true));
        // More modifiers still outrank the named key: Ctrl+Shift+1 is the family's.
        assertSame(reserved, r.match(NUM_1, 0, true, true));
        assertNull(r.match(NUM_1, 0, false, false));
    }

    @Test
    void anAliasMatchesButIsNotSpoken() {
        InputRegistry r = registry();
        assertSame(activate, r.find("nav.ACTIVATE"));
        assertNull(r.find("nav.NOPE"));
        assertSame(activate, r.match(NUMPAD_ENTER, -1, false, false));
        assertEquals("Enter", activate.keysDisplay());
    }

    @Test
    void chordsAreSpokenWithTheirModifiersAndDigitRange() {
        assertEquals("Shift+Tab", prev.keysDisplay());
        assertEquals("Ctrl+Up arrow", regionPrev.keysDisplay());
        enemyGlance.digitCount(new IntSupplier() {
            @Override
            public int getAsInt() {
                return 2;
            }
        });
        assertEquals("Ctrl+Shift+1 to 2", enemyGlance.keysDisplay());
        heroGlance.digitCount(new IntSupplier() {
            @Override
            public int getAsInt() {
                return 1;
            }
        });
        assertEquals("Ctrl+1", heroGlance.keysDisplay());
        assertTrue(heroGlance.isDigitFamily());
        assertFalse(prev.isDigitFamily());
    }

    @Test
    void aChordOrAnIdCannotBeRegisteredTwice() {
        final InputRegistry r = registry();
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                r.register(InputAction.of("other", "l").bind(KeyChord.of(TAB, "key.tab").shift()));
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                r.register(InputAction.of("glance.hero", "l").bind(KeyChord.of(BACKSPACE, "key.tab")));
            }
        });
    }

    @Test
    void anAvailabilityCheckThatThrowsReadsAsNotAvailable() {
        InputAction broken = InputAction.of("broken", "l").when(new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                throw new IllegalStateException("game not loaded");
            }
        });
        assertFalse(broken.isAvailable());
        assertTrue(InputAction.of("always", "l").isAvailable());
    }
}
