package snd.core.graph;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ported from wotr-access GraphBuilderTests — the builder spec. */
class GraphBuilderTest {
    private static NodeVtable vt(String label) {
        NodeVtable v = new NodeVtable();
        v.announcements = Arrays.asList(NodeAnnouncement.of(label));
        return v;
    }

    private static ControlId id(String key) {
        return ControlId.structural(key);
    }

    @Test
    void singleItemsFormVerticalMenu() {
        GraphRender render = new GraphBuilder()
                .addItem(id("a"), vt("A"))
                .addItem(id("b"), vt("B"))
                .addItem(id("c"), vt("C"))
                .build();

        assertEquals(id("a"), render.startKey);
        assertEquals(id("b"), render.nodes.get(id("a")).transitions.get(GraphDir.DOWN).destination);
        assertEquals(id("a"), render.nodes.get(id("b")).transitions.get(GraphDir.UP).destination);
        assertEquals(id("c"), render.nodes.get(id("b")).transitions.get(GraphDir.DOWN).destination);
        assertFalse(render.nodes.get(id("a")).transitions.containsKey(GraphDir.UP));
        assertFalse(render.nodes.get(id("a")).transitions.containsKey(GraphDir.LEFT));
        assertFalse(render.nodes.get(id("a")).transitions.containsKey(GraphDir.RIGHT));
    }

    @Test
    void rowsWireHorizontally() {
        GraphRender render = new GraphBuilder()
                .startRow().addItem(id("a"), vt("A")).addItem(id("b"), vt("B")).endRow()
                .build();

        assertEquals(id("b"), render.nodes.get(id("a")).transitions.get(GraphDir.RIGHT).destination);
        assertEquals(id("a"), render.nodes.get(id("b")).transitions.get(GraphDir.LEFT).destination);
    }

    @Test
    void sharedRowKeysPreserveColumn() {
        GraphRender render = new GraphBuilder()
                .startRow("grid").addItem(id("a1"), vt("A1")).addItem(id("a2"), vt("A2")).endRow()
                .startRow("grid").addItem(id("b1"), vt("B1")).addItem(id("b2"), vt("B2")).endRow()
                .build();

        assertEquals(id("b2"), render.nodes.get(id("a2")).transitions.get(GraphDir.DOWN).destination);
        assertEquals(id("a2"), render.nodes.get(id("b2")).transitions.get(GraphDir.UP).destination);
    }

    @Test
    void unkeyedRowsLandOnFirstItem() {
        GraphRender render = new GraphBuilder()
                .startRow().addItem(id("a1"), vt("A1")).addItem(id("a2"), vt("A2")).endRow()
                .startRow().addItem(id("b1"), vt("B1")).addItem(id("b2"), vt("B2")).endRow()
                .build();

        assertEquals(id("b1"), render.nodes.get(id("a2")).transitions.get(GraphDir.DOWN).destination);
    }

    @Test
    void raggedKeyedRowFallsToFirstItem() {
        GraphRender render = new GraphBuilder()
                .startRow("grid").addItem(id("a1"), vt("A1")).addItem(id("a2"), vt("A2")).addItem(id("a3"), vt("A3")).endRow()
                .startRow("grid").addItem(id("b1"), vt("B1")).endRow()
                .build();

        // Column 2 doesn't exist below → first item.
        assertEquals(id("b1"), render.nodes.get(id("a3")).transitions.get(GraphDir.DOWN).destination);
    }

    @Test
    void arrowsNeverCrossStops() {
        GraphRender render = new GraphBuilder()
                .addItem(id("a"), vt("A"))
                .beginStop()
                .addItem(id("b"), vt("B"))
                .build();

        assertFalse(render.nodes.get(id("a")).transitions.containsKey(GraphDir.DOWN));
        assertFalse(render.nodes.get(id("b")).transitions.containsKey(GraphDir.UP));
        assertNotEquals(render.nodes.get(id("a")).stopKey, render.nodes.get(id("b")).stopKey);
    }

