package snd.core.buffers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import snd.contracts.SndLog;
import snd.contracts.speech.TextFilter;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.GraphAnnouncer;
import snd.core.graph.GraphNode;
import snd.core.graph.NodeAnnouncement;

/**
 * The control buffer's lines for a focused node: its head line (the state,
 * label, value parts of its readout, never the role word or the "n of m"
 * position), then one line per description part
 * ({@link AnnouncementKinds#TOOLTIP}), then the vtable's
 * {@link snd.core.graph.NodeVtable#details}, one line each. A detail that
 * only repeats a head part or a comma-separated piece of one (an item tooltip
 * whose title is the item's name, through markup or not) or an earlier detail
 * is folded; blank lines are dropped. Read live on every buffer keypress.
 */
public final class NodeLines {
    private NodeLines() {
    }

    public static List<String> lines(GraphNode node) {
        List<String> out = new ArrayList<String>();
        if (node == null) {
            return out;
        }
        List<String> head = new ArrayList<String>();
        List<String> details = new ArrayList<String>();
        for (NodeAnnouncement part : GraphAnnouncer.effectiveAnnouncements(node)) {
            if (part == null || part.text == null || AnnouncementKinds.ROLE.equals(part.kind)
                    || AnnouncementKinds.POSITION.equals(part.kind)) {
                continue;
            }
            String text = null;
            try {
                text = part.text.get();
            } catch (Throwable t) {
                SndLog.error("buffer lines: a part failed", t);
            }
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            (AnnouncementKinds.TOOLTIP.equals(part.kind) ? details : head).add(text);
        }

        Set<String> seen = new HashSet<String>();
        if (!head.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String h : head) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(h);
                // Said already: the part, and each piece of a part that joins
                // several ("First Boss, achieved" has said "First Boss").
                String clean = TextFilter.clean(h);
                seen.add(clean);
                for (String piece : clean.split(",")) {
                    seen.add(piece.trim());
                }
            }
            out.add(sb.toString());
        }

        if (node.vtable.details != null) {
            try {
                List<String> own = node.vtable.details.get();
                if (own != null) {
                    details.addAll(own);
                }
            } catch (Throwable t) {
                SndLog.error("buffer lines: details failed", t);
            }
        }
        // A detail repeating a head part, or an earlier detail (the same
        // tooltip reached twice), is folded: the reader steps through new
        // information only.
        for (String line : details) {
            if (line == null) {
                continue;
            }
            String clean = TextFilter.clean(line);
            if (!clean.isEmpty() && seen.add(clean)) {
                out.add(line);
            }
        }
        return out;
    }
}
