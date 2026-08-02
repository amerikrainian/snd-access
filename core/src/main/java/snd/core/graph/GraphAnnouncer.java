package snd.core.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Composes the spoken line for a focus change by diffing the old and new focus
 * PATHS — each node's ancestor chain plus the node itself, compared by
 * identity. Newly-entered levels read outermost-first, then the landing
 * control: "Difficulty settings, list, Normal, radio button, selected".
 * Sibling moves share the whole prefix and read just the control; ascends
 * likewise; and descending from a group onto its own child re-announces
 * nothing but the child — the group is on the child's chain AND is the
 * from-node, so the prefix swallows it.
 */
public final class GraphAnnouncer {
    private GraphAnnouncer() {
    }

    /** Per-part filter installed by the host (the user's announcement settings). */
    public interface PartFilter {
        boolean allow(ControlType type, NodeAnnouncement part);
    }

    /** "n of m" wording, localized by the host. */
    public interface PositionText {
        String text(int index, int count);
    }

    /** Expanded/collapsed wording for group headers, localized by the host. */
    public interface ExpandedStateText {
        String text(boolean expanded);
    }

    /** Null (tests, boot) = everything speaks. Filters readouts AND the live watch. */
    public static volatile PartFilter partFilter;

    /** Null = no auto positions. */
    public static volatile PositionText positionText;

    /** Null = groups don't speak their state. */
    public static volatile ExpandedStateText expandedStateText;

    /**
     * The line for landing on to having come from from (null = from nothing:
     * the full path reads). transitionLabel is the crossed edge's spoken line,
     * when it had one. Null when there is nothing to say.
     */
    public static String compose(GraphNode from, GraphNode to, String transitionLabel) {
        if (to == null) {
            return null;
        }

        List<GraphNode> toPath = pathOf(to);
        List<GraphNode> fromPath = from != null ? pathOf(from) : Collections.<GraphNode>emptyList();

        // Common prefix by identity — levels we were already inside (or ON:
        // descending from a group onto its child keeps the group in the
        // prefix) stay silent.
        int i = 0;
        while (i < fromPath.size() && i < toPath.size() && fromPath.get(i).id.equals(toPath.get(i).id)) {
            i++;
        }

        List<String> parts = new ArrayList<String>();
        if (transitionLabel != null && !transitionLabel.isEmpty()) {
            parts.add(transitionLabel);
        }

        if (i >= toPath.size()) {
            // Ascended (or same node): announce just the now-innermost focus.
            String text = leafText(to);
            if (text != null && !text.isEmpty()) {
                parts.add(text);
            }
        } else {
            for (int j = i; j < toPath.size(); j++) {
                String text = leafText(toPath.get(j));
                if (text == null || text.isEmpty()) {
                    continue;
                }
                // Dedupe: a level whose label just duplicates the next level down.
                if (j + 1 < toPath.size()) {
                    String label = firstPartText(toPath.get(j));
                    String next = firstPartText(toPath.get(j + 1));
                    if (label != null && !label.isEmpty() && next != null && !next.isEmpty()
                            && duplicatesNext(label, next)) {
                        continue;
                    }
                }
                parts.add(text);
            }
        }

        if (parts.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < parts.size(); p++) {
            if (p > 0) {
                sb.append(", ");
            }
            sb.append(parts.get(p));
        }
        return sb.toString();
    }

    public static String compose(GraphNode from, GraphNode to) {
        return compose(from, to, null);
    }

    /** The full readout for a landing with no prior focus (screen entry, restore). */
    public static String composeFull(GraphNode to) {
        return compose(null, to, null);
    }

