package snd.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import snd.core.loc.FlatJson;

class SettingsTest {
    private static Supplier<String> label(final String s) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return s;
            }
        };
    }

    private static CategorySetting root() {
        return new CategorySetting("root", label("Settings"));
    }

    @Test
    void pathsJoinKeysAndSkipMenuOnlyGroups() {
        CategorySetting root = root();
        CategorySetting events = root.add(new CategorySetting("events", label("Events"), false));
        CategorySetting combat = events.add(new CategorySetting("combat", label("Combat")));
        BoolSetting announce = combat.add(new BoolSetting("announce", label("Announce"), true));
        Settings settings = new Settings(root);

        assertEquals("combat.announce", announce.path());
        assertSame(announce, settings.find("combat.announce"));
        assertNull(settings.find("events.combat.announce"));
    }

    @Test
    void onlySetValuesAreSaved() {
        CategorySetting root = root();
        BoolSetting a = root.add(new BoolSetting("a", label("A"), true));
        root.add(new IntSetting("n", label("N"), 5, 0, 10, 1));
        Settings settings = new Settings(root);
        assertTrue(settings.snapshot().isEmpty());

        a.set(false);
        assertEquals(singleton("a", "false"), settings.snapshot());
        a.reset();
        assertTrue(settings.snapshot().isEmpty());
    }

    @Test
    void everyChangeIsWritten() {
        CategorySetting root = root();
        BoolSetting a = root.add(new BoolSetting("a", label("A"), true));
        Settings settings = new Settings(root);
        final List<Map<String, String>> written = new ArrayList<Map<String, String>>();
        settings.setWriter(new Consumer<Map<String, String>>() {
            @Override
            public void accept(Map<String, String> m) {
                written.add(m);
            }
        });

        a.set(false);
        a.reset();
        assertEquals(Arrays.asList(singleton("a", "false"), new LinkedHashMap<String, String>()), written);
    }

    @Test
    void loadRestoresValuesKeepsUnknownEntriesAndSkipsBadOnes() {
        CategorySetting root = root();
        BoolSetting a = root.add(new BoolSetting("a", label("A"), true));
        IntSetting n = root.add(new IntSetting("n", label("N"), 5, 0, 10, 1));
        Settings settings = new Settings(root);

        Map<String, String> saved = new LinkedHashMap<String, String>();
        saved.put("a", "false");
        saved.put("n", "maybe");
        saved.put("from.a.newer.version", "x");
        settings.load(saved);

        assertFalse(a.get());
        assertEquals(5, n.get());
        Map<String, String> expected = new LinkedHashMap<String, String>();
        expected.put("a", "false");
        expected.put("from.a.newer.version", "x");
        assertEquals(expected, settings.snapshot());
    }

    @Test
    void savedFormRoundTripsThroughTheFile() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("readouts.button.order", "label,role,value");
        m.put("odd", "quote \" backslash \\ newline \n tab \t");
        assertEquals(m, FlatJson.parse(FlatJson.write(m)));
        assertEquals(new LinkedHashMap<String, String>(), FlatJson.parse(FlatJson.write(new LinkedHashMap<String, String>())));
    }

    @Test
    void anInheritingToggleFollowsItsFallbackUntilSet() {
        CategorySetting root = root();
        BoolSetting global = root.add(new BoolSetting("global", label("Global"), true));
        BoolSetting local = root.add(BoolSetting.inheriting("local", label("Local"), global));
        new Settings(root);

        assertTrue(local.get());
        assertTrue(local.inherits());
        global.set(false);
        assertFalse(local.get());

        local.toggle(); // differs from the fallback: pinned
        assertTrue(local.get());
        assertFalse(local.inherits());

        local.toggle(); // back to what the fallback says: inherits again
        assertFalse(local.get());
        assertTrue(local.inherits());
        global.set(true);
        assertTrue(local.get());
    }

    @Test
    void intsClampAndStep() {
        CategorySetting root = root();
        IntSetting n = root.add(new IntSetting("n", label("N"), 5, 0, 10, 5));
        new Settings(root);
        assertTrue(n.adjust(1));
        assertEquals(10, n.get());
        assertFalse(n.adjust(1));
        n.set(-3);
        assertEquals(0, n.get());
    }

    @Test
    void choicesHoldOnlyTheirOptions() {
        CategorySetting root = root();
        ChoiceSetting c = root.add(new ChoiceSetting("c", label("C"), Arrays.asList(
                new ChoiceSetting.Option("x", label("X")), new ChoiceSetting.Option("y", label("Y"))), "x"));
        Settings settings = new Settings(root);
        assertEquals(0, c.index());
        settings.load(singleton("c", "z"));
        assertEquals("x", c.get());
        settings.load(singleton("c", "y"));
        assertEquals("Y", c.chosen().label());
    }

    @Test
    void mergeKeepsTheSavedOrderAndSlotsNewPartsAfterTheirDefaultNeighbour() {
        List<String> defaults = Arrays.asList("label", "dying", "owner", "type", "hp", "position");
        assertEquals(Arrays.asList("label", "dying", "type", "owner", "hp", "position"),
                PartOrder.merge(Arrays.asList("label", "type", "owner", "hp", "position"), defaults));
        assertEquals(Arrays.asList("hp", "label", "dying", "owner", "type", "position"),
                PartOrder.merge(Arrays.asList("hp", "label", "owner", "type", "position"), defaults));
    }

    @Test
    void mergePutsAPartWithNoPlacedNeighbourFirstAndDropsGoneParts() {
        List<String> defaults = Arrays.asList("new", "label", "hp");
        assertEquals(Arrays.asList("new", "hp", "label"),
                PartOrder.merge(Arrays.asList("hp", "gone", "label", "hp"), defaults));
    }

    @Test
    void movingAPartSavesTheWholeOrderAndResetRestoresTheDefault() {
        CategorySetting root = root();
        CategorySetting page = root.add(new CategorySetting("button", label("Button")));
        PartOrder order = PartOrder.attach(page, Arrays.asList("label", "role", "value"));
        Settings settings = new Settings(root);

        assertTrue(order.move("value", -1));
        assertEquals(Arrays.asList("label", "value", "role"), order.order());
        assertEquals(singleton("button.order", "label,value,role"), settings.snapshot());
        assertFalse(order.move("label", -1));
        assertFalse(order.move("nope", 1));

        page.reset();
        assertEquals(Arrays.asList("label", "role", "value"), order.order());
        assertTrue(settings.snapshot().isEmpty());
    }

    @Test
    void aPartPageListsItsPartsInTheArrangedOrder() {
        CategorySetting root = root();
        CategorySetting page = root.add(new CategorySetting("button", label("Button")));
        PartOrder order = PartOrder.attach(page, Arrays.asList("label", "role"));
        page.add(new CategorySetting("label", label("Label")));
        page.add(new CategorySetting("role", label("Role")));
        new Settings(root);

        order.move("role", -1);
        List<String> shown = new ArrayList<String>();
        for (Setting s : page.shown()) {
            shown.add(s.key);
        }
        assertEquals(Arrays.asList("role", "label"), shown);
    }

    @Test
    void aComposedLineFollowsTheOrderTheSettingsAndEachPartsSuffix() {
        CategorySetting root = root();
        Announcements a = new Announcements(root);
        a.unsuffixed("affected");
        CategorySetting page = a.event("intent", Arrays.asList("label", "side", "aim"));
        a.event("outcome", Arrays.asList("affected", "damage", "fate"));
        Settings settings = new Settings(root);
        Map<String, String> texts = new LinkedHashMap<String, String>();
        texts.put("aim", "targets Thief");
        texts.put("side", "2 damage");
        texts.put("label", "Rat 1");

        assertEquals("Rat 1, 2 damage, targets Thief", a.compose("events.intent", texts));
        page.partOrder.move("label", 1);
        assertEquals("2 damage, Rat 1, targets Thief", a.compose("events.intent", texts));
        ((BoolSetting) settings.find("announcements.side.enabled")).set(false);
        assertEquals("Rat 1, targets Thief", a.compose("events.intent", texts));
        texts.remove("label");
        texts.remove("aim");
        assertNull(a.compose("events.intent", texts));

        // A part with no suffix runs on into the next, wherever it lands.
        Map<String, String> outcome = new LinkedHashMap<String, String>();
        outcome.put("affected", "Bandit 1");
        outcome.put("damage", "2");
        outcome.put("fate", "defeated");
        assertEquals("Bandit 1 2, defeated", a.compose("events.outcome", outcome));
        assertNull(settings.find("announcements.affected.include_suffix"));
    }

    @Test
    void aPartsCommaCanBeTurnedOffEverywhereOrOnOneReadout() {
        CategorySetting root = root();
        Announcements a = new Announcements(root);
        a.event("intent", Arrays.asList("label", "side"));
        a.event("roll", Arrays.asList("label", "side"));
        a.readout(Announcements.BUFFERS, "unit", Arrays.asList("label", "sides"), false);
        Settings settings = new Settings(root);
        Map<String, String> texts = new LinkedHashMap<String, String>();
        texts.put("label", "Thief");
        texts.put("side", "2 damage");

        ((BoolSetting) settings.find("announcements.label.include_suffix")).set(false);
        assertEquals("Thief 2 damage", a.compose("events.intent", texts));
        assertEquals("Thief 2 damage", a.compose("events.roll", texts));
        ((BoolSetting) settings.find("events.roll.label.include_suffix")).toggle();
        assertEquals("Thief, 2 damage", a.compose("events.roll", texts));
        assertEquals("Thief 2 damage", a.compose("events.intent", texts));
        // A buffer's sections are lines: they take no suffix.
        assertNull(settings.find("buffers.unit.label.include_suffix"));
    }

    @Test
    void aSectionedReadoutListsItsSpeakingSectionsInOrder() {
        CategorySetting root = root();
        Announcements a = new Announcements(root);
        CategorySetting page = a.readout(Announcements.BUFFERS, "unit", Arrays.asList("header", "sides", "rules"));
        Settings settings = new Settings(root);
        Map<String, List<String>> sections = new LinkedHashMap<String, List<String>>();
        sections.put("rules", Arrays.asList("cleave: hits the neighbours"));
        sections.put("header", Arrays.asList("Thief, 4 hp"));
        sections.put("sides", Arrays.asList("left: 2 damage", "right: blank"));

        assertEquals(Arrays.asList("Thief, 4 hp", "left: 2 damage", "right: blank", "cleave: hits the neighbours"),
                a.lines("buffers.unit", sections));
        page.partOrder.move("rules", -1);
        ((BoolSetting) settings.find("buffers.unit.header.enabled")).set(false);
        assertEquals(Arrays.asList("cleave: hits the neighbours", "left: 2 damage", "right: blank"),
                a.lines("buffers.unit", sections));
    }

    private static Map<String, String> singleton(String k, String v) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put(k, v);
        return m;
    }
}
