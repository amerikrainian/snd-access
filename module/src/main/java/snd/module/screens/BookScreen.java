package snd.module.screens;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.tann.dice.screens.dungeon.panels.book.Book;
import com.tann.dice.screens.dungeon.panels.book.SideBar;
import com.tann.dice.screens.dungeon.panels.book.TopTab;
import com.tann.dice.screens.dungeon.panels.book.page.BookPage;

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
import snd.core.nav.KeyOffer;
import snd.module.GameKeys;
import snd.module.GameUi;

/**
 * The almanac (Book): the game swallows every key except Escape while it is
 * open — all tab switching is clicking and scrolling is mouse-wheel — but our
 * processor sits ahead of it, so the navigator works normally. Three stops:
 * the page tabs (help/ledger/stuff), the focused page's sub-tabs, and the
 * current tab's content. Tabs activate through their own TopTab listeners
 * (sound, focus highlight, last-page memory); content is walked from the
 * page's scroll panel, with model-driven builders for the tabs whose visuals
 * don't read (options, lifetime numbers).
 */
public class BookScreen extends AccessScreen {
    private final snd.contracts.HostServices host;

    public BookScreen(snd.contracts.HostServices host) {
        this.host = host;
    }

    @Override
    public String key() {
        return "book";
    }

    @Override
    public List<KeyOffer> keys() {
        return GameKeys.escapeOnly();
    }

    @Override
    public int layer() {
        return 20; // replaces the generic modal reader while the Book is top
    }

    @Override
    public boolean isActive() {
        return GameUi.topModal() instanceof Book;
    }

    @Override
    public String screenName() {
        return Loc.get("ui", "book.title");
    }

    @Override
    public void build(GraphBuilder b) {
        Book book = (Book) GameUi.topModal();
        if (book == null) {
            return;
        }

        b.beginStop("pages").pushContext(Loc.get("ui", "book.pages"), Loc.get("ui", "role.list"));
        SideBar pageBar = bookSideBar(book);
        if (pageBar != null) {
            for (TopTab tab : pageBar.getItems()) {
                b.addItem(ControlId.referenced(tab, CompositeKey.of("book-page", tab.getTabName())),
                        tabNode(tab));
            }
        }
        b.popContext();

        BookPage page = focusedPage(book);
        if (page == null) {
            return;
        }

        b.beginStop("tabs").pushContext(
                Loc.get("ui", "book.tabs", "page", stripMarkup(page.title)),
                Loc.get("ui", "role.list"));
        for (TopTab tab : page.getSideBar().getItems()) {
            b.addItem(ControlId.referenced(tab, CompositeKey.of("book-tab", tab.getTabName())),
                    tabNode(tab));
        }
        b.popContext();

        b.beginStop("content");
        buildContent(b, page);
    }

    // The current tab's content. The generic actor walk covers the mostly-
    // textual pages; tabs whose visuals don't read get model-driven builders.
    private void buildContent(GraphBuilder b, BookPage page) {
        Actor content = contentPanel(page);
        if (content == null) {
            return;
        }
        b.pushContext(Loc.get("ui", "book.content"), null, false);
        Object tab = focusedTabIdentifier(page);
        if (tab == com.tann.dice.screens.dungeon.panels.book.page.stuffPage.StuffPage.StuffSection.Numbers) {
            buildNumbers(b, content);
        } else if (tab == com.tann.dice.screens.dungeon.panels.book.page.stuffPage.StuffPage.StuffSection.Options) {
            buildOptions(b, content);
        } else if (tab == com.tann.dice.screens.dungeon.panels.book.page.stuffPage.StuffPage.StuffSection.Graph) {
            ActorNodes.emit(b, content); // series icons (named), add/remove
            buildGraphTable(b, content);
        } else {
            ActorNodes.emit(b, content);
        }
        b.popContext();
    }

