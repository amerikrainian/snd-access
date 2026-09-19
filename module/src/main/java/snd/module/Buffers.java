package snd.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.content.item.Item;
import com.tann.dice.screens.dungeon.DungeonScreen;

import snd.core.buffers.Buffer;
import snd.core.buffers.BufferControls;
import snd.core.buffers.BufferManager;
import snd.core.buffers.EventLog;
import snd.core.buffers.NodeLines;
import snd.core.graph.ControlId;
import snd.core.loc.Loc;
import snd.core.nav.GraphNavigator;
import snd.core.speech.SpeechPipeline;
import snd.module.screens.CombatScreen;
import snd.module.screens.UnitLines;

/**
 * The mod's buffer roster and its review keys (Ctrl plus arrows): review
 * lists for the information a focus announcement leaves out, stepped line by
 * line instead of heard in one burst. In cycling order: the focused control's
 * own lines; the hero or the monster it concerns, whole (its sheet, without
 * opening it); the items that hero carries, or the focused item; the party
 * and the enemies, one line per unit; the log of what happened (banners,
 * rolls, outcomes, phases, notifications), which follows its latest line.
 * Every buffer reads live on each
 * keypress; a focus change re-homes review to the control's own buffer and
 * rewinds the focus-fed ones, since a new control is new content. An empty
 * buffer is skipped by the review keys.
 */
final class Buffers {
    static final String CONTROL = "control";
    static final String HERO = "hero";
    static final String MONSTER = "monster";
    static final String ITEMS = "items";
    static final String PARTY = "party";
    static final String ENEMIES = "enemies";
    static final String LOG = "log";

    private final GraphNavigator nav;
    private final BufferManager manager = new BufferManager();
    private final List<Buffer> focusFed = new ArrayList<Buffer>();
    private ControlId homed;
    final BufferControls controls;

    Buffers(final GraphNavigator nav, final EventLog events, SpeechPipeline speech) {
        this.nav = nav;
        this.controls = new BufferControls(manager, speech);
        // The focused control's own lines: its head, then one per tooltip.
        focusFed(CONTROL, new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return NodeLines.lines(nav.focusedNode());
            }
        });
        // What the control concerns (its subject), wherever it was declared.
        focusFed(HERO, new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                Ent unit = CombatScreen.focusedUnit(nav);
                return unit != null && unit.isPlayer() ? UnitLines.of(unit) : null;
            }
        });
        focusFed(MONSTER, new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                Ent unit = CombatScreen.focusedUnit(nav);
                return unit != null && !unit.isPlayer() ? UnitLines.of(unit) : null;
            }
        });
        focusFed(ITEMS, new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                Object subject = nav.focusedSubject();
                if (subject instanceof Item) {
                    return UnitLines.item((Item) subject);
                }
                return subject instanceof Ent ? UnitLines.items((Ent) subject) : null;
            }
        });
        // The battlefield, whatever has the focus. Empty outside the dungeon.
        add(PARTY, new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return inDungeon() ? UnitLines.side(true) : null;
            }
        });
        add(ENEMIES, new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return inDungeon() ? UnitLines.side(false) : null;
            }
        });
        // What happened, oldest first; switching to it lands on the latest.
        add(LOG, new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return events.lines();
            }
        }).followLatest = true;
    }

    private static boolean inDungeon() {
        return com.tann.dice.Main.getCurrentScreen() instanceof DungeonScreen
                && DungeonScreen.get().getFightLog() != null;
    }

    private Buffer focusFed(String key, Supplier<List<String>> source) {
        Buffer buffer = add(key, source);
        focusFed.add(buffer);
        return buffer;
    }

    private Buffer add(final String key, Supplier<List<String>> source) {
        return manager.add(new Buffer(key, new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", "buffer." + key);
            }
        }).source(source));
    }

    /** Per frame: a focus change (by key or by the game moving things) re-homes review. */
    void tick() {
        ControlId id = nav.focusedId();
        if (id == null ? homed == null : id.equals(homed)) {
            return;
        }
        homed = id;
        for (Buffer buffer : focusFed) {
            buffer.reset();
        }
        manager.setCurrent(CONTROL);
    }

    /** The dev driver's view: every buffer with its lines, the cursor marked. */
    String devDump() {
        StringBuilder sb = new StringBuilder();
        Buffer current = manager.current();
        for (Buffer buffer : manager.buffers()) {
            List<String> lines = buffer.lines();
            sb.append(buffer == current ? "> " : "  ").append(buffer.key).append(" (").append(lines.size())
                    .append(")\n");
            for (int i = 0; i < lines.size(); i++) {
                sb.append(buffer == current && i == buffer.position() ? "    * " : "      ")
                        .append(lines.get(i)).append('\n');
            }
        }
        return sb.toString();
    }
}
