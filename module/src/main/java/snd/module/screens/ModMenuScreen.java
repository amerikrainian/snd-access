package snd.module.screens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import com.badlogic.gdx.Gdx;

import snd.contracts.HostServices;
import snd.contracts.SndLog;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.core.nav.AccessScreen;
import snd.core.nav.GraphNavigator;
import snd.core.settings.BoolSetting;
import snd.core.settings.CategorySetting;
import snd.core.settings.ChoiceSetting;
import snd.core.settings.IntSetting;
import snd.core.settings.PartOrder;
import snd.core.settings.Setting;
import snd.core.settings.StringSetting;
import snd.module.ModVersion;
import snd.module.settings.ModSettings;

/**
 * The mod's menu (Ctrl+Shift+M anywhere): a hub — the settings, the change
 * log, the latest release — and the settings tree under it, one page per
 * category. Enter opens a page; Escape or Backspace goes back one page, and
 * from the hub closes the menu. A page of a readout's parts lists one row per
 * part, in the order they speak: the part's on/off toggle, then Move up and
 * Move down (focus stays on the button, so it can be pressed again), then
 * Configure when the part has settings of its own.
 */
public final class ModMenuScreen extends AccessScreen {
    private static final String RELEASE_URL = "https://github.com/amerikrainian/snd-access/releases/latest";
    private static final String CHANGELOG_URL = "https://github.com/amerikrainian/snd-access/blob/main/CHANGELOG.md";

    private final HostServices host;
    private final GraphNavigator nav;

    private boolean open;
    // The pages opened from the hub, outermost first; empty = the hub.
    private final List<CategorySetting> pages = new ArrayList<CategorySetting>();

    public ModMenuScreen(HostServices host, GraphNavigator nav) {
        this.host = host;
        this.nav = nav;
    }

    public void toggle() {
        if (open) {
            open = false;
            return;
        }
        pages.clear();
        open = true;
    }

    @Override
    public String key() {
        return "mod-menu";
    }

    @Override
    public boolean isActive() {
        return open;
    }

    @Override
    public int layer() {
        return 50; // over every game-backed screen; a choice's list and the key help open over it
    }

    @Override
    public String screenName() {
        return Loc.get("ui", "menu.title", "version", ModVersion.current());
    }

    @Override
    public boolean exclusive() {
        return true;
    }

    @Override
    public boolean onCancel() {
        back();
        return true;
    }

    private void back() {
        if (pages.isEmpty()) {
            open = false;
            return;
        }
        CategorySetting left = pages.remove(pages.size() - 1);
        nav.focusNode(entryId(left), true, true);
    }

    private void openPage(CategorySetting page) {
        pages.add(page);
        List<Setting> shown = page.shown();
        if (!shown.isEmpty()) {
            nav.focusNode(firstId(page, shown.get(0)), true, true);
        }
    }

    @Override
    public void build(GraphBuilder b) {
        b.beginStop(CompositeKey.of("menu", pages.size()));
        if (pages.isEmpty()) {
            hub(b);
            return;
        }
        CategorySetting page = pages.get(pages.size() - 1);
        b.pushContext(page.label());
        if (page.partOrder != null) {
            resetButton(b, page);
        }
        for (Setting s : page.shown()) {
            if (page.partOrder != null && page.partOrder.defaults().contains(s.key)) {
                partRow(b, page, (CategorySetting) s);
            } else {
                control(b, s);
            }
        }
        b.popContext();
    }

    // ---- the hub ----

    private void hub(GraphBuilder b) {
        button(b, ControlId.structural(CompositeKey.of("menu", "hub", "settings")), Setting.loc("menu.settings"),
                new Runnable() {
                    @Override
                    public void run() {
                        openPage(ModSettings.get().settings.root);
                    }
                });
        button(b, ControlId.structural(CompositeKey.of("menu", "hub", "changelog")), Setting.loc("menu.changelog"),
                browse(CHANGELOG_URL));
        button(b, ControlId.structural(CompositeKey.of("menu", "hub", "release")), Setting.loc("menu.release"),
                browse(RELEASE_URL));
        button(b, ControlId.structural(CompositeKey.of("menu", "hub", "close")), Setting.loc("menu.close"),
                new Runnable() {
                    @Override
                    public void run() {
                        open = false;
                    }
                });
    }

