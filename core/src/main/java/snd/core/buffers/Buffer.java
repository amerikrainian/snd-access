package snd.core.buffers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import snd.contracts.SndLog;

/**
 * A named, ordered list of text lines the player reviews on demand
 * (Ctrl+arrows), independent of the auto-spoken focus announcement. Lines come
 * from a live source re-read on every buffer keypress, so content never goes
 * stale; the cursor survives a re-read while still in range. Detail is not
 * nested: one focused control explodes into several flat lines (its own line,
 * then one line per tooltip), and the player steps line by line.
 */
public final class Buffer {
    public final String key;
    private final Supplier<String> label;
    private final List<String> lines = new ArrayList<String>();
    private Supplier<? extends Iterable<String>> source;
    private int position;

    /** Switching to this buffer jumps to its last line (an event log). */
    public boolean followLatest;

    /** label: the buffer's spoken name, resolved at speak time. */
    public Buffer(String key, Supplier<String> label) {
        this.key = key;
        this.label = label;
    }

    public String label() {
        return label.get();
    }

    /** Bind the live source (null detaches, emptying it). A new source is new content: the cursor resets. */
    public Buffer source(Supplier<? extends Iterable<String>> newSource) {
        source = newSource;
        position = 0;
        return this;
    }

    /** Back to the first line: the source stayed, its subject changed (a new focus). */
    public void reset() {
        position = 0;
    }

    // Re-read the source, keeping the cursor while still in range. A source
    // that throws reads as empty, logged.
    private void refresh() {
        lines.clear();
        if (source != null) {
            Iterable<String> read = null;
            try {
                read = source.get();
            } catch (Throwable t) {
                SndLog.error("buffer " + key + ": source failed", t);
            }
            if (read != null) {
                for (String line : read) {
                    if (line != null && !line.trim().isEmpty()) {
                        lines.add(line);
                    }
                }
            }
        }
        if (position >= lines.size()) {
            position = 0;
        }
    }

    public boolean isEmpty() {
        refresh();
        return lines.isEmpty();
    }

    /** Every line the source reads now (a re-read; the cursor stays). */
    public List<String> lines() {
        refresh();
        return Collections.unmodifiableList(new ArrayList<String>(lines));
    }

    public int position() {
        return position;
    }

    public String currentLine() {
        refresh();
        return lines.isEmpty() ? null : lines.get(position);
    }

    public boolean moveNext() {
        refresh();
        if (position + 1 >= lines.size()) {
            return false;
        }
        position++;
        return true;
    }

    public boolean movePrevious() {
        refresh();
        if (position == 0) {
            return false;
        }
        position--;
        return true;
    }

    public void moveToEnd() {
        refresh();
        position = lines.isEmpty() ? 0 : lines.size() - 1;
    }
}
