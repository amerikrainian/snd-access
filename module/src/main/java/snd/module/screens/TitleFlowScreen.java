package snd.module.screens;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.tann.dice.gameplay.context.config.ContextConfig;
import com.tann.dice.gameplay.context.config.difficultyConfig.DifficultyConfig;
import com.tann.dice.gameplay.mode.Mode;
import com.tann.dice.gameplay.mode.meta.folder.FolderMode;
import com.tann.dice.gameplay.progress.chievo.AchLib;
import com.tann.dice.gameplay.progress.chievo.unlock.UnUtil;
import com.tann.dice.gameplay.save.SaveState;
import com.tann.dice.screens.dungeon.DungeonUtils;
import com.tann.dice.screens.dungeon.panels.book.Book;
import com.tann.dice.screens.titleScreen.GameStart;
import com.tann.dice.screens.titleScreen.ModesPanel;
import com.tann.dice.screens.titleScreen.TitleScreen;

import snd.core.SndLog;
import snd.core.loc.Loc;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.nav.AccessScreen;
import snd.module.GameText;
import snd.module.GameUi;

/**
 * The title screen: mode selection, the selected mode's card (description,
 * difficulty start buttons with win records, continue), folder navigation,
 * and the system buttons. Everything reads and drives the game's own model —
 * selection state comes from the live ModesPanel, activation goes through
 * TitleScreen.showMode / GameStart / SaveState, so mouse use and our
 * navigation can never disagree. Locked modes are listed BY NAME with a
 * locked state (the visual UI blanks them); activating one opens the game's
 * own unlock-requirement panel, which the modal screen reads.
 */
public class TitleFlowScreen extends AccessScreen {
    private final snd.core.HostServices host;

    public TitleFlowScreen(snd.core.HostServices host) {
        this.host = host;
    }

    @Override
    public String key() {
        return "title";
    }

    @Override
    public boolean isActive() {
        return com.tann.dice.Main.getCurrentScreen() instanceof TitleScreen;
    }

    @Override
    public void build(GraphBuilder b) {
        ModesPanel panel = GameUi.modesPanel();
        Mode selected = panel != null ? panel.getSelectedMode() : null;

        buildModesStop(b, selected);
        if (selected != null) {
            buildCardStop(b, selected);
        }
        buildSystemStop(b);
    }

    // ---- stop 1: the mode list (the drawer, with locked names restored) ----

    private void buildModesStop(GraphBuilder b, Mode selected) {
        b.beginStop("modes").pushContext(Loc.get("ui", "title.modes"), Loc.get("ui", "role.list"));
        for (Mode m : Mode.getPlayableModes()) {
            if (m.skipFromMainList()) {
                continue;
            }
            b.addItem(modeId(m), modeButton(m, m == selected));
        }
        b.popContext();
    }

    private static ControlId modeId(Mode m) {
        return ControlId.referenced(m, CompositeKey.of("mode", m.getName()));
    }

