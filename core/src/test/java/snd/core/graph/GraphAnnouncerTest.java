package snd.core.graph;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Ported from wotr-access GraphAnnouncerTests — the announcer spec. */
class GraphAnnouncerTest {
    @AfterEach
    void resetHooks() {
        GraphAnnouncer.partFilter = null;
        GraphAnnouncer.positionText = null;
        GraphAnnouncer.expandedStateText = null;
    }

    private static GraphNode node(String label) {
        return node(label, null);
    }

    private static GraphNode node(String label, GraphNode parent) {
        GraphNode n = new GraphNode();
        n.id = ControlId.structural(label);
        n.vtable = new NodeVtable();
        n.vtable.announcements = Arrays.asList(NodeAnnouncement.of(label));
        n.parent = parent;
        return n;
    }

    private static GraphNode context(String label) {
        return context(label, null, null);
    }

    private static GraphNode context(String label, String role) {
        return context(label, role, null);
    }

    private static GraphNode context(String label, String role, GraphNode parent) {
        GraphNode n = new GraphNode();
        n.id = ControlId.structural("ctx:" + label);
        n.vtable = new NodeVtable();
        n.vtable.announcements = role == null
                ? Arrays.asList(NodeAnnouncement.of(label))
                : Arrays.asList(NodeAnnouncement.of(label), NodeAnnouncement.of(role));
        n.parent = parent;
        n.focusable = false;
        return n;
    }

    @Test
    void entryFromNothingReadsFullChain() {
        GraphNode options = context("Options");
        GraphNode list = context("Difficulty settings", "list", options);
        GraphNode node = node("Normal, radio button, selected", list);

        assertEquals("Options, Difficulty settings, list, Normal, radio button, selected",
                GraphAnnouncer.composeFull(node));
    }

    @Test
    void siblingMoveReadsLeafOnly() {
        GraphNode list = context("Difficulty settings", "list");
        GraphNode from = node("Easy", list);
        GraphNode to = node("Hard", list);

        assertEquals("Hard", GraphAnnouncer.compose(from, to));
    }

    @Test
    void enteringNestedContextReadsEnteredLevels() {
        GraphNode outer = context("Options");
        GraphNode from = node("Back", outer);
        GraphNode list = context("Difficulty settings", "list", outer);
        GraphNode to = node("Normal", list);

        assertEquals("Difficulty settings, list, Normal", GraphAnnouncer.compose(from, to));
    }

    @Test
    void ascendReadsLeafOnly() {
        GraphNode outer = context("Options");
        GraphNode list = context("Difficulty settings", "list", outer);
        GraphNode from = node("Normal", list);
        GraphNode to = node("Back", outer);

        assertEquals("Back", GraphAnnouncer.compose(from, to));
    }

    @Test
    void descendingFromGroupOntoItsChildReadsChildOnly() {
        // The group is ON the child's chain AND is the from-node: the prefix
        // swallows it.
        GraphNode group = node("Combat");
        group.expandable = true;
        group.expanded = true;
        GraphNode child = node("Auto pause on combat start, toggle, on", group);

        assertEquals("Auto pause on combat start, toggle, on", GraphAnnouncer.compose(group, child));
    }

    @Test
    void enteringAGroupFromOutsideReadsTheGroup() {
        GraphNode group = node("Combat");
        group.expandable = true;
        group.expanded = true;
        GraphNode child = node("Auto pause on combat start, toggle, on", group);
        GraphNode elsewhere = node("Tabs");

        assertEquals("Combat, Auto pause on combat start, toggle, on",
                GraphAnnouncer.compose(elsewhere, child));
    }

    @Test
    void expandedStateWordAppendsToGroups() {
        GraphNode group = node("Combat");
        group.expandable = true;
        group.expanded = false;
        GraphAnnouncer.expandedStateText = new GraphAnnouncer.ExpandedStateText() {
            @Override
            public String text(boolean expanded) {
                return expanded ? "expanded" : "collapsed";
            }
        };
        assertEquals("Combat, collapsed", GraphAnnouncer.composeFull(group));
        group.expanded = true;
        assertEquals("Combat, expanded", GraphAnnouncer.composeFull(group));
        group.vtable.speaksOwnExpansion = true; // node carries its own state word
        assertEquals("Combat", GraphAnnouncer.composeFull(group));
    }

    @Test
    void duplicateContainerLabelIsSkipped() {
        // A "Game difficulty" section wrapping the "Game difficulty" control:
        // the section stays silent.
        GraphNode section = context("Game difficulty");
        GraphNode to = node("Game difficulty, menu button", section);
        assertEquals("Game difficulty, menu button", GraphAnnouncer.composeFull(to));

        // But a control that merely STARTS with different text keeps its container.
        GraphNode other = node("Game difficulty presets, menu button", context("Game difficulty"));
        assertEquals("Game difficulty, Game difficulty presets, menu button",
                GraphAnnouncer.composeFull(other));
    }

