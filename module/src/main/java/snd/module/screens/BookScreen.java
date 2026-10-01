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
    public boolean onCancel() {
        return GameUi.popTopModalOnly();
    }

    @Override
    public int layer() {
        return 19; // over the base screens; a panel pushed over the Book (the modal reader, 20) covers it
    }

    @Override
    public boolean isActive() {
        // While the Book is open at all, not only while it is on top: a
        // details panel pushed over it covers this screen rather than closing
        // it, so closing the panel lands back on the entry that opened it.
        return GameUi.modalOpen(Book.class);
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
        } else if (!(tab instanceof com.tann.dice.screens.dungeon.panels.book.page.ledgerPage.LedgerPage.LedgerPageType)
                || !LedgerNodes.emit(b, content,
                        (com.tann.dice.screens.dungeon.panels.book.page.ledgerPage.LedgerPage.LedgerPageType) tab,
                        LedgerFacts.of(page))) {
            ActorNodes.emit(b, content, place(tab));
        }
        b.popContext();
    }

    // The jukebox transport (JukeboxUtils.makeSongControls), also carried by
    // the cog menu's sound panel.
    static final java.util.Map<String, String> JUKEBOX_GLYPHS = new java.util.HashMap<String, String>();
    // The "?" beside the achievements tab's two headings (LedgerPage.makeInfoButton).
    private static final java.util.Map<String, String> UNLOCK_GLYPHS =
            java.util.Collections.singletonMap("?", "glyph.help");
    static {
        JUKEBOX_GLYPHS.put("<-", "glyph.skip_back");
        JUKEBOX_GLYPHS.put(">", "glyph.skip_forward");
        JUKEBOX_GLYPHS.put("->", "glyph.next_song");
    }

    // The Modifier tab's two filter rows (LedgerUtils.makeModifiersGroup).
    // Each button's runnable holds its own value (val$blessLoop, val$mt) and
    // the page's current value of the other row (val$genType, val$bless), so
    // a button is the chosen one when its value is the current one the other
    // row's buttons hold. Tested when a button is spoken.
    private static final java.util.function.Predicate<Actor> CHOSEN_MODIFIER_FILTER =
            new java.util.function.Predicate<Actor>() {
                @Override
                public boolean test(Actor actor) {
                    Runnable own = filterRunnable(actor);
                    if (own == null || actor.getParent() == null) {
                        return false;
                    }
                    String ownName = snd.module.Captured.declares(own, "val$blessLoop") ? "val$blessLoop" : "val$mt";
                    String currentName = ownName.equals("val$blessLoop") ? "val$bless" : "val$genType";
                    for (Actor sibling : actor.getParent().getChildren()) {
                        Runnable other = filterRunnable(sibling);
                        if (other != null && snd.module.Captured.declares(other, currentName)) {
                            return java.util.Objects.equals(
                                    snd.module.Captured.field(own, own.getClass(), ownName),
                                    snd.module.Captured.field(other, other.getClass(), currentName));
                        }
                    }
                    return false;
                }
            };

    private static Runnable filterRunnable(Actor actor) {
        if (!(actor instanceof com.tann.dice.util.ui.standardButton.StandardButton)) {
            return null;
        }
        Runnable run = snd.module.Captured.runnable((com.tann.dice.util.ui.standardButton.StandardButton) actor);
        return snd.module.Captured.builtBy(run,
                com.tann.dice.screens.dungeon.panels.book.page.ledgerPage.LedgerUtils.class, "makeModifiersGroup")
                ? run : null;
    }

    // The TextMod tab's rows (APIUtils), against the page the game saved as
    // shown (Settings.lastTextmodPage: "1" info, "2" api, "3" and the pipe's
    // tag for api-2): the section row's buttons hold their page index, the
    // letter row's their pipe, and the type row leaves the shown type's
    // button without a runnable.
    private static final java.util.function.Predicate<Actor> CHOSEN_TEXTMOD_PAGE =
            new java.util.function.Predicate<Actor>() {
                @Override
                public boolean test(Actor actor) {
                    if (!(actor instanceof com.tann.dice.util.ui.standardButton.StandardButton)) {
                        return false;
                    }
                    String shown = com.tann.dice.Main.getSettings().getLastTextmodPage();
                    if (shown == null || shown.isEmpty()) {
                        return false;
                    }
                    Runnable run = snd.module.Captured.runnable((com.tann.dice.util.ui.standardButton.StandardButton) actor);
                    Class<?> api = com.tann.dice.screens.dungeon.panels.book.page.stuffPage.APIUtils.class;
                    if (snd.module.Captured.builtBy(run, api, "makeTopPixl")) {
                        return Integer.valueOf(shown.charAt(0) - '1').equals(snd.module.Captured.primitive(run, int.class));
                    }
                    if (snd.module.Captured.builtBy(run, api, "makeTMPageAPI2")) {
                        com.tann.dice.gameplay.content.gen.pipe.Pipe pipe =
                                snd.module.Captured.value(run, com.tann.dice.gameplay.content.gen.pipe.Pipe.class);
                        return pipe != null && shown.equals("3" + pipe.getIdTag());
                    }
                    return run == null && shown.startsWith("3") && inTypeRow(actor);
                }
            };

    // A button among the api-2 page's type buttons, whose runnables hold a PipeType.
    private static boolean inTypeRow(Actor actor) {
        if (actor.getParent() == null) {
            return false;
        }
        for (Actor sibling : actor.getParent().getChildren()) {
            if (sibling instanceof com.tann.dice.util.ui.standardButton.StandardButton && snd.module.Captured.value(
                    snd.module.Captured.runnable((com.tann.dice.util.ui.standardButton.StandardButton) sibling),
                    com.tann.dice.screens.dungeon.panels.book.page.stuffPage.PipeType.class) != null) {
                return true;
            }
        }
        return false;
    }

    private static ActorNodes.Place place(Object tab) {
        if (tab == com.tann.dice.screens.dungeon.panels.book.page.stuffPage.StuffPage.StuffSection.Jukebox) {
            return ActorNodes.Place.glyphs(JUKEBOX_GLYPHS);
        }
        if (tab == com.tann.dice.screens.dungeon.panels.book.page.ledgerPage.LedgerPage.LedgerPageType.Unlock) {
            return ActorNodes.Place.glyphs(UNLOCK_GLYPHS);
        }
        if (tab == com.tann.dice.screens.dungeon.panels.book.page.ledgerPage.LedgerPage.LedgerPageType.Modifier) {
            return ActorNodes.Place.chosenBy(CHOSEN_MODIFIER_FILTER);
        }
        if (tab == com.tann.dice.screens.dungeon.panels.book.page.ledgerPage.LedgerPage.LedgerPageType.TextMod) {
            return ActorNodes.Place.chosenBy(CHOSEN_TEXTMOD_PAGE);
        }
        return ActorNodes.Place.PLAIN;
    }

    // The side-value curves, as numbers: the plot tells series apart by hash
    // colour alone. Read from what the graph was built with (GraphUtils.make,
    // held by its "+" button's runnable): the plotted sides, the pip range,
    // and hero or monster mode. A row per side and reference unit, as the
    // graph draws a curve for each (GraphUtils.addSideToGraph).
    private void buildGraphTable(GraphBuilder b, Actor content) {
        Runnable graph = graphRunnable(content);
        if (graph == null) {
            if (!graphMissReported) {
                graphMissReported = true;
                SndLog.error("graph tab: the graph's \"+\" button was not found", null);
            }
            return;
        }
        graphMissReported = false;
        List<?> plotted = snd.module.Captured.value(graph, List.class);
        Object maxPipsBox = snd.module.Captured.primitive(graph, int.class);
        Object heroBox = snd.module.Captured.primitive(graph, boolean.class);
        if (plotted == null || plotted.isEmpty() || maxPipsBox == null || heroBox == null) {
            return;
        }
        int maxPips = (Integer) maxPipsBox;
        com.tann.dice.gameplay.content.ent.type.EntType[] references = (Boolean) heroBox
                ? new com.tann.dice.gameplay.content.ent.type.EntType[] {
                        com.tann.dice.gameplay.content.ent.type.lib.HeroTypeUtils.byName("thief"),
                        com.tann.dice.gameplay.content.ent.type.lib.HeroTypeUtils.byName("guardian"),
                        com.tann.dice.gameplay.content.ent.type.lib.HeroTypeUtils.byName("veteran")}
                : new com.tann.dice.gameplay.content.ent.type.EntType[] {
                        com.tann.dice.gameplay.content.ent.type.lib.MonsterTypeLib.byName("bones")};
        String[] headers = new String[Math.max(0, maxPips - 1)];
        for (int pip = 2; pip <= maxPips; pip++) {
            headers[pip - 2] = Loc.get("ui", "book.pips_col", "n", pip);
        }
        snd.core.graph.GraphSheet sheet = new snd.core.graph.GraphSheet(b, "graphtab");
        sheet.region(Loc.get("ui", "book.value_table"), headers);
        for (Object o : plotted) {
            final com.tann.dice.gameplay.content.ent.die.side.EntSide side =
                    (com.tann.dice.gameplay.content.ent.die.side.EntSide) o;
            for (final com.tann.dice.gameplay.content.ent.type.EntType reference : references) {
                NodeVtable primary = new NodeVtable();
                primary.controlType = ControlTypes.TEXT;
                primary.announcements = Arrays.asList(
                        NodeAnnouncement.kinded(new Supplier<String>() {
                            @Override
                            public String get() {
                                return SideText.of(side.getBaseEffect()) + ", " + snd.module.GameText.t(reference.getName(true));
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
                sheet.row(primary, snd.core.graph.CompositeKey.of(side, reference), cells);
            }
        }
        sheet.finish();
    }

    private boolean graphMissReported;

    // The graph's "+" button (GraphUtils.make): a direct child of the graph
    // group, which the game names "graph".
    private static Runnable graphRunnable(Actor content) {
        Actor graph = content instanceof com.badlogic.gdx.scenes.scene2d.Group
                ? ((com.badlogic.gdx.scenes.scene2d.Group) content).findActor("graph") : null;
        if (!(graph instanceof com.badlogic.gdx.scenes.scene2d.Group)) {
            return null;
        }
        for (Actor child : ((com.badlogic.gdx.scenes.scene2d.Group) graph).getChildren()) {
            if (child instanceof com.tann.dice.util.ui.standardButton.StandardButton) {
                Runnable run = snd.module.Captured.runnable((com.tann.dice.util.ui.standardButton.StandardButton) child);
                if (snd.module.Captured.builtBy(run, com.tann.dice.screens.graph.GraphUtils.class, "make")) {
                    return run;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Supplier<String>[] makeCells(final com.tann.dice.gameplay.content.ent.die.side.EntSide side,
            final com.tann.dice.gameplay.content.ent.type.EntType reference, int maxPips) {
        Supplier<String>[] cells = new Supplier[Math.max(0, maxPips - 1)];
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
            // OptionsMenu.boxType's list: the font option is left out in
            // Russian, and the unlocked options come before the locked ones.
            List<com.tann.dice.gameplay.save.settings.option.Option> options =
                    new java.util.ArrayList<com.tann.dice.gameplay.save.settings.option.Option>(category.getOptions());
            if ("ru".equals(com.tann.dice.Main.self().translator.getLanguageCode())) {
                options.remove(com.tann.dice.gameplay.save.settings.option.OptionLib.FONT);
            }
            List<com.tann.dice.gameplay.save.settings.option.Option> ordered =
                    new java.util.ArrayList<com.tann.dice.gameplay.save.settings.option.Option>();
            for (boolean locked : com.tann.dice.util.Tann.BOTH) {
                for (com.tann.dice.gameplay.save.settings.option.Option option : options) {
                    if (option.isValid() && !option.isDebug()
                            && com.tann.dice.gameplay.progress.chievo.unlock.UnUtil.isLocked(option) == locked) {
                        ordered.add(option);
                    }
                }
            }
            for (final com.tann.dice.gameplay.save.settings.option.Option option : ordered) {
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
            addFoundButton(b, root, com.tann.dice.screens.dungeon.panels.book.page.cogPage.menuPanel.OptionsMenu.class,
                    "boxType", "book-opt-cog");
            addFoundButton(b, root, com.tann.dice.screens.dungeon.panels.book.page.cogPage.menuPanel.OptionsMenu.class,
                    "makeResetButton", "book-opt-reset");
        }
    }

    private final java.util.Set<Object> missingButtons = new java.util.HashSet<Object>();
    // Each page's buttons, found once per page the game builds.
    private final java.util.Map<Actor, java.util.Map<Object, com.tann.dice.util.ui.standardButton.StandardButton>> foundButtons =
            new java.util.WeakHashMap<Actor, java.util.Map<Object, com.tann.dice.util.ui.standardButton.StandardButton>>();

    // A button the page builds inline (the game keeps it in no field), found
    // by the game method that built its runnable.
    private void addFoundButton(GraphBuilder b, com.badlogic.gdx.scenes.scene2d.Group root,
            Class<?> owner, String method, Object key) {
        java.util.Map<Object, com.tann.dice.util.ui.standardButton.StandardButton> found = foundButtons.get(root);
        if (found == null) {
            found = new java.util.HashMap<Object, com.tann.dice.util.ui.standardButton.StandardButton>();
            foundButtons.put(root, found);
        }
        if (!found.containsKey(key)) {
            found.put(key, GameUi.findButtonBuiltBy(root, owner, method));
        }
        final com.tann.dice.util.ui.standardButton.StandardButton button = found.get(key);
        if (button == null) {
            // The game adds these unconditionally. Said once per miss.
            if (missingButtons.add(key)) {
                SndLog.error("almanac button not found: built by " + owner.getSimpleName() + "." + method, null);
            }
            return;
        }
        missingButtons.remove(key);
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
        return OptionNodes.toggle(option, new Runnable() {
            @Override
            public void run() {
                boolean on = !option.c();
                com.tann.dice.statics.sound.Sounds.playSound(on
                        ? com.tann.dice.statics.sound.Sounds.pip
                        : com.tann.dice.statics.sound.Sounds.pop);
                option.setValue(on, true); // warning dialogs ride manualSelectAction
            }
        });
    }

    private NodeVtable choiceOptionNode(final com.tann.dice.gameplay.save.settings.option.ChOption option) {
        return OptionNodes.chooser(option, java.util.function.Function.<String>identity(), new NodeVtable.Adjust() {
            @Override
            public void adjust(int sign, boolean large) {
                option.setValue(OptionNodes.step(option, sign), true);
            }
        });
    }

    private NodeVtable sliderOptionNode(final com.tann.dice.gameplay.save.settings.option.FlOption option) {
        return OptionNodes.slider(option, new NodeVtable.Adjust() {
            @Override
            public void adjust(int sign, boolean large) {
                float step = large ? 0.2f : 0.05f;
                float value = Math.max(0f, Math.min(1f, option.getVal() + sign * step));
                option.setValue(value, true);
            }
        });
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

        if (content instanceof com.badlogic.gdx.scenes.scene2d.Group) {
            // Its own confirm dialog follows.
            addFoundButton(b, (com.badlogic.gdx.scenes.scene2d.Group) content,
                    com.tann.dice.screens.dungeon.panels.book.page.stuffPage.StuffPage.class, "makeNumbersPage",
                    "book-reset-stats");
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
                }, AnnouncementKinds.LABEL));
        // Moving onto a tab opens it (the navigator's rule for tabs), so the
        // open one is not announced as "selected" — it only says where
        // entering the group lands.
        vt.selected = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return tabFocused(tab);
            }
        };
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
