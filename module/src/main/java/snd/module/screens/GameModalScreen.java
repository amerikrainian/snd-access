package snd.module.screens;

import java.util.Arrays;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.tann.dice.screens.dungeon.panels.book.Book;

import snd.core.loc.Loc;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.nav.AccessScreen;
import snd.module.GameUi;

/**
 * A generic reader for the game's pushed modals — choice dialogs, the cog
 * menu, unlock-requirement panels, the party-layout picker, mode info. Covers
 * whatever the game pushes onto its modal stack by walking the top modal's
 * actor tree ({@link ActorNodes}). Activation fires the game's own listener,
 * and Escape falls through to the game's own modal-pop handling. Screens with
 * dedicated readers (the Book, for now a placeholder; the party management
 * panel) are special-cased above this generic floor.
 */
public class GameModalScreen extends AccessScreen {
    @Override
    public String key() {
        return "game-modal";
    }

    @Override
    public int layer() {
        return 20; // covers whichever base screen the modal floats over
    }

    @Override
    public boolean isActive() {
        Actor modal = GameUi.topModal();
        // The party management panel has its own screen (InventoryScreen).
        return modal != null && !(modal instanceof com.tann.dice.screens.generalPanels.PartyManagementPanel);
    }

    @Override
    public void build(GraphBuilder b) {
        Actor modal = GameUi.topModal();
        if (modal == null) {
            return;
        }
        if (modal instanceof Book) {
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.TEXT;
            vt.announcements = Arrays.asList(NodeAnnouncement.of(Loc.get("ui", "modal.book_stub")));
            b.addItem(ActorNodes.actorId(modal), vt);
            return;
        }
        b.pushContext(Loc.get("ui", "modal.dialog"));
        ActorNodes.emit(b, modal);
        b.popContext();
    }
}
