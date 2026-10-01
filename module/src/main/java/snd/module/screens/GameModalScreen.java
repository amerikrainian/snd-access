package snd.module.screens;

import java.util.List;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.tann.dice.screens.dungeon.panels.book.Book;

import snd.core.loc.Loc;
import snd.core.graph.GraphBuilder;
import snd.core.nav.AccessScreen;
import snd.core.nav.KeyOffer;
import snd.module.GameKeys;
import snd.module.Captured;
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
 *
 * <p>One instance reads each level of the modal stack, so a panel covered by
 * another one (a hero's details over the class picker) is a covered screen
 * that keeps its place: closing the cover lands back on the control that
 * opened it. The deepest instance reads whatever lies past it.
 */
public class GameModalScreen extends AccessScreen {
    public static final int LEVELS = 4;

    private final int level;

    /** Register in ascending level: the same layer, so the later (higher) one wins. */
    public GameModalScreen(int level) {
        this.level = level;
    }

    @Override
    public String key() {
        return "game-modal-" + level;
    }

    // This level's modal, or null when the stack is shallower or a modal
    // with a screen of its own sits at or over it (that screen reads it).
    private Actor modal() {
        List<Actor> modals = GameUi.modals();
        if (modals.size() <= level) {
            return null;
        }
        for (Actor above : modals.subList(level, modals.size())) {
            if (above instanceof com.tann.dice.screens.generalPanels.PartyManagementPanel
                    || above instanceof Book) {
                return null;
            }
        }
        return modals.get(level == LEVELS - 1 ? modals.size() - 1 : level);
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
        return modal() != null;
    }

    @Override
    public void build(GraphBuilder b) {
        Actor modal = modal();
        if (modal == null) {
            return;
        }
        b.pushContext(Loc.get("ui", isCogMenu(modal) ? "modal.settings" : "modal.dialog"));
        if (modal instanceof com.tann.dice.screens.dungeon.panels.entPanel.choosablePanel.ConcisePanel) {
            // A choosable's big panel reads from the model, not the actors.
            ChoosablePanelNodes.emit(b,
                    (com.tann.dice.screens.dungeon.panels.entPanel.choosablePanel.ConcisePanel) modal);
        } else if (modal instanceof com.tann.dice.screens.dungeon.panels.Explanel.EntPanelInventory) {
            unitPanel(b, (com.tann.dice.screens.dungeon.panels.Explanel.EntPanelInventory) modal);
        } else if (!LedgerNodes.emit(b, modal,
                com.tann.dice.screens.dungeon.panels.book.page.ledgerPage.LedgerPage.LedgerPageType.Hero, null)) {
            // Hero tiles (the Choose-Party class picker, a past run's party)
            // are grouped by colour as in the almanac; anything else is walked.
            ActorNodes.emit(b, modal, place(modal));
        }
        b.popContext();
    }

    // The cog menu's UI-size steppers and the jukebox transport it carries.
    private static final java.util.Map<String, String> COG_GLYPHS = new java.util.HashMap<String, String>();
    // The surrender dialog's purple "?" between its no and yes (SurrenderPhase):
    // the explanation. Punctuation alone is silent in speech.
    private static final java.util.Map<String, String> SURRENDER_GLYPHS =
            java.util.Collections.singletonMap("?", "glyph.help");
    static {
        COG_GLYPHS.put("-", "glyph.decrease");
        COG_GLYPHS.put("+", "glyph.increase");
        COG_GLYPHS.putAll(BookScreen.JUKEBOX_GLYPHS);
    }

    private static ActorNodes.Place place(Actor modal) {
        if (isCogMenu(modal)) {
            // Screen mode, display, sound and the menu's buttons: Tab moves between them.
            return ActorNodes.Place.glyphs(COG_GLYPHS).withSectionStops();
        }
        // The almanac's leaderboard picker, under the name the game pops it by
        // (StuffPage.makeLeaderboard): the chosen board is the one the page
        // under it displays, and each button's runnable holds its board.
        if ("leaderboard_modal".equals(modal.getName())) {
            // Tested only when a button is spoken, never per frame.
            return ActorNodes.Place.chosenBy(new java.util.function.Predicate<Actor>() {
                @Override
                public boolean test(Actor actor) {
                    if (!(actor instanceof com.tann.dice.util.ui.standardButton.StandardButton)) {
                        return false;
                    }
                    com.tann.dice.gameplay.leaderboard.Leaderboard shown = shownLeaderboard();
                    return shown != null && Captured.value(
                            Captured.runnable((com.tann.dice.util.ui.standardButton.StandardButton) actor),
                            com.tann.dice.gameplay.leaderboard.Leaderboard.class) == shown;
                }
            });
        }
        if (com.tann.dice.gameplay.phase.PhaseManager.get().getPhase()
                instanceof com.tann.dice.gameplay.phase.gameplay.SurrenderPhase) {
            return ActorNodes.Place.glyphs(SURRENDER_GLYPHS);
        }
        return ActorNodes.Place.PLAIN;
    }

    // A unit's panel. Opened from the almanac's hero page, it carries what the
    // page puts on it (LedgerUtils.makeHeroGroup): the chosen record above a
    // hero past tier one, and its marks in the run below. The page works
    // these out once, as it builds the panel; so are they here, per panel.
    private java.lang.ref.WeakReference<Actor> notedPanel = new java.lang.ref.WeakReference<Actor>(null);
    private final java.util.List<String> notedAbove = new java.util.ArrayList<String>();
    private final java.util.List<String> notedBelow = new java.util.ArrayList<String>();

    private void unitPanel(GraphBuilder b, com.tann.dice.screens.dungeon.panels.Explanel.EntPanelInventory panel) {
        if (notedPanel.get() != panel) {
            notedPanel = new java.lang.ref.WeakReference<Actor>(panel);
            notedAbove.clear();
            notedBelow.clear();
            com.tann.dice.screens.dungeon.panels.book.page.BookPage page = almanacPage();
            if (page != null && panel.ent instanceof com.tann.dice.gameplay.content.ent.Hero
                    && BookScreen.focusedTabIdentifier(page)
                            == com.tann.dice.screens.dungeon.panels.book.page.ledgerPage.LedgerPage.LedgerPageType.Hero) {
                com.tann.dice.gameplay.content.ent.type.HeroType hero =
                        ((com.tann.dice.gameplay.content.ent.Hero) panel.ent).getHeroType();
                LedgerFacts facts = LedgerFacts.of(page);
                String chosen = facts != null ? facts.chosenLine(hero) : null;
                if (chosen != null) {
                    notedAbove.add(chosen);
                }
                notedBelow.addAll(LedgerFacts.runMarks(hero));
            }
        }
        EntPanelNodes.emit(b, panel, notedAbove, notedBelow);
    }

    // The almanac's focused page, when the almanac is open under the modal.
    private static com.tann.dice.screens.dungeon.panels.book.page.BookPage almanacPage() {
        for (Actor modal : GameUi.modals()) {
            if (modal instanceof Book) {
                return BookScreen.focusedPage((Book) modal);
            }
        }
        return null;
    }

    // The board the almanac's online page displays, under the picker.
    private static com.tann.dice.gameplay.leaderboard.Leaderboard shownLeaderboard() {
        for (Actor modal : GameUi.modals()) {
            if (modal instanceof Book) {
                com.tann.dice.gameplay.leaderboard.LeaderboardDisplay display = com.tann.dice.util.Tann.findByClass(
                        (com.badlogic.gdx.scenes.scene2d.Group) modal, com.tann.dice.gameplay.leaderboard.LeaderboardDisplay.class);
                return display != null ? LeaderboardNodes.boardOf(display) : null;
            }
        }
        return null;
    }

    // The cog/settings menu marks itself with a CogTag child — the same tag
    // the game's own EscMenuUtils.refreshIfOnTop checks for.
    private static boolean isCogMenu(Actor modal) {
        return modal instanceof com.badlogic.gdx.scenes.scene2d.Group
                && com.tann.dice.util.Tann.findByClass((com.badlogic.gdx.scenes.scene2d.Group) modal,
                        com.tann.dice.screens.dungeon.DungeonUtils.CogTag.class) != null;
    }
}
