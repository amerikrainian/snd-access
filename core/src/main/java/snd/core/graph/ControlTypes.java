package snd.core.graph;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import snd.core.loc.Loc;

/**
 * The control-type registry. One registry serves the whole mod — UI and any
 * world layer — so the per-type/per-kind announcement settings (a later phase)
 * have a single home. Role words resolve through {@link Loc} ("ui" table,
 * "role.&lt;key&gt;") at announce time, so they follow the live language.
 */
public final class ControlTypes {
    private ControlTypes() {
    }

    /** The shared speak order: state, label, role, value, selected, enabled, tooltip, position. */
    public static final String[] STANDARD_ORDER = {
            AnnouncementKinds.STATE, AnnouncementKinds.LABEL, AnnouncementKinds.ROLE,
            AnnouncementKinds.VALUE, AnnouncementKinds.SELECTED, AnnouncementKinds.ENABLED,
            AnnouncementKinds.TOOLTIP, AnnouncementKinds.POSITION,
    };

    private static ControlType make(String key, boolean spoken) {
        final String roleKey = "role." + key;
        Supplier<List<NodeAnnouncement>> common = !spoken ? null
                : new Supplier<List<NodeAnnouncement>>() {
                    @Override
                    public List<NodeAnnouncement> get() {
                        return Collections.singletonList(NodeAnnouncement.kinded(new Supplier<String>() {
                            @Override
                            public String get() {
                                return Loc.get("ui", roleKey);
                            }
                        }, AnnouncementKinds.ROLE));
                    }
                };
        return new ControlType(key, STANDARD_ORDER, common);
    }

    public static final ControlType BUTTON = make("button", true);
    public static final ControlType TOGGLE = make("toggle", true);
    public static final ControlType SLIDER = make("slider", true);
    public static final ControlType RADIO = make("radio_button", true);
    public static final ControlType TAB = make("tab", true);
    public static final ControlType GROUP = make("group", true);
    public static final ControlType TEXT = make("text", false);
    /** A value chosen from a list Enter opens. */
    public static final ControlType DROPDOWN = make("dropdown", true);

    public static final List<ControlType> ALL = Arrays.asList(
            BUTTON, TOGGLE, SLIDER, RADIO, TAB, GROUP, TEXT, DROPDOWN);
}