    // The side-value curves, as numbers: the plot identifies series only by
    // hash colours; this table gives each plotted side its calculated value
    // per pip count (the same computation the curves draw for the strongest
    // reference hero).
    private void buildGraphTable(GraphBuilder b, Actor content) {
        List<com.tann.dice.gameplay.content.ent.die.side.EntSide> sides =
                new java.util.ArrayList<com.tann.dice.gameplay.content.ent.die.side.EntSide>();
        collectSides(content, sides);
        if (sides.isEmpty()) {
            return;
        }
        final com.tann.dice.gameplay.content.ent.type.EntType reference;
        try {
            reference = com.tann.dice.gameplay.content.ent.type.lib.HeroTypeUtils.byName("veteran");
        } catch (Throwable t) {
            SndLog.error("graph reference hero missing", t);
            return;
        }
        int maxPips = 6;
        String[] headers = new String[maxPips - 1];
        for (int pip = 2; pip <= maxPips; pip++) {
            headers[pip - 2] = Loc.get("ui", "book.pips_col", "n", pip);
        }
        snd.core.graph.GraphSheet sheet = new snd.core.graph.GraphSheet(b, "graphtab");
        sheet.region(Loc.get("ui", "book.value_table"), headers);
        for (final com.tann.dice.gameplay.content.ent.die.side.EntSide side : sides) {
            NodeVtable primary = new NodeVtable();
            primary.controlType = ControlTypes.TEXT;
            primary.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return snd.module.GameText.t(side.getBaseEffect().describe());
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return Loc.get("ui", "book.pips_col", "n", 1) + " "
                                    + graphValue(side, 1, reference);
                        }
                    }, AnnouncementKinds.VALUE));
            Supplier<String>[] cells = makeCells(side, reference, maxPips);
            sheet.row(primary, side, cells);
        }
        sheet.finish();
    }

    @SuppressWarnings("unchecked")
    private Supplier<String>[] makeCells(final com.tann.dice.gameplay.content.ent.die.side.EntSide side,
            final com.tann.dice.gameplay.content.ent.type.EntType reference, int maxPips) {
        Supplier<String>[] cells = new Supplier[maxPips - 1];
        for (int pip = 2; pip <= maxPips; pip++) {
            final int p = pip;
            cells[pip - 2] = new Supplier<String>() {
                @Override
                public String get() {
                    return graphValue(side, p, reference);
                }
            };
        }
        return cells;
    }

    private static String graphValue(com.tann.dice.gameplay.content.ent.die.side.EntSide side,
            int pips, com.tann.dice.gameplay.content.ent.type.EntType reference) {
        try {
            return String.format(java.util.Locale.US, "%.1f",
                    side.withValue(pips).getEffectTier(reference));
        } catch (Throwable t) {
            SndLog.error("graph value failed", t);
            return "?";
        }
    }

    private static void collectSides(Actor actor,
            List<com.tann.dice.gameplay.content.ent.die.side.EntSide> out) {
        com.tann.dice.gameplay.content.ent.die.side.EntSide side = ActorNodes.sideOf(actor);
        if (side != null && !out.contains(side)) {
            out.add(side);
        }
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.Group) {
            for (Actor child : ((com.badlogic.gdx.scenes.scene2d.Group) actor).getChildren()) {
                collectSides(child, out);
            }
        }
    }

    // The options screen, from the option registry rather than its pointer-
    // only widgets: checkboxes become toggles, radio rows become choosers,
    // sliders adjust with Left/Right — every value change through the
    // option's own setValue, so warning dialogs, saves, and rebuild side
    // effects behave exactly as a click. Locked options read as the padlock
    // the sighted player sees, with the unlock requirement on Backspace.
    private void buildOptions(GraphBuilder b, Actor content) {
        for (com.tann.dice.gameplay.save.settings.option.OptionUtils.EscBopType category
                : com.tann.dice.gameplay.save.settings.option.OptionUtils.EscBopType.values()) {
            if (!category.shownInOptions) {
                continue;
            }
            b.pushContext(snd.module.GameText.t(category.toString()), Loc.get("ui", "role.group"));
            for (final com.tann.dice.gameplay.save.settings.option.Option option : category.getOptions()) {
                if (!option.isValid() || option.isDebug()) {
                    continue;
                }
                ControlId id = ControlId.referenced(option,
                        CompositeKey.of("book-option", option.getName()));
                if (com.tann.dice.gameplay.progress.chievo.unlock.UnUtil.isLocked(option)) {
                    NodeVtable vt = new NodeVtable();
                    vt.controlType = ControlTypes.TEXT;
                    vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return Loc.get("ui", "state.locked");
                        }
                    }, AnnouncementKinds.LABEL));
                    vt.onSecondary = new Runnable() {
                        @Override
                        public void run() {
                            com.tann.dice.gameplay.progress.chievo.AchLib.showUnlockFor(option);
                        }
                    };
                    b.addItem(id, vt);
                } else if (option instanceof com.tann.dice.gameplay.save.settings.option.BOption) {
                    b.addItem(id, boolOptionNode(
                            (com.tann.dice.gameplay.save.settings.option.BOption) option));
                } else if (option instanceof com.tann.dice.gameplay.save.settings.option.ChOption) {
                    b.addItem(id, choiceOptionNode(
                            (com.tann.dice.gameplay.save.settings.option.ChOption) option));
                } else if (option instanceof com.tann.dice.gameplay.save.settings.option.FlOption) {
                    b.addItem(id, sliderOptionNode(
                            (com.tann.dice.gameplay.save.settings.option.FlOption) option));
                } else {
                    b.addLabel(id, new Supplier<String>() {
                        @Override
                        public String get() {
                            return snd.module.GameText.t(option.getName());
                        }
                    });
                }
            }
            b.popContext();
        }

        // The tab's own buttons: Reset All (confirm dialog) and the Music
        // section's display/sound shortcut into the cog menu.
        if (content instanceof com.badlogic.gdx.scenes.scene2d.Group) {
            com.badlogic.gdx.scenes.scene2d.Group root = (com.badlogic.gdx.scenes.scene2d.Group) content;
            addFoundButton(b, root, "display/sound", "book-opt-cog");
            addFoundButton(b, root, "Reset All", "book-opt-reset");
        }
    }

    private void addFoundButton(GraphBuilder b, com.badlogic.gdx.scenes.scene2d.Group root,
            String text, Object key) {
        final com.tann.dice.util.ui.standardButton.StandardButton button =
                GameUi.findButtonByText(root, text);
        if (button == null) {
            return;
        }
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return GameUi.labelOf(button);
            }
        }, AnnouncementKinds.LABEL));
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                GameUi.activate(button);
            }
        };
        b.addItem(ControlId.referenced(button, key), vt);
    }

    private NodeVtable boolOptionNode(final com.tann.dice.gameplay.save.settings.option.BOption option) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TOGGLE;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return snd.module.GameText.t(option.getName());
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return Loc.get("ui", option.c() ? "state.checked" : "state.unchecked");
                    }
                }, AnnouncementKinds.VALUE),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return optionDescription(option);
                    }
                }, AnnouncementKinds.TOOLTIP));
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                boolean on = !option.c();
                com.tann.dice.statics.sound.Sounds.playSound(on
                        ? com.tann.dice.statics.sound.Sounds.pip
                        : com.tann.dice.statics.sound.Sounds.pop);
                option.setValue(on, true); // warning dialogs ride manualSelectAction
            }
        };
        vt.stateText = new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", option.c() ? "state.checked" : "state.unchecked");
            }
        };
        return vt;
    }

    private NodeVtable choiceOptionNode(final com.tann.dice.gameplay.save.settings.option.ChOption option) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.CHOOSER;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return snd.module.GameText.t(option.getName());
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return snd.module.GameText.t(option.getOptions()[option.c()]);
                    }
                }, AnnouncementKinds.VALUE),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return optionDescription(option);
                    }
                }, AnnouncementKinds.TOOLTIP));
        vt.onAdjust = new NodeVtable.Adjust() {
            @Override
            public void adjust(int sign, boolean large) {
                String[] values = option.getOptions();
                int next = ((option.c() + sign) % values.length + values.length) % values.length;
                option.setValue(next, true);
            }
        };
        vt.stateText = new Supplier<String>() {
            @Override
            public String get() {
                return snd.module.GameText.t(option.getOptions()[option.c()]);
            }
        };
        return vt;
    }

    private NodeVtable sliderOptionNode(final com.tann.dice.gameplay.save.settings.option.FlOption option) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.SLIDER;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return snd.module.GameText.t(option.getName());
                    }
                }, AnnouncementKinds.LABEL),
                new NodeAnnouncement(new Supplier<String>() {
                    @Override
                    public String get() {
                        return Loc.get("ui", "value.percent", "value", Math.round(option.getVal() * 100f));
                    }
                }, true, AnnouncementKinds.VALUE));
        vt.onAdjust = new NodeVtable.Adjust() {
            @Override
            public void adjust(int sign, boolean large) {
                float step = large ? 0.2f : 0.05f;
                float value = Math.max(0f, Math.min(1f, option.getVal() + sign * step));
                option.setValue(value, true);
            }
        };
        vt.stateText = new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", "value.percent", "value", Math.round(option.getVal() * 100f));
            }
        };
        return vt;
    }

    // The right-click-only option description, straight off the model.
    private static Field optionDescField;

    private static String optionDescription(com.tann.dice.gameplay.save.settings.option.Option option) {
        try {
            if (optionDescField == null) {
                optionDescField = com.tann.dice.gameplay.save.settings.option.Option.class
                        .getDeclaredField("desc");
                optionDescField.setAccessible(true);
            }
            String desc = (String) optionDescField.get(option);
            return desc != null ? snd.module.GameText.t(desc) : null;
        } catch (Throwable t) {
            SndLog.error("option description read failed", t);
            return null;
        }
    }

    // The lifetime-stats tab renders name and value as two parallel columns
    // of separate actors (association purely spatial); rebuild each line from
    // the same merged-stats data, then the tab's own Reset Stats button.
    private void buildNumbers(GraphBuilder b, Actor content) {
        try {
            java.util.Map<String, com.tann.dice.gameplay.progress.stats.stat.Stat> merged =
                    com.tann.dice.Main.self().masterStats.createMergedStats();
            for (int side = 0; side < 2; side++) {
                java.util.List<com.tann.dice.gameplay.progress.stats.stat.Stat> stats =
                        new java.util.ArrayList<com.tann.dice.gameplay.progress.stats.stat.Stat>();
                for (com.tann.dice.gameplay.progress.stats.stat.Stat stat : merged.values()) {
                    if (stat.showInAlmanac(side)) {
                        stats.add(stat);
                    }
                }
                Collections.sort(stats,
                        new java.util.Comparator<com.tann.dice.gameplay.progress.stats.stat.Stat>() {
                            @Override
                            public int compare(com.tann.dice.gameplay.progress.stats.stat.Stat a,
                                    com.tann.dice.gameplay.progress.stats.stat.Stat b) {
                                return a.getOrder() - b.getOrder();
                            }
                        });
                for (final com.tann.dice.gameplay.progress.stats.stat.Stat stat : stats) {
                    b.addLabel(ControlId.referenced(stat, CompositeKey.of("book-stat", stat.getName())),
                            new Supplier<String>() {
                                @Override
                                public String get() {
                                    return snd.module.GameText.t(stat.getNameForDisplay()) + " "
                                            + snd.module.GameText.t(stat.getValueForDisplay());
                                }
                            });
                }
            }
        } catch (Throwable t) {
            SndLog.error("numbers page build failed", t);
        }

        final com.tann.dice.util.ui.standardButton.StandardButton reset =
                content instanceof com.badlogic.gdx.scenes.scene2d.Group
                        ? GameUi.findButtonByText((com.badlogic.gdx.scenes.scene2d.Group) content, "Reset Stats")
                        : null;
        if (reset != null) {
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.BUTTON;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return GameUi.labelOf(reset);
                }
            }, AnnouncementKinds.LABEL));
            vt.onActivate = new Runnable() {
                @Override
                public void run() {
                    GameUi.activate(reset); // its own confirm dialog follows
                }
            };
            b.addItem(ControlId.referenced(reset, "book-reset-stats"), vt);
        }
    }

    private NodeVtable tabNode(final TopTab tab) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TAB;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return tab.getTabName();
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return tabFocused(tab) ? Loc.get("ui", "state.selected") : null;
                    }
                }, AnnouncementKinds.SELECTED));
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                GameUi.activate(tab); // the tab's own listener: sound + focus + memory
            }
        };
        return vt;
    }

    private static String stripMarkup(String text) {
        return snd.contracts.speech.TextFilter.clean(text);
    }

    // ---- the Book's non-public structure: its page bar, the focused page,
    // each page's content scroll panel, a tab's highlight flag ----

    private static Field bookSideBarField;
    private static Field bookFocusedField;
    private static Field contentPanelField;
    private static Field tabFocusedField;

    private static SideBar bookSideBar(Book book) {
        try {
            if (bookSideBarField == null) {
                bookSideBarField = Book.class.getDeclaredField("sideBar");
                bookSideBarField.setAccessible(true);
            }
            return (SideBar) bookSideBarField.get(book);
        } catch (Throwable t) {
            SndLog.error("failed to read Book.sideBar", t);
            return null;
        }
    }

    static BookPage focusedPage(Book book) {
        try {
            if (bookFocusedField == null) {
                bookFocusedField = Book.class.getDeclaredField("focused");
                bookFocusedField.setAccessible(true);
            }
            return (BookPage) bookFocusedField.get(book);
        } catch (Throwable t) {
            SndLog.error("failed to read Book.focused", t);
            return null;
        }
    }

    static Actor contentPanel(BookPage page) {
        try {
            if (contentPanelField == null) {
                contentPanelField = BookPage.class.getDeclaredField("contentPanel");
                contentPanelField.setAccessible(true);
            }
            return (Actor) contentPanelField.get(page);
        } catch (Throwable t) {
            SndLog.error("failed to read BookPage.contentPanel", t);
            return null;
        }
    }

    static boolean tabFocused(TopTab tab) {
        try {
            if (tabFocusedField == null) {
                tabFocusedField = TopTab.class.getDeclaredField("focused");
                tabFocusedField.setAccessible(true);
            }
            return tabFocusedField.getBoolean(tab);
        } catch (Throwable t) {
            SndLog.error("failed to read TopTab.focused", t);
            return false;
        }
    }

    /** The focused sub-tab's identifier (a HelpType / LedgerPageType / StuffSection), or null. */
    static Object focusedTabIdentifier(BookPage page) {
        List<TopTab> tabs = page.getSideBar() != null
                ? page.getSideBar().getItems() : Collections.<TopTab>emptyList();
        for (TopTab tab : tabs) {
            if (tabFocused(tab)) {
                return tab.getIdentifier();
            }
        }
        return null;
    }
}
