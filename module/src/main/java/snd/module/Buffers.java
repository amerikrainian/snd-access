package snd.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import snd.core.buffers.Buffer;
import snd.core.buffers.BufferControls;
import snd.core.buffers.BufferManager;
import snd.core.buffers.NodeLines;
import snd.core.graph.ControlId;
import snd.core.loc.Loc;
import snd.core.nav.GraphNavigator;
import snd.core.speech.SpeechPipeline;

/**
 * The mod's buffer roster and its review keys (Ctrl plus arrows): review
 * lists for the information a focus announcement leaves out, stepped line by
 * line instead of heard in one burst. Every buffer reads live on each
 * keypress; a focus change re-homes review to the control's own buffer and
 * rewinds the focus-fed ones, since a new control is new content. An empty
 * buffer is skipped by the review keys.
 */
final class Buffers {
    static final String CONTROL = "control";

    private final GraphNavigator nav;
    private final BufferManager manager = new BufferManager();
    private final List<Buffer> focusFed = new ArrayList<Buffer>();
    private ControlId homed;
    final BufferControls controls;

    Buffers(final GraphNavigator nav, SpeechPipeline speech) {
        this.nav = nav;
        this.controls = new BufferControls(manager, speech);
        // The focused control's own lines: its head, then one per tooltip.
        focusFed(CONTROL, new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return NodeLines.lines(nav.focusedNode());
            }
        });
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
