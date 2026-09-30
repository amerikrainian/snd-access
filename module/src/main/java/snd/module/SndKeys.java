package snd.module;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import snd.contracts.speech.SpeechPipeline;
import snd.core.input.InputAction;
import snd.core.input.InputRegistry;
import snd.core.input.KeyChord;
import snd.core.nav.GraphNavigator;
import snd.core.nav.NavAction;
import snd.module.screens.CombatScreen;
import snd.module.screens.HelpScreen;
import snd.module.screens.UnitLines;

/**
 * The mod's key table: every key of our own, bound here and nowhere else.
 * {@link SndInput} resolves presses through it, the key help reads labels and
 * spoken chords from it, and the dev driver drives it. Registration order is
 * the key help's order within a group: what the focused control answers,
 * then movement.
 *
 * <p>Picking a key: no bare letters (type-ahead owns them), nothing the game
 * binds (a fall-through fires the game's action), so new keys take Ctrl or
 * Shift tiers of existing ones, or keys that type nothing — or a game key
 * only where the game leaves it unused, yielding it everywhere else. Shift+digit is the
 * game's (target the other side); the Ctrl tiers of the digits, and Ctrl+I,
 * are the glances'. Keycodes are the game's libGDX ({@code Input.Keys}).
 */
final class SndKeys {
    private SndKeys() {
    }