    private Runnable browse(final String url) {
        return new Runnable() {
            @Override
            public void run() {
                boolean opened = false;
                try {
                    opened = Gdx.net.openURI(url);
                } catch (Throwable t) {
                    SndLog.error("menu: opening " + url + " failed", t);
                }
                if (!opened) {
                    SndLog.error("menu: the system would not open " + url, null);
                }
                host.speech().speak(Loc.get("ui", opened ? "menu.opened" : "menu.open_failed"), true);
            }
        };
    }

    // ---- a page's controls ----

    private ControlId entryId(Setting s) {
        return ControlId.referenced(s, CompositeKey.of("menu", "entry", s.path()));
    }

    // The node a page lands on for its first shown setting: a part row's toggle.
    private ControlId firstId(CategorySetting page, Setting first) {
        return page.partOrder != null && page.partOrder.defaults().contains(first.key)
                ? ControlId.referenced(first, CompositeKey.of("menu", "part", first.path(), "speak"))
                : entryId(first);
    }

    private void control(GraphBuilder b, final Setting s) {
        if (s instanceof CategorySetting) {
            button(b, entryId(s), labelOf(s), new Runnable() {
                @Override
                public void run() {
                    openPage((CategorySetting) s);
                }
            });
        } else if (s instanceof BoolSetting) {
            toggle(b, entryId(s), labelOf(s), (BoolSetting) s);
        } else if (s instanceof IntSetting) {
            slider(b, (IntSetting) s);
        } else if (s instanceof ChoiceSetting) {
            dropdown(b, (ChoiceSetting) s);
        } else if (!(s instanceof StringSetting)) {
            SndLog.error("menu: no control for setting " + s.path() + " (" + s.getClass().getSimpleName() + ")", null);
        }
    }