    private static final ControlType TEST_BUTTON = new ControlType(
            "button",
            new String[]{AnnouncementKinds.STATE, AnnouncementKinds.LABEL, AnnouncementKinds.ROLE,
                    AnnouncementKinds.VALUE, AnnouncementKinds.ENABLED, AnnouncementKinds.POSITION},
            new Supplier<List<NodeAnnouncement>>() {
                @Override
                public List<NodeAnnouncement> get() {
                    return Collections.singletonList(
                            NodeAnnouncement.kinded(fixed("button"), AnnouncementKinds.ROLE));
                }
            });

    private static Supplier<String> fixed(final String s) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return s;
            }
        };
    }

    private static GraphNode typedNode(ControlType type, NodeAnnouncement... parts) {
        GraphNode n = new GraphNode();
        n.id = ControlId.structural("typed");
        n.vtable = new NodeVtable();
        n.vtable.controlType = type;
        n.vtable.announcements = Arrays.asList(parts);
        return n;
    }

    @Test
    void controlTypeSuppliesRoleAndOrdering() {
        // Parts declared out of order — the type's kind order sorts them; the
        // common role merges in.
        GraphNode node = typedNode(TEST_BUTTON,
                NodeAnnouncement.kinded(fixed("on"), AnnouncementKinds.VALUE),
                NodeAnnouncement.kinded(fixed("Hold position"), AnnouncementKinds.LABEL));

        assertEquals("Hold position, button, on", GraphAnnouncer.composeFull(node));
    }

    @Test
    void statePartSpeaksBeforeTheLabel() {
        // Declared last, spoken first — and the first DECLARED part (the
        // label, which search and dedupe read) is unaffected.
        GraphNode node = typedNode(TEST_BUTTON,
                NodeAnnouncement.kinded(fixed("Ranger"), AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(fixed("2 damage"), AnnouncementKinds.VALUE),
                NodeAnnouncement.kinded(fixed("used"), AnnouncementKinds.STATE));

        assertEquals("used, Ranger, button, 2 damage", GraphAnnouncer.composeFull(node));
        assertEquals("Ranger", GraphAnnouncer.firstPartText(node));
    }

    @Test
    void nodePartOverridesCommonOfSameKind() {
        GraphNode node = typedNode(TEST_BUTTON,
                NodeAnnouncement.kinded(fixed("Continue"), AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(fixed("menu button"), AnnouncementKinds.ROLE));

        assertEquals("Continue, menu button", GraphAnnouncer.composeFull(node));
    }

    @Test
    void kindlessPartsKeepDeclarationOrderAfterKnownKinds() {
        GraphNode node = typedNode(TEST_BUTTON,
                new NodeAnnouncement(fixed("custom one")),
                NodeAnnouncement.kinded(fixed("Continue"), AnnouncementKinds.LABEL),
                new NodeAnnouncement(fixed("custom two")));

        assertEquals("Continue, button, custom one, custom two", GraphAnnouncer.composeFull(node));
    }

    @Test
    void partFilterDropsParts() {
        GraphNode node = typedNode(TEST_BUTTON,
                NodeAnnouncement.kinded(fixed("Continue"), AnnouncementKinds.LABEL));
        GraphAnnouncer.partFilter = new GraphAnnouncer.PartFilter() {
            @Override
            public boolean allow(ControlType type, NodeAnnouncement part) {
                return !AnnouncementKinds.ROLE.equals(part.kind);
            }
        };
        assertEquals("Continue", GraphAnnouncer.composeFull(node));
    }

    @Test
    void leafTextJoinsAnnouncementParts() {
        GraphNode node = new GraphNode();
        node.id = ControlId.structural("x");
        node.vtable = new NodeVtable();
        node.vtable.announcements = Arrays.asList(
                NodeAnnouncement.of("Hold position"),
                NodeAnnouncement.of("toggle"),
                new NodeAnnouncement(fixed("on"), true, null),
                new NodeAnnouncement(fixed(null))); // empty at speak time — silent
        assertEquals("Hold position, toggle, on", GraphAnnouncer.composeFull(node));
    }

    @Test
    void transitionLabelLeads() {
        GraphNode from = node("A");
        GraphNode to = node("B");
        assertEquals("next column, B", GraphAnnouncer.compose(from, to, "next column"));
    }

    @Test
    void contextChangeAtSameDepthReadsNewLevel() {
        GraphNode from = node("Fireball", context("Level 1 spells", "table"));
        GraphNode to = node("Haste", context("Level 2 spells", "table"));

        assertEquals("Level 2 spells, table, Haste", GraphAnnouncer.compose(from, to));
    }

    @Test
    void autoPositionAppendsWhenHostProvidesWording() {
        GraphNode n = node("Classic");
        n.positionIndex = 3;
        n.positionCount = 12;
        GraphAnnouncer.positionText = new GraphAnnouncer.PositionText() {
            @Override
            public String text(int index, int count) {
                return index + " of " + count;
            }
        };
        assertEquals("Classic, 3 of 12", GraphAnnouncer.composeFull(n));
        n.vtable.speaksOwnPosition = true;
        assertEquals("Classic", GraphAnnouncer.composeFull(n));
    }
}
