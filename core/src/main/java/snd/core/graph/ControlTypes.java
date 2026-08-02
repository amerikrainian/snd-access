package snd.core.graph;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The control-type registry. One registry serves the whole mod — UI and any
 * world layer — so the per-type/per-kind announcement settings (a later phase)
 * have a single home. Role words are plain English for now; they route through
 * the strings/localization layer when it lands.
 */
public final class ControlTypes {
    private ControlTypes() {
    }

    /** The shared speak order: label, role, value, selected, enabled, tooltip, position. */
    public static final String[] STANDARD_ORDER = {
            AnnouncementKinds.LABEL, AnnouncementKinds.ROLE, AnnouncementKinds.VALUE,
            AnnouncementKinds.SELECTED, AnnouncementKinds.ENABLED, AnnouncementKinds.TOOLTIP,
            AnnouncementKinds.POSITION,
    };

    private static ControlType make(String key, final String roleWord) {
        Supplier<List<NodeAnnouncement>> common = roleWord == null ? null
                : new Supplier<List<NodeAnnouncement>>() {
                    @Override
                    public List<NodeAnnouncement> get() {
                        return Collections.singletonList(NodeAnnouncement.kinded(new Supplier<String>() {
                            @Override
                            public String get() {
                                return roleWord;
                            }
                        }, AnnouncementKinds.ROLE));
                    }
                };
        return new ControlType(key, STANDARD_ORDER, common);
    }

    public static final ControlType BUTTON = make("button", "button");
    public static final ControlType TOGGLE = make("toggle", "toggle");
    public static final ControlType SLIDER = make("slider", "slider");
    public static final ControlType RADIO = make("radio_button", "radio button");
    public static final ControlType TAB = make("tab", "tab");
    public static final ControlType GROUP = make("group", "group");
    public static final ControlType TEXT = make("text", null);
    /** A cycle-through-options control (left/right changes the value). */
    public static final ControlType CHOOSER = make("chooser", "chooser");

    public static final List<ControlType> ALL = Arrays.asList(
            BUTTON, TOGGLE, SLIDER, RADIO, TAB, GROUP, TEXT, CHOOSER);
}