    private static Supplier<String> labelOf(final Setting s) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return s.label();
            }
        };
    }

    private void button(GraphBuilder b, ControlId id, Supplier<String> label, Runnable run) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(label, AnnouncementKinds.LABEL));
        vt.onActivate = run;
        add(b, id, vt);
    }

    // Every control goes back a page on Backspace, as Escape does.
    private void add(GraphBuilder b, ControlId id, NodeVtable vt) {
        vt.onSecondary = new Runnable() {
            @Override
            public void run() {
                back();
            }
        };
        b.addItem(id, vt);
    }

    private static String onOff(boolean on) {
        return Loc.get("ui", on ? "state.checked" : "state.unchecked");
    }

    // A readout's own setting is marked only where it departs from the
    // global one; following it is the rule.
    private static String toggleValue(BoolSetting s) {
        String v = onOff(s.get());
        return s.fallback() != null && !s.inherits() ? Loc.get("ui", "settings.custom", "value", v) : v;
    }

    private void toggle(GraphBuilder b, ControlId id, Supplier<String> label, final BoolSetting s) {
        toggleNode(b, id, label, s, null);
    }

    private void toggleNode(GraphBuilder b, ControlId id, Supplier<String> label, final BoolSetting s,
            NodeAnnouncement extra) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TOGGLE;
        Supplier<String> value = new Supplier<String>() {
            @Override
            public String get() {
                return toggleValue(s);
            }
        };
        List<NodeAnnouncement> parts = new ArrayList<NodeAnnouncement>();
        parts.add(NodeAnnouncement.kinded(label, AnnouncementKinds.LABEL));
        parts.add(NodeAnnouncement.kinded(value, AnnouncementKinds.VALUE));
        if (extra != null) {
            parts.add(extra);
        }
        vt.announcements = parts;
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                s.toggle();
            }
        };
        vt.stateText = value;
        add(b, id, vt);
    }

    private void slider(GraphBuilder b, final IntSetting s) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.SLIDER;
        Supplier<String> value = new Supplier<String>() {
            @Override
            public String get() {
                return String.valueOf(s.get());
            }
        };
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(labelOf(s), AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(value, AnnouncementKinds.VALUE));
        vt.onAdjust = new NodeVtable.Adjust() {
            @Override
            public void adjust(int sign, boolean large) {
                s.adjust(sign * (large ? 5 : 1));
            }
        };
        vt.stateText = value;
        add(b, entryId(s), vt);
    }

    private void dropdown(GraphBuilder b, final ChoiceSetting s) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.DROPDOWN;
        final Supplier<String> value = new Supplier<String>() {
            @Override
            public String get() {
                return s.chosen().label();
            }
        };
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(labelOf(s), AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(value, AnnouncementKinds.VALUE));
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                List<String> labels = new ArrayList<String>();
                for (ChoiceSetting.Option o : s.options()) {
                    labels.add(o.label());
                }
                DropdownScreen.open(s.label(), labels, s.index(), new IntConsumer() {
                    @Override
                    public void accept(int index) {
                        s.set(s.options().get(index).key);
                    }
                }, value);
            }
        };
        add(b, entryId(s), vt);
    }

    private void resetButton(GraphBuilder b, final CategorySetting page) {
        button(b, ControlId.referenced(page, CompositeKey.of("menu", "reset", page.path())), Setting.loc("settings.reset"),
                new Runnable() {
                    @Override
                    public void run() {
                        page.reset();
                        host.speech().speak(Loc.get("ui", "settings.reset_done"), true);
                    }
                });
    }

    // ---- a readout's part, as one row ----

    private void partRow(GraphBuilder b, final CategorySetting page, final CategorySetting part) {
        final PartOrder order = page.partOrder;
        b.pushContext(part, labelOf(part));
        b.startRow("part");

        Supplier<String> position = new Supplier<String>() {
            @Override
            public String get() {
                List<String> parts = order.order();
                return Loc.get("ui", "position", "index", parts.indexOf(part.key) + 1, "count", parts.size());
            }
        };
        BoolSetting speak = (BoolSetting) part.child("enabled");
        toggleNode(b, ControlId.referenced(part, CompositeKey.of("menu", "part", part.path(), "speak")), labelOf(part), speak,
                NodeAnnouncement.kinded(position, AnnouncementKinds.POSITION));
        moveButton(b, order, part, -1, "settings.move_up");
        moveButton(b, order, part, 1, "settings.move_down");
        if (part.shown().size() > 1) {
            button(b, ControlId.referenced(part, CompositeKey.of("menu", "part", part.path(), "configure")),
                    Setting.loc("settings.configure"), new Runnable() {
                        @Override
                        public void run() {
                            openPage(part);
                        }
                    });
        }
        b.endRow();
        b.popContext();
    }

    private void moveButton(GraphBuilder b, final PartOrder order, final CategorySetting part, final int by,
            String labelKey) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(Setting.loc(labelKey), AnnouncementKinds.LABEL));
        vt.speaksOwnPosition = true;
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                if (!order.move(part.key, by)) {
                    host.speech().speak(Loc.get("ui", by < 0 ? "settings.already_first" : "settings.already_last"),
                            true);
                    return;
                }
                host.speech().speak(movedText(order.order(), part.key, (CategorySetting) part.parent()), true);
            }
        };
        add(b, ControlId.referenced(part, CompositeKey.of("menu", "part", part.path(), by < 0 ? "up" : "down")), vt);
    }

    // "Moved between hp and statuses", or before the first / after the last.
    private static String movedText(List<String> order, String key, CategorySetting page) {
        int at = order.indexOf(key);
        String before = at > 0 ? page.child(order.get(at - 1)).label() : null;
        String after = at < order.size() - 1 ? page.child(order.get(at + 1)).label() : null;
        if (before != null && after != null) {
            return Loc.get("ui", "settings.moved_between", "before", before, "after", after);
        }
        return before != null ? Loc.get("ui", "settings.moved_after", "before", before)
                : Loc.get("ui", "settings.moved_before", "after", after);
    }
}
