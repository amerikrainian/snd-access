package snd.core.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

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

    /**
     * The player's announcement settings, installed by the module: for a
     * readout (a node's {@link NodeVtable#readout}, else its control type's
     * key), the order its parts speak in and whether a part speaks at all.
     */
    public interface PartPolicy {
        /** The readout's parts in speaking order, or null for its control type's default order. */
        List<String> order(String readout);

        /** Whether part speaks in readout; readout null for a node that has none (its global setting). */
        boolean enabled(String readout, String part);
    }

    /** "n of m" wording, localized by the host. */
    public interface PositionText {
        String text(int index, int count);
    }

    /** Expanded/collapsed wording for group headers, localized by the host. */
    public interface ExpandedStateText {
        String text(boolean expanded);
    }

    /** Null (tests, boot) = every part speaks, in the default order. Governs readouts AND the live watch. */
    public static volatile PartPolicy partPolicy;

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

    /** The readout a node speaks as: its own, else its control type's. Null when it has neither. */
    public static String readoutOf(GraphNode node) {
        NodeVtable vt = node != null ? node.vtable : null;
        if (vt == null) {
            return null;
        }
        if (vt.readout != null) {
            return vt.readout;
        }
        return vt.controlType != null ? vt.controlType.key : null;
    }

    /**
     * A node's EFFECTIVE announcement parts: the control type's common parts
     * (the role word) merged with the node's own — a node part overrides a
     * common part of the same kind — and the parts the engine supplies (an
     * expandable group's state, the sibling position), sorted by the
     * readout's order (unknown/kindless parts append in declaration order),
     * then filtered by the player's settings. This is the single list
     * readouts, the live watch and the control buffer operate on.
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
        NodeAnnouncement expansion = expansionPart(node);
        if (expansion != null) {
            result.add(expansion);
        }
        NodeAnnouncement position = positionPart(node);
        if (position != null) {
            result.add(position);
        }

        String readout = readoutOf(node);
        PartPolicy policy = partPolicy;
        List<String> order = policy != null && readout != null ? policy.order(readout) : null;
        if (order == null && type != null && type.order != null) {
            order = Arrays.asList(type.order);
        }
        if (order != null && !order.isEmpty() && result.size() > 1) {
            // Stable: composite key = (kind's order index, declaration index).
            final List<NodeAnnouncement> unsorted = new ArrayList<NodeAnnouncement>(result);
            final long[] keys = new long[unsorted.size()];
            Integer[] indices = new Integer[unsorted.size()];
            for (int i = 0; i < unsorted.size(); i++) {
                keys[i] = ((long) orderIndex(order, unsorted.get(i).kind) << 32) | i;
                indices[i] = i;
            }
            Arrays.sort(indices, new Comparator<Integer>() {
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

        if (policy != null) {
            for (int i = result.size() - 1; i >= 0; i--) {
                String kind = result.get(i).kind;
                if (kind != null && !policy.enabled(readout, kind)) {
                    result.remove(i);
                }
            }
        }
        return result;
    }

    // An expandable group's expanded/collapsed word, unless it speaks its own.
    private static NodeAnnouncement expansionPart(final GraphNode node) {
        final ExpandedStateText est = expandedStateText;
        if (!node.expandable || node.vtable.speaksOwnExpansion || est == null) {
            return null;
        }
        return NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return est.text(node.expanded);
            }
        }, AnnouncementKinds.EXPANDED);
    }

    // The auto-stamped sibling position, unless the node carries its own.
    private static NodeAnnouncement positionPart(GraphNode node) {
        final PositionText pt = positionText;
        if (node.positionCount <= 1 || pt == null || node.vtable.speaksOwnPosition
                || hasKind(node.vtable.announcements, AnnouncementKinds.POSITION)) {
            return null;
        }
        final int index = node.positionIndex;
        final int count = node.positionCount;
        return NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return pt.text(index, count);
            }
        }, AnnouncementKinds.POSITION);
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

    // Sort key: ordered kinds by their index; everything else after
    // (one shared bucket, declaration-index tie-break keeps relative order).
    private static int orderIndex(List<String> order, String kind) {
        int i = kind != null ? order.indexOf(kind) : -1;
        return i >= 0 ? i : order.size();
    }

    /** A node's own readout: its effective announcement parts, resolved live, non-empty ones joined. */
    public static String leafText(GraphNode node) {
        StringBuilder sb = new StringBuilder();
        for (NodeAnnouncement a : effectiveAnnouncements(node)) {
            String t = a.text != null ? a.text.get() : null;
            if (t == null || t.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(t);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

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
