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
 *
 * <p>Review lands on the buffer's HOME line and walks away from it: Ctrl+Up is
 * {@link #moveNext} (one line further from home), Ctrl+Down
 * {@link #movePrevious} (one line back toward it). Home is the source's first
 * line — a control's own readout, with its tooltips further on — or, for a
 * buffer that {@link #followLatest follows its latest line}, the last: the
 * newest event, with older ones further on. Which way that runs through the
 * source's list is this class's business alone; a source just lists its
 * lines in their natural order, head first or oldest first.
 */
public final class Buffer {
    public final String key;
    private final Supplier<String> label;
    private final List<String> lines = new ArrayList<String>();
    private Supplier<? extends Iterable<String>> source;
    private int position;

    /**
     * An event log: home is the LAST line (the latest), switching to the
     * buffer lands there, and review walks back through the older ones.
     */
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

    /** Back to the home line: the source stayed, its subject changed (a new focus). */
    public void reset() {
        moveHome();
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
            position = homeIndex();
        }
    }

    private int homeIndex() {
        return followLatest && !lines.isEmpty() ? lines.size() - 1 : 0;
    }

    // One line further from home (+1) or back toward it (-1), as an index
    // step through the source's list.
    private boolean step(int awayFromHome) {
        refresh();
        int target = position + (followLatest ? -awayFromHome : awayFromHome);
        if (target < 0 || target >= lines.size()) {
            return false;
        }
        position = target;
        return true;
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

    /** One line further from home. False at the far end. */
    public boolean moveNext() {
        return step(1);
    }

    /** One line back toward home. False on the home line. */
    public boolean movePrevious() {
        return step(-1);
    }

    public void moveHome() {
        refresh();
        position = homeIndex();
    }
}
