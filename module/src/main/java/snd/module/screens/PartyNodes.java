package snd.module.screens;

import java.util.Arrays;
import java.util.function.Supplier;

import com.tann.dice.gameplay.phase.Phase;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.screens.dungeon.DungeonScreen;

import snd.contracts.HostServices;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.module.GameText;
import snd.module.GameUi;

/**
 * What the dungeon screen keeps on screen between fights, under whatever
 * offer, hub or dialog the phase draws over it: the hero column, each panel
 * opening the hero's sheet as a click does, and the corner Inventory button
 * while the phase slides it in ({@code DungeonScreen.enterPhase} →
 * {@code toggleHiddenInventory(showCornerInventory())}). Every screen over
 * the dungeon outside a fight ends its build with it.
 */
final class PartyNodes {
    private PartyNodes() {
    }

    static void build(GraphBuilder b, HostServices host) {
        if (!(com.tann.dice.Main.getCurrentScreen() instanceof DungeonScreen)) {
            return;
        }
        final DungeonScreen ds = DungeonScreen.get();
        CombatScreen.heroStop(b, host, ds);
        Phase phase = PhaseManager.get().getPhase();
        if (phase.showCornerInventory() && ds.getDungeonContext().allowInventory()) {
            NodeVtable inv = new NodeVtable();
            inv.controlType = ControlTypes.BUTTON;
            inv.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return GameText.t("Inventory");
                }
            }, AnnouncementKinds.LABEL));
            inv.onActivate = new Runnable() {
                @Override
                public void run() {
                    GameUi.activate(ds.hiddenInventoryButton);
                }
            };
            // Below the column, as drawn; the hero rows count no positions.
            inv.speaksOwnPosition = true;
            b.addItem(ControlId.structural(CompositeKey.of("party", "inventory")), inv);
        }
    }
}