    @Test
    void contextBuildsNonFocusableParentChain() {
        GraphRender render = new GraphBuilder()
                .pushContext("Settings", "list")
                .addItem(id("a"), vt("A"))
                .pushContext("Advanced")
                .addItem(id("b"), vt("B"))
                .popContext()
                .addItem(id("c"), vt("C"))
                .build();

        GraphNode a = render.nodes.get(id("a"));
        GraphNode b2 = render.nodes.get(id("b"));
        GraphNode c2 = render.nodes.get(id("c"));
        assertNotNull(a.parent);
        assertFalse(a.parent.focusable);
        assertNull(a.parent.parent);
        assertSame(a.parent, b2.parent.parent);   // Advanced nests under Settings
        assertSame(a.parent, c2.parent);          // c popped back out to Settings
        assertFalse(render.nodes.containsKey(a.parent.id)); // contexts are never navigable
    }

    private static GraphRender buildGroups(Set<ControlId> expansion) {
        return new GraphBuilder(expansion)
                .beginGroup(id("combat"), vt("Combat"))
                .addItem(id("pause"), vt("Auto pause"))
                .beginGroup(id("nested"), vt("Nested"))
                .addItem(id("deep"), vt("Deep"))
                .endGroup()
                .endGroup()
                .addItem(id("after"), vt("After"))
                .build();
    }

    @Test
    void groupsEmitHeadersAndSuppressCollapsedSubtrees() {
        Set<ControlId> expansion = new HashSet<ControlId>();

        GraphRender collapsed = buildGroups(expansion);
        assertTrue(collapsed.nodes.containsKey(id("combat")));
        assertTrue(collapsed.nodes.get(id("combat")).expandable);
        assertFalse(collapsed.nodes.get(id("combat")).expanded);
        assertFalse(collapsed.nodes.containsKey(id("pause")));  // collapsed → children swallowed
        assertFalse(collapsed.nodes.containsKey(id("nested")));
        assertTrue(collapsed.nodes.containsKey(id("after")));

        expansion.add(id("combat"));
        GraphRender expanded = buildGroups(expansion);
        assertTrue(expanded.nodes.containsKey(id("pause")));
        assertSame(expanded.nodes.get(id("combat")), expanded.nodes.get(id("pause")).parent);
        assertTrue(expanded.nodes.containsKey(id("nested"))); // nested header visible…
        assertFalse(expanded.nodes.containsKey(id("deep")));  // …but its subtree still collapsed

        expansion.add(id("nested"));
        GraphRender deep = buildGroups(expansion);
        assertTrue(deep.nodes.containsKey(id("deep")));
        assertSame(deep.nodes.get(id("nested")), deep.nodes.get(id("deep")).parent);
    }

    @Test
    void positionsAutoStampBySiblingGroup() {
        Set<ControlId> expansion = new HashSet<ControlId>();
        expansion.add(id("g"));
        GraphRender render = new GraphBuilder(expansion)
                .addItem(id("a"), vt("A"))          // top level: a, g = 2 siblings
                .beginGroup(id("g"), vt("G"))
                .addItem(id("c1"), vt("C1"))        // group level: 3 siblings
                .addItem(id("c2"), vt("C2"))
                .addItem(id("c3"), vt("C3"))
                .endGroup()
                .beginStop()
                .addItem(id("lone"), vt("Lone"))    // single sibling → no position
                .beginStop()
                .startRow().addItem(id("r1"), vt("R1")).addItem(id("r2"), vt("R2")).endRow() // row members
                .build();

        assertEquals(1, render.nodes.get(id("a")).positionIndex);
        assertEquals(2, render.nodes.get(id("a")).positionCount);
        assertEquals(2, render.nodes.get(id("g")).positionIndex);
        assertEquals(2, render.nodes.get(id("c2")).positionIndex);
        assertEquals(3, render.nodes.get(id("c2")).positionCount);
        assertEquals(0, render.nodes.get(id("lone")).positionCount);
        assertEquals(1, render.nodes.get(id("r1")).positionIndex);
        assertEquals(2, render.nodes.get(id("r2")).positionIndex);
        assertEquals(2, render.nodes.get(id("r2")).positionCount);
    }

    @Test
    void regionsAreStamped() {
        GraphRender render = new GraphBuilder()
                .setRegion("filters").addItem(id("a"), vt("A"))
                .setRegion("items").addItem(id("b"), vt("B"))
                .build();

        assertEquals("filters", render.nodes.get(id("a")).regionKey);
        assertEquals("items", render.nodes.get(id("b")).regionKey);
    }

