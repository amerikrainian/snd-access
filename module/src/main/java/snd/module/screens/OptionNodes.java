package snd.module.screens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
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
 * toggle, a choice option as a dropdown, a fraction as a slider, each with its
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

    /**
     * A choice option as a dropdown: Enter opens the list of its values on
     * the one it holds ({@link DropdownScreen}), and choose applies the index
     * picked. valueName turns a choice's caption into its spoken word (a
     * place's shorthand). The values are in the list, so the description,
     * which often spells them out, is the control buffer's.
     */
    static NodeVtable dropdown(final ChOption option, final Function<String, String> valueName,
            final IntConsumer choose) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.DROPDOWN;
        final Supplier<String> value = new Supplier<String>() {
            @Override
            public String get() {
                return valueWord(option, option.c(), valueName);
            }
        };
        final Supplier<String> name = name(option);
        final Supplier<String> description = description(option);
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(name, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(value, AnnouncementKinds.VALUE));
        vt.details = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                String desc = description.get();
                return desc != null ? Collections.singletonList(desc) : Collections.<String>emptyList();
            }
        };
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                List<String> values = new ArrayList<String>();
                for (int i = 0; i < option.getOptions().length; i++) {
                    values.add(valueWord(option, i, valueName));
                }
                DropdownScreen.open(name.get(), values, option.c(), choose, value);
            }
        };
        return vt;
    }

    private static String valueWord(ChOption option, int index, Function<String, String> valueName) {
        String caption = option.getOptions()[index];
        return option == com.tann.dice.platform.control.desktop.DesktopControl.SCREEN_MODE
                ? screenModeWord(option, caption) : valueName.apply(GameText.t(caption));
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
