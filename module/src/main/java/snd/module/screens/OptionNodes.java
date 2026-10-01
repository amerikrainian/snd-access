package snd.module.screens;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

import com.tann.dice.gameplay.save.settings.option.BOption;
import com.tann.dice.gameplay.save.settings.option.ChOption;
import com.tann.dice.gameplay.save.settings.option.FlOption;
import com.tann.dice.gameplay.save.settings.option.Option;

import snd.core.graph.AnnouncementKinds;
import snd.core.graph.ControlTypes;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.module.Captured;
import snd.module.GameText;

/**
 * A game option as one control, read from the option: a yes/no option as a
 * toggle, a choice option as a chooser, a fraction as a slider, each with its
 * name, its value and the description the game shows on right-click. How a
 * change is made is the caller's: the Options tab sets the option, a place
 * showing the game's own widgets presses them.
 */
final class OptionNodes {
    private OptionNodes() {
    }

    static NodeVtable toggle(final BOption option, Runnable activate) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TOGGLE;
        final Supplier<String> state = new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", option.c() ? "state.checked" : "state.unchecked");
            }
        };
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(name(option), AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(state, AnnouncementKinds.VALUE),
                NodeAnnouncement.kinded(description(option), AnnouncementKinds.TOOLTIP));
        vt.onActivate = activate;
        vt.stateText = state;
        return vt;
    }

    /** valueName turns a choice's caption into its spoken word (a place's shorthand). */
    static NodeVtable chooser(final ChOption option, final Function<String, String> valueName,
            NodeVtable.Adjust adjust) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.CHOOSER;
        final Supplier<String> value = new Supplier<String>() {
            @Override
            public String get() {
                String caption = option.getOptions()[option.c()];
                return option == com.tann.dice.platform.control.desktop.DesktopControl.SCREEN_MODE
                        ? screenModeWord(option, caption) : valueName.apply(GameText.t(caption));
            }
        };
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(name(option), AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(value, AnnouncementKinds.VALUE),
                NodeAnnouncement.kinded(description(option), AnnouncementKinds.TOOLTIP));
        vt.onAdjust = adjust;
        vt.stateText = value;
        return vt;
    }

    static NodeVtable slider(final FlOption option, NodeVtable.Adjust adjust) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.SLIDER;
        final Supplier<String> value = new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", "value.percent", "value", Math.round(option.getVal() * 100f));
            }
        };
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(name(option), AnnouncementKinds.LABEL),
                new NodeAnnouncement(value, true, AnnouncementKinds.VALUE));
        vt.onAdjust = adjust;
        vt.stateText = value;
        return vt;
    }

    // The screen modes' captions are shorthand ("w", "fs", "fs2") that the
    // option's description spells out a line each, every line a translated
    // game string ("w: [blue]windowed[cu]"): the word after the caption.
    private static String screenModeWord(ChOption option, String caption) {
        String shorthand = snd.contracts.speech.TextFilter.clean(caption).trim();
        String desc = (String) Captured.field(option, Option.class, "desc");
        if (desc != null) {
            for (String line : desc.split("\\[n\\]")) {
                if (snd.contracts.speech.TextFilter.clean(line).startsWith(shorthand + ":")) {
                    String spoken = snd.contracts.speech.TextFilter.clean(GameText.t(line));
                    return spoken.substring(spoken.indexOf(':') + 1).trim();
                }
            }
        }
        return shorthand;
    }

    /** The next choice from the current one, wrapping. */
    static int step(ChOption option, int sign) {
        int count = option.getOptions().length;
        return ((option.c() + sign) % count + count) % count;
    }

    private static Supplier<String> name(final Option option) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return GameText.t(option.getName());
            }
        };
    }

    // The right-click-only description, straight off the option.
    private static Supplier<String> description(final Option option) {
        return new Supplier<String>() {
            @Override
            public String get() {
                String desc = (String) Captured.field(option, Option.class, "desc");
                return desc != null ? GameText.t(desc) : null;
            }
        };
    }
}
