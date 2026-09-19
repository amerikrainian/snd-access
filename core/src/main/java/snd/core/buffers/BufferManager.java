package snd.core.buffers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ordered buffer roster plus the review cursor across buffers. Cycling
 * skips empty buffers; the current buffer may become empty under the cursor
 * (a focus change changed the sources' subject), in which case review
 * re-resolves to the first non-empty one.
 */
public final class BufferManager {
    private final List<Buffer> buffers = new ArrayList<Buffer>();
    private int position;

    public Buffer add(Buffer buffer) {
        buffers.add(buffer);
        return buffer;
    }

    public List<Buffer> buffers() {
        return Collections.unmodifiableList(buffers);
    }

    public Buffer get(String key) {
        for (Buffer b : buffers) {
            if (b.key.equals(key)) {
                return b;
            }
        }
        return null;
    }

    /**
     * The buffer under the review cursor, re-resolved to the first non-empty
     * buffer when the current one is empty. Null when every buffer is empty.
     */
    public Buffer current() {
        if (buffers.isEmpty()) {
            return null;
        }
        if (!buffers.get(position).isEmpty()) {
            return buffers.get(position);
        }
        for (int i = 0; i < buffers.size(); i++) {
            if (!buffers.get(i).isEmpty()) {
                position = i;
                return buffers.get(i);
            }
        }
        return null;
    }

    /** Make a buffer current (a focus change re-homing review to the control's own buffer). */
    public void setCurrent(String key) {
        for (int i = 0; i < buffers.size(); i++) {
            if (buffers.get(i).key.equals(key)) {
                land(i);
                return;
            }
        }
    }

    /** Step to the next/previous non-empty buffer, wrapping. False when no other non-empty buffer exists. */
    public boolean moveBuffer(int step) {
        int n = buffers.size();
        for (int i = 1; i < n; i++) {
            int idx = (((position + step * i) % n) + n) % n;
            if (!buffers.get(idx).isEmpty()) {
                land(idx);
                return true;
            }
        }
        return false;
    }

    private void land(int index) {
        position = index;
        if (buffers.get(index).followLatest) {
            buffers.get(index).moveHome(); // the latest line, not where review was left
        }
    }
}
