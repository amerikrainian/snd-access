package snd.core.nav;

import snd.core.graph.GraphAnnouncer;

/**
 * The announcer's pluggable wording, installed by the HOST at boot. Lives in
 * core (app classloader) so the announcer's statics never reference a
 * module-loader class — assigning them from the reloadable module would pin
 * every old module in memory. English for now; routes through the strings
 * layer when it lands.
 */
public final class GraphDefaults {
    private GraphDefaults() {
    }

    public static void install() {
        GraphAnnouncer.positionText = new GraphAnnouncer.PositionText() {
            @Override
            public String text(int index, int count) {
                return index + " of " + count;
            }
        };
        GraphAnnouncer.expandedStateText = new GraphAnnouncer.ExpandedStateText() {
            @Override
            public String text(boolean expanded) {
                return expanded ? "expanded" : "collapsed";
            }
        };
    }
}
