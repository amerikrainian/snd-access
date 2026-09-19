package snd.core.nav;

import snd.core.graph.GraphAnnouncer;
import snd.core.loc.Loc;

/**
 * The announcer's pluggable wording, installed by the module at load: the
 * announcer's statics belong to the generation that loaded them. Wording
 * resolves through {@link Loc} at speak time, so it follows the live language.
 */
public final class GraphDefaults {
    private GraphDefaults() {
    }

    public static void install() {
        GraphAnnouncer.positionText = new GraphAnnouncer.PositionText() {
            @Override
            public String text(int index, int count) {
                return Loc.get("ui", "position", "index", index, "count", count);
            }
        };
        GraphAnnouncer.expandedStateText = new GraphAnnouncer.ExpandedStateText() {
            @Override
            public String text(boolean expanded) {
                return Loc.get("ui", expanded ? "state.expanded" : "state.collapsed");
            }
        };
    }
}