    private NodeVtable modeButton(final Mode m, final boolean selected) {
        final boolean locked = UnUtil.isLocked(m);
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return GameText.t(m.getName());
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return selected ? Loc.get("ui", "state.selected") : null;
                    }
                }, AnnouncementKinds.SELECTED),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return locked ? Loc.get("ui", "state.locked") : null;
                    }
                }, AnnouncementKinds.ENABLED));
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                if (locked) {
                    // The game's own unlock-requirement panel (for a locked
                    // folder, its first contained mode — the game's rule);
                    // the modal screen reads it.
                    Mode target = m;
                    if (m instanceof FolderMode) {
                        List<Mode> children = ((FolderMode) m).getContainedModes();
                        if (!children.isEmpty()) {
                            target = children.get(0);
                        }
                    }
                    AchLib.showUnlockFor(target);
                } else {
                    TitleScreen.showMode(m);
                }
            }
        };
        vt.onSecondary = new Runnable() {
            @Override
            public void run() {
                m.showModeInfo(); // wins/history/leaderboards panel → modal screen
            }
        };
        return vt;
    }

    // ---- stop 2: the selected mode's card ----

    private void buildCardStop(GraphBuilder b, final Mode mode) {
        b.beginStop("card").pushContext(
                Loc.get("ui", "title.mode_context", "mode", GameText.t(mode.getName())));

        if (mode.getParent() != null) {
            NodeVtable back = new NodeVtable();
            back.controlType = ControlTypes.BUTTON;
            back.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return Loc.get("ui", "title.back_to", "mode", GameText.t(mode.getParent().getName()));
                }
            }, AnnouncementKinds.LABEL));
            back.onActivate = new Runnable() {
                @Override
                public void run() {
                    TitleScreen.showMode(mode.getParent());
                }
            };
            b.addItem(ControlId.structural(CompositeKey.of("card", "back")), back);
        }

        String[] description = mode.getDescriptionLines();
        if (description != null) {
            for (int i = 0; i < description.length; i++) {
                final String line = description[i];
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                NodeVtable vt = new NodeVtable();
                vt.controlType = ControlTypes.TEXT;
                vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return GameText.t(line);
                    }
                }, AnnouncementKinds.LABEL));
                b.addItem(ControlId.structural(CompositeKey.of("card", "desc", i)), vt);
            }
        }

        if (mode instanceof FolderMode) {
            for (Mode child : ((FolderMode) mode).getContainedModes()) {
                b.addItem(ControlId.referenced(child, CompositeKey.of("card", "child", child.getName())),
                        modeButton(child, false));
            }
        } else {
            buildStartButtons(b, mode);
        }
        b.popContext();
    }

    private void buildStartButtons(GraphBuilder b, Mode mode) {
        List<ContextConfig> configs;
        try {
            configs = mode.getConfigs();
        } catch (Throwable t) {
            SndLog.error("getConfigs failed for " + mode.getName(), t);
            return;
        }
        for (ContextConfig cc : configs) {
            b.addItem(ControlId.referenced(cc, CompositeKey.of("card", "start", startLabel(cc))),
                    startButton(cc));
        }
        // The mode's single autosave slot: one Continue node when it exists.
        if (!configs.isEmpty() && configs.get(0).hasSave()) {
            final ContextConfig first = configs.get(0);
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.BUTTON;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return Loc.get("ui", "title.continue");
                }
            }, AnnouncementKinds.LABEL));
            vt.onActivate = new Runnable() {
                @Override
                public void run() {
                    SaveState save = SaveState.load(first.getGeneralSaveKey());
                    if (save != null) {
                        save.start();
                    } else {
                        SndLog.error("failed to load save for " + first.getGeneralSaveKey(), null);
                    }
                }
            };
            b.addItem(ControlId.structural(CompositeKey.of("card", "continue")), vt);
        }
    }

    private static String startLabel(ContextConfig cc) {
        if (cc instanceof DifficultyConfig) {
            return ((DifficultyConfig) cc).getDifficulty().name();
        }
        return "Start";
    }

    private NodeVtable startButton(final ContextConfig cc) {
        final boolean locked = cc.isLocked();
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return Loc.get("ui", "title.start", "name", GameText.t(startLabel(cc)));
                    }
                }, AnnouncementKinds.LABEL),
                // The record the sighted UI hides behind a right-click wreath.
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        int wins = cc.getWins();
                        int losses = cc.getLosses();
                        if (wins == 0 && losses == 0) {
                            return null;
                        }
                        String text = Loc.get("ui", "title.record",
                                "wins", wins, "runs", wins + losses);
                        int streak = cc.getStreak(false);
                        if (streak > 0) {
                            text += ", " + Loc.get("ui", "title.streak", "streak", streak);
                        }
                        return text;
                    }
                }, AnnouncementKinds.VALUE),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return locked ? Loc.get("ui", "state.locked") : null;
                    }
                }, AnnouncementKinds.ENABLED));
        if (!locked) {
            vt.onActivate = new Runnable() {
                @Override
                public void run() {
                    GameStart.startWithPLTChoice(cc, null, false);
                }
            };
        }
        // The difficulty's rules (the game shows these only in the almanac
        // glossary) on the tooltip key.
        if (cc instanceof DifficultyConfig) {
            final DifficultyConfig dc = (DifficultyConfig) cc;
            vt.onTooltip = new Runnable() {
                @Override
                public void run() {
                    host.speech().speak(GameText.t(dc.getDifficulty().getRules()), false);
                }
            };
        }
        return vt;
    }

    // ---- stop 3: system buttons (the icon-only left cluster, with names) ----

    private void buildSystemStop(GraphBuilder b) {
        b.beginStop("system").pushContext(Loc.get("ui", "sys.context"));
        b.addItem(ControlId.structural(CompositeKey.of("sys", "menu")),
                systemButton(Loc.get("ui", "sys.menu"), new Runnable() {
                    @Override
                    public void run() {
                        DungeonUtils.showCogMenu();
                    }
                }));
        b.addItem(ControlId.structural(CompositeKey.of("sys", "almanac")),
                systemButton(Loc.get("ui", "sys.almanac"), new Runnable() {
                    @Override
                    public void run() {
                        Book.openBook(true);
                    }
                }));
        b.addItem(ControlId.structural(CompositeKey.of("sys", "language")), languageChooser());
        // The conditional members of the game's icon cluster.
        if (com.tann.dice.Main.getSettings().isBypass()) {
            b.addItem(ControlId.structural(CompositeKey.of("sys", "bypass")),
                    systemButton(Loc.get("ui", "sys.bypass"), new Runnable() {
                        @Override
                        public void run() {
                            GameUi.activate(com.tann.dice.gameplay.save.settings.option.OptionUtils
                                    .makeLockButton());
                        }
                    }));
        }
        if (com.tann.dice.gameplay.save.settings.option.OptionLib.SEARCH_BUTT.c()) {
            b.addItem(ControlId.structural(CompositeKey.of("sys", "search")),
                    systemButton(Loc.get("ui", "sys.search"), new Runnable() {
                        @Override
                        public void run() {
                            com.tann.dice.screens.dungeon.panels.book.page.stuffPage.APIUtils.showSearch();
                        }
                    }));
        }
        b.popContext();
    }

    // The game's globe button pushes a chooser; a left/right cycle over the
    // same option is directer for keyboard use. Changing it reloads the
    // translator and rebuilds the stage; our screens rebuild with it.
    private NodeVtable languageChooser() {
        final com.tann.dice.gameplay.save.settings.option.ChOption lang =
                com.tann.dice.gameplay.save.settings.option.OptionLib.LANGUAGE;
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.CHOOSER;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return Loc.get("ui", "sys.language");
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return lang.getOptions()[lang.c()];
                    }
                }, AnnouncementKinds.VALUE));
        vt.onAdjust = new NodeVtable.Adjust() {
            @Override
            public void adjust(int sign, boolean large) {
                String[] options = lang.getOptions();
                int next = ((lang.c() + sign) % options.length + options.length) % options.length;
                lang.setValue(next, true);
            }
        };
        vt.stateText = new Supplier<String>() {
            @Override
            public String get() {
                return lang.getOptions()[lang.c()];
            }
        };
        return vt;
    }

    private static NodeVtable systemButton(final String label, Runnable action) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return label;
            }
        }, AnnouncementKinds.LABEL));
        vt.onActivate = action;
        return vt;
    }
}
