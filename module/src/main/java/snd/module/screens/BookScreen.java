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

import snd.core.SndLog;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.core.nav.AccessScreen;
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
    private final snd.core.HostServices host;

    public BookScreen(snd.core.HostServices host) {
        this.host = host;
    }

    @Override
    public String key() {
        return "book";
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
        } else {
            ActorNodes.emit(b, content);
        }
        b.popContext();
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
        return snd.core.speech.TextFilter.clean(text);
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