    // The node's path: ancestors outermost-first, then the node itself.
    private static List<GraphNode> pathOf(GraphNode node) {
        List<GraphNode> path = new ArrayList<GraphNode>();
        for (GraphNode n = node; n != null; n = n.parent) {
            path.add(n);
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * A node's EFFECTIVE announcement parts: the control type's common parts
     * (the role word) merged with the node's own — a node part overrides a
     * common part of the same kind — sorted by the type's kind order
     * (unknown/kindless parts append in declaration order), then filtered by
     * the user's settings. This is the single list readouts and the live watch
     * operate on.
     */
    public static List<NodeAnnouncement> effectiveAnnouncements(GraphNode node) {
        List<NodeAnnouncement> result = new ArrayList<NodeAnnouncement>();
        NodeVtable vt = node != null ? node.vtable : null;
        if (vt == null) {
            return result;
        }
        ControlType type = vt.controlType;

        List<NodeAnnouncement> common = type != null && type.common != null ? type.common.get() : null;
        if (common != null) {
            for (NodeAnnouncement c : common) {
                if (c != null && !hasKind(vt.announcements, c.kind)) {
                    result.add(c);
                }
            }
        }
        if (vt.announcements != null) {
            for (NodeAnnouncement a : vt.announcements) {
                if (a != null) {
                    result.add(a);
                }
            }
        }

        if (type != null && type.order != null && type.order.length > 0 && result.size() > 1) {
            // Stable: composite key = (kind's order index, declaration index).
            final List<NodeAnnouncement> unsorted = new ArrayList<NodeAnnouncement>(result);
            final long[] keys = new long[unsorted.size()];
            Integer[] indices = new Integer[unsorted.size()];
            for (int i = 0; i < unsorted.size(); i++) {
                keys[i] = ((long) orderIndex(type.order, unsorted.get(i).kind) << 32) | i;
                indices[i] = i;
            }
            java.util.Arrays.sort(indices, new java.util.Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return Long.compare(keys[a], keys[b]);
                }
            });
            result.clear();
            for (Integer idx : indices) {
                result.add(unsorted.get(idx));
            }
        }

        PartFilter filter = partFilter;
        if (filter != null) {
            for (int i = result.size() - 1; i >= 0; i--) {
                if (!filter.allow(type, result.get(i))) {
                    result.remove(i);
                }
            }
        }
        return result;
    }

    private static boolean hasKind(List<NodeAnnouncement> anns, String kind) {
        if (anns == null || kind == null) {
            return false;
        }
        for (NodeAnnouncement a : anns) {
            if (a != null && kind.equals(a.kind)) {
                return true;
            }
        }
        return false;
    }

    // Sort key: declared kinds by their order index; everything else after
    // (one shared bucket, declaration-index tie-break keeps relative order).
    private static int orderIndex(String[] order, String kind) {
        if (kind != null) {
            for (int i = 0; i < order.length; i++) {
                if (order[i].equals(kind)) {
                    return i;
                }
            }
        }
        return order.length;
    }

    /**
     * A node's own readout: its effective announcement parts, resolved live,
     * non-empty ones joined — plus, for an expandable group, its
     * expanded/collapsed state word, plus the auto-stamped sibling position.
     */
    public static String leafText(GraphNode node) {
        List<NodeAnnouncement> anns = effectiveAnnouncements(node);
        StringBuilder sb = new StringBuilder();
        for (NodeAnnouncement a : anns) {
            String t = null;
            if (a != null && a.text != null) {
                t = a.text.get();
            }
            if (t == null || t.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(t);
        }
        ExpandedStateText est = expandedStateText;
        if (node != null && node.expandable && !node.vtable.speaksOwnExpansion && est != null) {
            String state = est.text(node.expanded);
            if (state != null && !state.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(state);
            }
        }

        // The auto-stamped sibling position, unless the node carries its own.
        // Honors the user's per-kind setting via the stand-in probe part.
        PositionText pt = positionText;
        PartFilter filter = partFilter;
        if (node != null && node.positionCount > 1 && pt != null
                && !node.vtable.speaksOwnPosition
                && !hasKind(node.vtable.announcements, AnnouncementKinds.POSITION)
                && (filter == null || filter.allow(node.vtable.controlType, AUTO_POSITION_PROBE))) {
            String pos = pt.text(node.positionIndex, node.positionCount);
            if (pos != null && !pos.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(pos);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // A stand-in part handed to the partFilter so the user's position-kind
    // toggle governs the auto-stamped position too.
    private static final NodeAnnouncement AUTO_POSITION_PROBE =
            NodeAnnouncement.kinded(new java.util.function.Supplier<String>() {
                @Override
                public String get() {
                    return null;
                }
            }, AnnouncementKinds.POSITION);

    /** The first announcement part's text (the label) — for dedupe and search. */
    public static String firstPartText(GraphNode node) {
        List<NodeAnnouncement> anns = node != null && node.vtable != null ? node.vtable.announcements : null;
        if (anns == null || anns.isEmpty()) {
            return null;
        }
        NodeAnnouncement first = anns.get(0);
        return first != null && first.text != null ? first.text.get() : null;
    }

    // The next part "starts as" this label: equal, or its first
    // comma-separated segment is the label.
    private static boolean duplicatesNext(String label, String next) {
        if (!next.startsWith(label)) {
            return false;
        }
        return next.length() == label.length() || next.charAt(label.length()) == ',';
    }
}
