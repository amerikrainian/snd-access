package snd.core.graph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ported from wotr-access KeyGraphTests — the engine spec. */
class KeyGraphTest {
    private static NodeVtable vt(String label) {
        NodeVtable v = new NodeVtable();
        v.announcements = Arrays.asList(NodeAnnouncement.of(label));
        return v;
    }

    private static ControlId id(String key) {
        return ControlId.structural(key);
    }

    private static KeyGraph menu(GraphState state, final String... items) {
        return new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                GraphBuilder b = new GraphBuilder();
                for (String i : items) {
                    b.addItem(id(i), vt(i));
                }
                return b.build();
            }
        }, state);
    }

    @Test
    void firstRenderLandsOnStart() {
        GraphState state = new GraphState();
        KeyGraph g = menu(state, "a", "b");
        assertTrue(g.rerender());
        assertEquals(id("a"), state.curKey);
    }

    @Test
    void moveStepsAndStopsAtEdges() {
        GraphState state = new GraphState();
        KeyGraph g = menu(state, "a", "b");

        KeyGraph.MoveResult r = g.move(GraphDir.DOWN);
        assertTrue(r.moved);
        assertEquals(id("b"), r.to.id);

        r = g.move(GraphDir.DOWN); // at the end
        assertFalse(r.moved);
        assertEquals(id("b"), r.to.id);
        assertSame(r.from, r.to);
    }

    @Test
    void moveToEdgeGoesAllTheWay() {
        GraphState state = new GraphState();
        KeyGraph g = menu(state, "a", "b", "c", "d");
        KeyGraph.MoveResult r = g.moveToEdge(GraphDir.DOWN);
        assertTrue(r.moved);
        assertEquals(id("d"), r.to.id);
    }

    @Test
    void transitionLabelIsReported() {
        GraphState state = new GraphState();
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder()
                        .addNode(id("a"), vt("A")).addNode(id("b"), vt("B"))
                        .connect(id("a"), GraphDir.RIGHT, id("b"), "lane change")
                        .build();
            }
        }, state);

        KeyGraph.MoveResult r = g.move(GraphDir.RIGHT);
        assertTrue(r.moved);
        assertEquals("lane change", r.transitionLabel);
    }

    @Test
    void reconcileTier2FollowsStructuralKeyAcrossRebuilds() {
        GraphState state = new GraphState();
        final int[] generation = {0};
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                generation[0]++; // fresh vtables/nodes each render
                return new GraphBuilder()
                        .addItem(id("a"), vt("A" + generation[0]))
                        .addItem(id("b"), vt("B" + generation[0]))
                        .build();
            }
        }, state);

        g.move(GraphDir.DOWN);
        assertEquals(id("b"), state.curKey);
        assertTrue(g.rerender()); // a whole new render
        assertEquals(id("b"), state.curKey);
    }

    @Test
    void reconcileTier1FollowsAMovedObject() {
        GraphState state = new GraphState();
        final Object thing = new Object(); // the backing domain object
        final int[] slot = {1};            // its structural position, which will change

        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder()
                        .addItem(ControlId.structural("header"), vt("Header"))
                        .addItem(ControlId.referenced(thing, "slot" + slot[0]), vt("Thing"))
                        .build();
            }
        }, state);

        g.move(GraphDir.DOWN); // focus the thing (at slot1)
        assertEquals("slot1", state.curKey.structuralKey);

        slot[0] = 2; // the object moves to a different slot
        assertTrue(g.rerender());
        assertEquals("slot2", state.curKey.structuralKey); // followed the reference
    }

    @Test
    void reconcileFallsBackToNearestSurvivor() {
        GraphState state = new GraphState();
        final List<String> items = new ArrayList<String>(Arrays.asList("a", "b", "c", "d"));
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                GraphBuilder b = new GraphBuilder();
                for (String i : items) {
                    b.addItem(id(i), vt(i));
                }
                return b.build();
            }
        }, state);

        g.moveToEdge(GraphDir.DOWN); // on "d"
        items.remove("d");
        items.remove("c");
        assertTrue(g.rerender());
        assertEquals(id("b"), state.curKey); // nearest earlier survivor
    }

    @Test
    void suggestedMoveIsHonoredAndConsumed() {
        GraphState state = new GraphState();
        KeyGraph g = menu(state, "a", "b", "c");
        state.nextSuggestedMove = id("c");
        assertTrue(g.rerender());
        assertEquals(id("c"), state.curKey);
        assertNull(state.nextSuggestedMove);
    }

    @Test
    void computeOrderCoversAllStops() {
        GraphState state = new GraphState();
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder()
                        .addItem(id("a"), vt("A"))
                        .beginStop().addItem(id("b"), vt("B"))
                        .beginStop().addItem(id("c"), vt("C"))
                        .build();
            }
        }, state);

        assertTrue(g.rerender());
        assertEquals(3, state.keyOrder.size()); // later stops appended
    }

    @Test
    void stopCyclingRemembersPositionPerStop() {
        GraphState state = new GraphState();
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder()
                        .addItem(id("a1"), vt("A1")).addItem(id("a2"), vt("A2"))
                        .beginStop()
                        .addItem(id("b1"), vt("B1")).addItem(id("b2"), vt("B2"))
                        .build();
            }
        }, state);

        g.move(GraphDir.DOWN); // a2 (remembered for stop 1)
        KeyGraph.MoveResult r = g.moveStop(+1, false);
        assertTrue(r.moved);
        assertEquals(id("b1"), r.to.id);
        g.move(GraphDir.DOWN); // b2

        r = g.moveStop(-1, false);
        assertEquals(id("a2"), r.to.id); // remembered, not the stop's first node

        r = g.moveStop(-1, false); // at the first stop, no wrap
        assertFalse(r.moved);

        r = g.moveStop(-1, true); // wraps to the last stop's memory
        assertTrue(r.moved);
        assertEquals(id("b2"), r.to.id);
    }

    @Test
    void regionJumpsWithinStop() {
        GraphState state = new GraphState();
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder()
                        .setRegion("filters").addItem(id("f1"), vt("F1"))
                        .setRegion("items").addItem(id("i1"), vt("I1")).addItem(id("i2"), vt("I2"))
                        .setRegion("footer").addItem(id("z1"), vt("Z1"))
                        .build();
            }
        }, state);

        KeyGraph.MoveResult r = g.moveRegion(+1);
        assertEquals(id("i1"), r.to.id);
        r = g.moveRegion(+1);
        assertEquals(id("z1"), r.to.id);
        r = g.moveRegion(+1); // at the last region
        assertFalse(r.moved);
        r = g.moveRegion(-1);
        assertEquals(id("i1"), r.to.id);
    }

    @Test
    void containersAreRegionsWhereTheScreenTagsNone() {
        GraphState state = new GraphState();
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder()
                        .addItem(id("top"), vt("Top"))
                        .pushContext("Gameplay", "group").addItem(id("g1"), vt("G1")).addItem(id("g2"), vt("G2"))
                        .popContext()
                        .pushContext("UI", "group").addItem(id("u1"), vt("U1")).popContext()
                        .build();
            }
        }, state);

        assertTrue(g.hasRegions());
        KeyGraph.MoveResult r = g.moveRegion(+1);
        assertEquals(id("g1"), r.to.id);
        r = g.moveRegion(+1);
        assertEquals(id("u1"), r.to.id);
        r = g.moveRegion(-1);
        assertEquals(id("g1"), r.to.id);
    }

    @Test
    void oneContainerOrATreeIsNoRegionsToJump() {
        GraphState state = new GraphState();
        KeyGraph single = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder()
                        .pushContext("Heroes").addItem(id("a"), vt("A")).addItem(id("b"), vt("B")).popContext()
                        .build();
            }
        }, state);
        assertFalse(single.hasRegions());

        Set<ControlId> expanded = new java.util.HashSet<ControlId>(Arrays.asList(id("h")));
        KeyGraph tree = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder(expanded)
                        .pushContext("Settings")
                        .beginGroup(id("h"), vt("Header")).addItem(id("c"), vt("Child")).endGroup()
                        .addItem(id("after"), vt("After"))
                        .popContext()
                        .build();
            }
        }, new GraphState());
        assertFalse(tree.hasRegions());
    }

    @Test
    void behaviorInvokersReportAbsence() {
        GraphState state = new GraphState();
        final boolean[] clicked = {false};
        final boolean[] adjusted = {false};
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                NodeVtable v = new NodeVtable();
                v.announcements = Arrays.asList(NodeAnnouncement.of("A"));
                v.onActivate = new Runnable() {
                    @Override
                    public void run() {
                        clicked[0] = true;
                    }
                };
                v.onAdjust = new NodeVtable.Adjust() {
                    @Override
                    public void adjust(int sign, boolean large) {
                        adjusted[0] = sign > 0;
                    }
                };
                return new GraphBuilder().addItem(id("a"), v).build();
            }
        }, state);

        assertTrue(g.activate());
        assertTrue(clicked[0]);
        assertTrue(g.tryAdjust(+1, false));
        assertTrue(adjusted[0]);
        assertFalse(g.secondary());
    }

    private static NodeVtable radio(String label, final boolean selected) {
        NodeVtable v = new NodeVtable();
        v.announcements = Arrays.asList(
                NodeAnnouncement.of(label),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return selected ? "selected" : null;
                    }
                }, AnnouncementKinds.SELECTED));
        return v;
    }

    @Test
    void initialFocusLandsOnSelectedMember() {
        GraphState state = new GraphState();
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder()
                        .addItem(id("a"), radio("A", false))
                        .addItem(id("b"), radio("B", false))
                        .addItem(id("c"), radio("C", true))   // the checked radio, deep in the list
                        .addItem(id("d"), radio("D", false))
                        .build();
            }
        }, state);

        assertTrue(g.rerender());
        assertEquals(id("c"), state.curKey); // not the first node
    }

    @Test
    void tabIntoStopLandsOnSelectedMemberWhenNoMemory() {
        GraphState state = new GraphState();
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder()
                        .addItem(id("a1"), vt("A1"))
                        .beginStop()
                        .addItem(id("b1"), radio("B1", false))
                        .addItem(id("b2"), radio("B2", true)) // selected in the second stop
                        .build();
            }
        }, state);

        KeyGraph.MoveResult r = g.moveStop(+1, false);
        assertTrue(r.moved);
        assertEquals(id("b2"), r.to.id); // landed on the checked one, not b1

        // But remembered position wins on return.
        g.move(GraphDir.UP); // b1
        g.moveStop(-1, false); // to stop 1
        r = g.moveStop(+1, false);
        assertEquals(id("b1"), r.to.id); // memory beats selection
    }

    @Test
    void rawBlockBetweenMenuRowsStaysReachable() {
        // Menu row, raw block (its own internal wiring), menu row — all one
        // stop. Menu wiring must BREAK at the raw block, and the stitcher
        // wires the seams; otherwise the block becomes an unreachable island.
        GraphState state = new GraphState();
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder()
                        .addItem(id("above"), vt("Above"))
                        .addNode(id("raw1"), vt("Raw 1"))
                        .addNode(id("raw2"), vt("Raw 2"))
                        .connect(id("raw1"), GraphDir.DOWN, id("raw2"))
                        .connect(id("raw2"), GraphDir.UP, id("raw1"))
                        .addItem(id("below"), vt("Below"))
                        .build();
            }
        }, state);

        assertTrue(g.rerender());
        assertEquals(id("above"), state.curKey);
        g.move(GraphDir.DOWN);
        assertEquals(id("raw1"), state.curKey);   // into the block, not over it
        g.move(GraphDir.DOWN);
        assertEquals(id("raw2"), state.curKey);
        g.move(GraphDir.DOWN);
        assertEquals(id("below"), state.curKey);  // out the bottom
        g.move(GraphDir.UP);
        assertEquals(id("raw2"), state.curKey);   // and back in
    }

    @Test
    void treeOpsExpandCollapseDescendAscend() {
        final GraphState state = new GraphState();
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder(state.expanded)
                        .beginGroup(id("combat"), vt("Combat"))
                        .addItem(id("pause"), vt("Auto pause"))
                        .addItem(id("delay"), vt("Delay"))
                        .endGroup()
                        .build();
            }
        }, state);

        assertTrue(g.rerender()); // focus lands on the collapsed header
        assertEquals(id("combat"), state.curKey);
        assertTrue(KeyGraph.inTree(g.currentNode()));

        KeyGraph.TreeResult r = g.treeRight(); // collapsed → expand
        assertEquals(KeyGraph.TreeMove.EXPANDED, r.kind);
        assertEquals(id("combat"), state.curKey);      // focus stays on the header
        assertTrue(g.currentNode().expanded);
        assertTrue(g.current().nodes.containsKey(id("pause")));

        r = g.treeRight(); // expanded → descend to first child
        assertEquals(KeyGraph.TreeMove.DESCENDED, r.kind);
        assertEquals(id("pause"), state.curKey);

        r = g.treeRight(); // a leaf inside the tree — consume
        assertEquals(KeyGraph.TreeMove.LEAF, r.kind);

        r = g.treeLeft(); // child → ascend to the header
        assertEquals(KeyGraph.TreeMove.ASCENDED, r.kind);
        assertEquals(id("combat"), state.curKey);

        r = g.treeLeft(); // expanded header → collapse
        assertEquals(KeyGraph.TreeMove.COLLAPSED, r.kind);
        assertEquals(id("combat"), state.curKey);
        assertFalse(g.current().nodes.containsKey(id("pause")));
    }

    @Test
    void emptyGroupRecollapsesOnExpand() {
        final GraphState state = new GraphState();
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder(state.expanded)
                        .beginGroup(id("dud"), vt("Dud")) // no children declared
                        .endGroup()
                        .build();
            }
        }, state);

        assertTrue(g.rerender());
        KeyGraph.TreeResult r = g.treeRight();
        assertEquals(KeyGraph.TreeMove.EMPTY_GROUP, r.kind);
        assertFalse(g.currentNode().expanded); // auto-recollapsed
        assertFalse(state.expanded.contains(id("dud")));
    }

    @Test
    void collapseWhileInsideLandsOnNearestSurvivor() {
        final GraphState state = new GraphState();
        state.expanded.add(id("combat"));
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder(state.expanded)
                        .beginGroup(id("combat"), vt("Combat"))
                        .addItem(id("pause"), vt("Auto pause"))
                        .endGroup()
                        .build();
            }
        }, state);

        g.rerender();
        g.focus(id("pause"));
        state.expanded.remove(id("combat")); // collapsed externally while focus was inside
        assertTrue(g.rerender());
        assertEquals(id("combat"), state.curKey); // nearest survivor = the header
    }

    @Test
    void siblingEdgeJumpStaysAtDepth() {
        final GraphState state = new GraphState();
        state.expanded.add(id("g"));
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder(state.expanded)
                        .addItem(id("top"), vt("Top"))
                        .beginGroup(id("g"), vt("Group"))
                        .addItem(id("c1"), vt("C1"))
                        .addItem(id("c2"), vt("C2"))
                        .addItem(id("c3"), vt("C3"))
                        .endGroup()
                        .build();
            }
        }, state);

        g.rerender();
        g.focus(id("c2"));
        KeyGraph.MoveResult r = g.moveToSiblingEdge(false);
        assertTrue(r.moved);
        assertEquals(id("c3"), state.curKey); // last SIBLING, not the last row overall

        r = g.moveToSiblingEdge(true);
        assertEquals(id("c1"), state.curKey);
    }

    @Test
    void focusAndFocusByReferenceWork() {
        GraphState state = new GraphState();
        final Object backing = new Object();
        KeyGraph g = new KeyGraph(new Supplier<GraphRender>() {
            @Override
            public GraphRender get() {
                return new GraphBuilder()
                        .addItem(id("a"), vt("A"))
                        .addItem(ControlId.referenced(backing, "b"), vt("B"))
                        .build();
            }
        }, state);

        assertTrue(g.rerender());
        assertTrue(g.focusByReference(backing));
        assertEquals("b", state.curKey.structuralKey);
        assertFalse(g.focusByReference(backing)); // already there — not a change

        assertTrue(g.focus(id("a")));
        assertEquals(id("a"), state.curKey);
        assertFalse(g.focus(id("nope")));
    }
}
