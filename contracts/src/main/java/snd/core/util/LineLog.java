package snd.core.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A bounded ring of lines with monotonic global indices and long-poll support.
 * Backs the dev server's /speech and /log endpoints: readers pass the cursor
 * from the previous response and get only new lines, or block in waitForNew
 * until one lands.
 */
public final class LineLog {
    private final int capacity;
    private final Deque<String> lines = new ArrayDeque<String>();
    private long end; // index of the next line to be written

    public LineLog(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void add(String line) {
        if (lines.size() == capacity) {
            lines.removeFirst();
        }
        lines.addLast(line);
        end++;
        notifyAll();
    }

    public synchronized long end() {
        return end;
    }

    /** Renders lines with index >= since as "index: text", newest last. */
    public synchronized String render(long since) {
        StringBuilder sb = new StringBuilder();
        sb.append("cursor: ").append(end).append('\n');
        long first = end - lines.size();
        long idx = first;
        for (String line : lines) {
            if (idx >= since) {
                sb.append(idx).append(": ").append(line).append('\n');
            }
            idx++;
        }
        return sb.toString();
    }

    /** Blocks until a line with index >= since exists, or the timeout passes. */
    public synchronized boolean waitForNew(long since, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (end <= since) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            try {
                wait(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
