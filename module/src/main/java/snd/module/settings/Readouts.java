package snd.module.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import snd.core.graph.AnnouncementKinds;
import snd.core.graph.ControlType;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.settings.Announcements;
import snd.module.screens.UnitParts;

/**
 * Every readout the mod speaks, with its parts in their default order: what
 * the menu lets the player reorder and silence. A readout's parts are the
 * kinds of the announcement parts its speakers declare.
 */
final class Readouts {
    private Readouts() {
    }

    static void declare(Announcements a) {
        for (ControlType type : ControlTypes.ALL) {
            List<String> parts = new ArrayList<String>(Arrays.asList(type.order));
            if (type.common == null) {
                parts.remove(AnnouncementKinds.ROLE); // a type with no role word
            }
            a.readout(Announcements.READOUTS, type.key, parts);
        }
        a.readout(Announcements.READOUTS, GraphBuilder.CONTAINER_READOUT,
                Arrays.asList(AnnouncementKinds.LABEL, AnnouncementKinds.ROLE));
        UnitParts.declare(a);
        snd.module.screens.EventParts.declare(a);
    }
}
