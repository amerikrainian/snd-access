package snd.module.screens;

import java.util.List;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.tann.dice.screens.dungeon.panels.book.Book;

import snd.core.loc.Loc;
import snd.core.graph.GraphBuilder;
import snd.core.nav.AccessScreen;
import snd.core.nav.KeyOffer;
import snd.module.GameKeys;
import snd.module.GameUi;

/**
 * A generic reader for the game's pushed modals — choice dialogs, the cog
 * menu, unlock-requirement panels, the party-layout picker, mode info. Covers
 * whatever the game pushes onto its modal stack by walking the top modal's
 * actor tree ({@link ActorNodes}). Activation fires the game's own listener.
 * Escape closes this panel alone when it sits over another one, and otherwise
 * falls through to the game's own modal-pop handling. Screens with dedicated
 * readers (the Book, the party management panel) opt out of this generic
 * floor.
 */
public class GameModalScreen extends AccessScreen {
    @Override
    public String key() {
        return "game-modal";
    }

    @Override
    public List<KeyOffer> keys() {
        return GameKeys.escapeOnly();
    }

    @Override
    public boolean onCancel() {
        return GameUi.popTopModalOnly();
    }

    @Override
    public int layer() {
        return 20; // covers whichever base screen the modal floats over
    }

    @Override
    public boolean isActive() {
        Actor modal = GameUi.topModal();
        // The party management panel and the Book have their own screens
        // (InventoryScreen, BookScreen).
        return modal != null
                && !(modal instanceof com.tann.dice.screens.generalPanels.PartyManagementPanel)
                && !(modal instanceof Book);
    }

    @Override
    public void build(GraphBuilder b) {
        Actor modal = GameUi.topModal();
        if (modal == null) {
            return;
        }
        b.pushContext(Loc.get("ui", isCogMenu(modal) ? "modal.settings" : "modal.dialog"));
        if (modal instanceof com.tann.dice.screens.dungeon.panels.entPanel.choosablePanel.ConcisePanel) {
            // A choosable's big panel reads from the model, not the actors.
            ChoosablePanelNodes.emit(b,
                    (com.tann.dice.screens.dungeon.panels.entPanel.choosablePanel.ConcisePanel) modal);
        } else {
            ActorNodes.emit(b, modal, glyphs(modal));
        }
        b.popContext();
    }

    // The cog menu's screen modes (DesktopControl.SCREEN_MODE's values, which
    // its own description spells out), its UI-size steppers and the jukebox
    // transport it carries.
    private static final java.util.Map<String, String> COG_GLYPHS = new java.util.HashMap<String, String>();
    // The surrender dialog's purple "?" between its no and yes (SurrenderPhase):
    // the explanation. Punctuation alone is silent in speech.
    private static final java.util.Map<String, String> SURRENDER_GLYPHS =
            java.util.Collections.singletonMap("?", "glyph.help");
    static {
        COG_GLYPHS.put("w", "glyph.windowed");
        COG_GLYPHS.put("fs", "glyph.fullscreen");
        COG_GLYPHS.put("fs2", "glyph.fullscreen2");
        COG_GLYPHS.put("-", "glyph.decrease");
        COG_GLYPHS.put("+", "glyph.increase");
        COG_GLYPHS.putAll(BookScreen.JUKEBOX_GLYPHS);
    }

    private static java.util.Map<String, String> glyphs(Actor modal) {
        if (isCogMenu(modal)) {
            return COG_GLYPHS;
        }
        if (com.tann.dice.gameplay.phase.PhaseManager.get().getPhase()
                instanceof com.tann.dice.gameplay.phase.gameplay.SurrenderPhase) {
            return SURRENDER_GLYPHS;
        }
        return ActorNodes.NO_GLYPHS;
    }

    // The cog/settings menu marks itself with a CogTag child — the same tag
    // the game's own EscMenuUtils.refreshIfOnTop checks for.
    private static boolean isCogMenu(Actor modal) {
        return modal instanceof com.badlogic.gdx.scenes.scene2d.Group
                && com.tann.dice.util.Tann.findByClass((com.badlogic.gdx.scenes.scene2d.Group) modal,
                        com.tann.dice.screens.dungeon.DungeonUtils.CogTag.class) != null;
    }
}