    static InputRegistry build(final HelpScreen help, final GraphNavigator nav, final Buffers buffers,
            final SpeechPipeline speech) {
        InputRegistry keys = new InputRegistry();

        keys.register(InputAction.of("help", "help.title")
                .bind(KeyChord.of(131, "key.f1"))
                .overOverlay().unlisted()
                .handle(new IntConsumer() {
                    @Override
                    public void accept(int digit) {
                        help.toggle();
                    }
                }));

        keys.register(InputAction.nav(NavAction.ACTIVATE, "help.nav.ACTIVATE")
                .bind(KeyChord.of(66, "key.enter")).alias(KeyChord.of(160, "key.enter")));
        keys.register(InputAction.nav(NavAction.SECONDARY, "help.nav.SECONDARY")
                .bind(KeyChord.of(67, "key.backspace")));
        keys.register(InputAction.nav(NavAction.UP, "help.nav.UP").bind(KeyChord.of(19, "key.up")));
        keys.register(InputAction.nav(NavAction.DOWN, "help.nav.DOWN").bind(KeyChord.of(20, "key.down")));
        keys.register(InputAction.nav(NavAction.LEFT, "help.nav.LEFT").bind(KeyChord.of(21, "key.left")));
        keys.register(InputAction.nav(NavAction.RIGHT, "help.nav.RIGHT").bind(KeyChord.of(22, "key.right")));
        keys.register(InputAction.nav(NavAction.NEXT_STOP, "help.nav.NEXT_STOP")
                .bind(KeyChord.of(61, "key.tab")));
        keys.register(InputAction.nav(NavAction.PREV_STOP, "help.nav.PREV_STOP")
                .bind(KeyChord.of(61, "key.tab").shift()));
        keys.register(InputAction.nav(NavAction.HOME, "help.nav.HOME").bind(KeyChord.of(3, "key.home")));
        keys.register(InputAction.nav(NavAction.END, "help.nav.END").bind(KeyChord.of(123, "key.end")));
        keys.register(InputAction.nav(NavAction.REGION_PREV, "help.nav.REGION_PREV")
                .bind(KeyChord.of(19, "key.up").alt()));
        keys.register(InputAction.nav(NavAction.REGION_NEXT, "help.nav.REGION_NEXT")
                .bind(KeyChord.of(20, "key.down").alt()));
        // Escape answers only a live search or a screen's onCancel, and
        // opening the help ends the search: never worth a row.
        keys.register(InputAction.nav(NavAction.CANCEL, "help.nav.CANCEL")
                .bind(KeyChord.of(111, "key.escape")).unlisted());

        keys.register(InputAction.of("glance.vitals", "help.glance.vitals")
                .bind(KeyChord.of(8, "key.1").ctrl()).alias(KeyChord.of(145, "key.1").ctrl())
                .when(new BooleanSupplier() {
                    @Override
                    public boolean getAsBoolean() {
                        return CombatScreen.focusedUnit(nav) != null;
                    }
                })
                .handle(new IntConsumer() {
                    @Override
                    public void accept(int digit) {
                        // In place, interrupting, like any answer to a key press.
                        speech.speak(CombatScreen.vitalsLine(CombatScreen.focusedUnit(nav)), true);
                    }
                }));
        keys.register(InputAction.of("glance.incoming", "help.glance.incoming")
                .bind(KeyChord.of(37, "key.i").ctrl())
                .when(new BooleanSupplier() {
                    @Override
                    public boolean getAsBoolean() {
                        return CombatScreen.inFight();
                    }
                })
                .handle(new IntConsumer() {
                    @Override
                    public void accept(int digit) {
                        speech.speak(CombatScreen.partyIncoming(), true);
                    }
                }));
        // A die's sides one at a time, in the sheet's reading order, on a
        // control that concerns a unit outside a fight: the inventory, a
        // sheet, the almanac. In a fight, and on the choice and level-end
        // options, the digits are the game's.
        keys.register(InputAction.of("glance.side", "help.glance.side")
                .bind(KeyChord.digits())
                .yieldsToGame()
                .when(new BooleanSupplier() {
                    @Override
                    public boolean getAsBoolean() {
                        return sideOwner(nav) != null;
                    }
                })
                .digitCount(new IntSupplier() {
                    @Override
                    public int getAsInt() {
                        Object owner = sideOwner(nav);
                        return owner != null ? UnitLines.sideCount(owner) : 6;
                    }
                })
                .handle(new IntConsumer() {
                    @Override
                    public void accept(int digit) {
                        String line = UnitLines.sideLine(sideOwner(nav), digit);
                        if (line != null) {
                            speech.speak(line, true);
                        }
                    }
                }));
        // Review: Ctrl+Left/Right switch buffers; Ctrl+Up steps through a
        // buffer, away from the line review lands on, Ctrl+Down back to it.
        // They only read, so they answer over the help overlay too.
        keys.register(InputAction.of("buffer.next", "help.buffer.next")
                .bind(KeyChord.of(22, "key.right").ctrl())
                .overOverlay()
                .handle(new IntConsumer() {
                    @Override
                    public void accept(int digit) {
                        buffers.controls.nextBuffer();
                    }
                }));
        keys.register(InputAction.of("buffer.prev", "help.buffer.prev")
                .bind(KeyChord.of(21, "key.left").ctrl())
                .overOverlay()
                .handle(new IntConsumer() {
                    @Override
                    public void accept(int digit) {
                        buffers.controls.previousBuffer();
                    }
                }));
        keys.register(InputAction.of("buffer.line.next", "help.buffer.line.next")
                .bind(KeyChord.of(19, "key.up").ctrl())
                .overOverlay()
                .handle(new IntConsumer() {
                    @Override
                    public void accept(int digit) {
                        buffers.controls.nextLine();
                    }
                }));
        keys.register(InputAction.of("buffer.line.prev", "help.buffer.line.prev")
                .bind(KeyChord.of(20, "key.down").ctrl())
                .overOverlay()
                .handle(new IntConsumer() {
                    @Override
                    public void accept(int digit) {
                        buffers.controls.previousLine();
                    }
                }));

        // The rest of the Ctrl tiers of the digits: held for the glances to
        // come, and silent until then. Fallen through, Ctrl+2 is the game's 2.
        keys.register(InputAction.of("reserved.ctrl-digits", null)
                .bind(KeyChord.digits().ctrl()).bind(KeyChord.digits().ctrl().shift())
                .unlisted()
                .handle(new IntConsumer() {
                    @Override
                    public void accept(int digit) {
                    }
                }));
        return keys;
    }

    // The die the focused control concerns, while the digits are not the
    // fight's: the almanac takes every key before the fight sees it.
    private static Object sideOwner(GraphNavigator nav) {
        if (CombatScreen.inFight() && !com.tann.dice.screens.dungeon.panels.book.Book.inBook()) {
            return null;
        }
        return UnitLines.dieOwner(nav.focusedSubject());
    }
}