    @Test
    void mixedModesKeepDeclarationOrder() {
        // A screen declaring list → raw grid → button must keep that Tab-stop
        // order (raw nodes must NOT be appended after all menu rows).
        GraphRender render = new GraphBuilder()
                .addItem(id("list1"), vt("L1"))
                .beginStop()
                .addNode(id("cell1"), vt("C1"))
                .beginStop()
                .addItem(id("button"), vt("B"))
                .build();

        assertEquals(id("list1"), render.order.get(0).id);
        assertEquals(id("cell1"), render.order.get(1).id);
        assertEquals(id("button"), render.order.get(2).id);
    }

    @Test
    void mixedStopStitchesMenuToRawVertically() {
        // A stop with menu controls above raw sheet content (search/sort/
        // filters over an item table) must be arrow-traversable across the
        // mode boundary.
        GraphRender render = new GraphBuilder()
                .addItem(id("search"), vt("Search"))
                .startRow().addItem(id("f1"), vt("F1")).addItem(id("f2"), vt("F2")).endRow()
                .addNode(id("r0"), vt("Row0"))
                .addNode(id("r1"), vt("Row1"))
                .connect(id("r0"), GraphDir.DOWN, id("r1"))
                .connect(id("r1"), GraphDir.UP, id("r0"))
                .build();

        // Filter cells drop into the sheet's first row; the sheet's top links back up.
        assertEquals(id("r0"), render.nodes.get(id("f1")).transitions.get(GraphDir.DOWN).destination);
        assertEquals(id("r0"), render.nodes.get(id("f2")).transitions.get(GraphDir.DOWN).destination);
        assertEquals(id("f1"), render.nodes.get(id("r0")).transitions.get(GraphDir.UP).destination);
        // The sheet's own wiring is untouched.
        assertEquals(id("r1"), render.nodes.get(id("r0")).transitions.get(GraphDir.DOWN).destination);
    }

    @Test
    void rawModeWiresExplicitEdges() {
        GraphRender render = new GraphBuilder()
                .addNode(id("a"), vt("A"))
                .addNode(id("b"), vt("B"))
                .connect(id("a"), GraphDir.RIGHT, id("b"), "crossing the aisle")
                .connect(id("a"), GraphDir.DOWN, id("ghost")) // undeclared → dropped
                .setStart(id("b"))
                .build();

        assertEquals(id("b"), render.startKey);
        assertEquals("crossing the aisle", render.nodes.get(id("a")).transitions.get(GraphDir.RIGHT).label);
        assertFalse(render.nodes.get(id("a")).transitions.containsKey(GraphDir.DOWN));
    }

    @Test
    void guardsRejectMisuse() {
        assertNull(new GraphBuilder().build()); // empty = closed

        final GraphBuilder dup = new GraphBuilder().addItem(id("a"), vt("A"));
        assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                dup.addItem(id("a"), vt("A2"));
            }
        });

        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                new GraphBuilder().addItem(id("x"), new NodeVtable());
            }
        });
    }

    @Test
    void menuRowsAndRawNodesMix() {
        // A screen mixing an auto-wired list with a computed-topology grid:
        // raw edges may reference menu nodes.
        GraphRender render = new GraphBuilder()
                .addItem(id("list1"), vt("List1"))
                .addNode(id("cell1"), vt("Cell1"))
                .addNode(id("cell2"), vt("Cell2"))
                .connect(id("cell1"), GraphDir.RIGHT, id("cell2"))
                .connect(id("cell1"), GraphDir.UP, id("list1"))
                .build();

        assertEquals(3, render.order.size());
        assertEquals(id("cell2"), render.nodes.get(id("cell1")).transitions.get(GraphDir.RIGHT).destination);
        assertEquals(id("list1"), render.nodes.get(id("cell1")).transitions.get(GraphDir.UP).destination);
    }

    @Test
    void compositeKeysAreValueEqual() {
        Object hero = new Object();
        assertEquals(CompositeKey.of("inv", hero, 2), CompositeKey.of("inv", hero, 2));
        assertNotEquals(CompositeKey.of("inv", hero, 2), CompositeKey.of("inv", hero, 3));
        assertEquals(ControlId.structural(CompositeKey.of("a", 1)),
                ControlId.structural(CompositeKey.of("a", 1)));
    }
}
