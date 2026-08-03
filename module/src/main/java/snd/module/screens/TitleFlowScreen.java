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
        } else if (mode instanceof com.tann.dice.gameplay.mode.general.nightmare.NightmareMode) {
            // Nightmare replaces the start buttons with its run picker.
            buildNightmareCard(b, (com.tann.dice.gameplay.mode.general.nightmare.NightmareMode) mode);
        } else if (mode instanceof com.tann.dice.gameplay.mode.creative.pastey.PasteMode) {
            // Paste replaces them with clipboard actions + stored scenarios.
            buildPasteCard(b, (com.tann.dice.gameplay.mode.creative.pastey.PasteMode) mode);
        } else {
            if (mode instanceof com.tann.dice.gameplay.mode.chooseParty.ChoosePartyMode) {
                buildChoosePartySelectors(b, (com.tann.dice.gameplay.mode.chooseParty.ChoosePartyMode) mode);
            }
            buildStartButtons(b, mode);
        }
        b.popContext();
    }

    // ---- Choose-Party: the five portrait slots + the reroll die ----

    private static java.lang.reflect.Field selectorsField;
    private static java.lang.reflect.Method refreshHeroesMethod;

    @SuppressWarnings("unchecked")
    private void buildChoosePartySelectors(GraphBuilder b,
            final com.tann.dice.gameplay.mode.chooseParty.ChoosePartyMode mode) {
        List<com.tann.dice.gameplay.mode.chooseParty.HeroSelector> selectors;
        try {
            if (selectorsField == null) {
                selectorsField = com.tann.dice.gameplay.mode.chooseParty.ChoosePartyMode.class
                        .getDeclaredField("selectors");
                selectorsField.setAccessible(true);
            }
            selectors = (List<com.tann.dice.gameplay.mode.chooseParty.HeroSelector>) selectorsField.get(mode);
        } catch (Throwable t) {
            SndLog.error("failed to read ChoosePartyMode.selectors", t);
            return;
        }
        if (selectors == null || selectors.isEmpty()) {
            return; // the card hasn't built its selector actors yet
        }

        final List<com.tann.dice.gameplay.mode.chooseParty.HeroSelector> live = selectors;
        b.startRow("party-slots");
        for (int i = 0; i < live.size(); i++) {
            final com.tann.dice.gameplay.mode.chooseParty.HeroSelector selector = live.get(i);
            final int slot = i;
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.BUTTON;
            vt.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return Loc.get("ui", "party.slot", "n", slot + 1);
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t(selector.getType().getName(true));
                        }
                    }, AnnouncementKinds.VALUE));
            // The slot's own listeners: click opens the picker grid (a pushed
            // modal of named hero tiles), right-click the die panel.
            vt.onActivate = new Runnable() {
                @Override
                public void run() {
                    GameUi.activate(selector);
                }
            };
            vt.onSecondary = new Runnable() {
                @Override
                public void run() {
                    GameUi.info(selector);
                }
            };
            b.addItem(ControlId.referenced(selector, CompositeKey.of("party-slot", i)), vt);
        }
        b.endRow();

        NodeVtable reroll = new NodeVtable();
        reroll.controlType = ControlTypes.BUTTON;
        reroll.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", "party.reroll");
            }
        }, AnnouncementKinds.LABEL));
        reroll.onActivate = new Runnable() {
            @Override
            public void run() {
                // The reroll die's listener body (the actor itself is built
                // inline with no field to reach).
                try {
                    com.tann.dice.statics.sound.Sounds.playSound(com.tann.dice.statics.sound.Sounds.clacks);
                    for (com.tann.dice.gameplay.mode.chooseParty.HeroSelector hs : live) {
                        hs.setToRandomHeroType();
                    }
                    if (refreshHeroesMethod == null) {
                        refreshHeroesMethod = com.tann.dice.gameplay.mode.chooseParty.ChoosePartyMode.class
                                .getDeclaredMethod("refreshHeroes");
                        refreshHeroesMethod.setAccessible(true);
                    }
                    refreshHeroesMethod.invoke(mode);
                } catch (Throwable t) {
                    SndLog.error("party reroll failed", t);
                }
            }
        };
        reroll.stateText = new Supplier<String>() {
            @Override
            public String get() {
                StringBuilder sb = new StringBuilder();
                for (com.tann.dice.gameplay.mode.chooseParty.HeroSelector hs : live) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(GameText.t(hs.getType().getName(true)));
                }
                return sb.toString();
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("card", "party-reroll")), reroll);
    }

    // ---- Nightmare: the past-victory run picker ----

    private static java.lang.reflect.Method showSelectionDialogMethod;

    private void buildNightmareCard(GraphBuilder b,
            final com.tann.dice.gameplay.mode.general.nightmare.NightmareMode mode) {
        NodeVtable choose = new NodeVtable();
        choose.controlType = ControlTypes.BUTTON;
        choose.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return GameText.t("Choose Party");
            }
        }, AnnouncementKinds.LABEL));
        choose.onActivate = new Runnable() {
            @Override
            public void run() {
                try {
                    if (showSelectionDialogMethod == null) {
                        showSelectionDialogMethod =
                                com.tann.dice.gameplay.mode.general.nightmare.NightmareMode.class
                                        .getDeclaredMethod("showSelectionDialog");
                        showSelectionDialogMethod.setAccessible(true);
                    }
                    showSelectionDialogMethod.invoke(mode); // the picker: a pushed modal
                } catch (Throwable t) {
                    SndLog.error("nightmare picker failed", t);
                }
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("card", "nightmare-pick")), choose);
        buildContinue(b, mode);
    }

    // ---- Paste: clipboard actions + the stored-scenario list ----

    private void buildPasteCard(GraphBuilder b,
            final com.tann.dice.gameplay.mode.creative.pastey.PasteMode mode) {
        b.addItem(ControlId.structural(CompositeKey.of("card", "paste-go")),
                cardButtonByText("Paste!"));
        b.addItem(ControlId.structural(CompositeKey.of("card", "paste-store")),
                cardButtonByText("Store"));

        List<com.tann.dice.gameplay.mode.creative.pastey.Scenario> scenarios =
                com.tann.dice.Main.getSettings().getScenarios();
        for (int i = 0; i < scenarios.size(); i++) {
            final com.tann.dice.gameplay.mode.creative.pastey.Scenario scenario = scenarios.get(i);
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.BUTTON;
            vt.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return scenario.getTitle();
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return Loc.get("ui", "paste.scenario");
                        }
                    }, AnnouncementKinds.VALUE));
            // Play: the same public entry the row's listener calls. Delete
            // (with its confirm dialog) lives on the row's info listener.
            vt.onActivate = new Runnable() {
                @Override
                public void run() {
                    com.tann.dice.gameplay.mode.creative.pastey.PasteMode.attemptToStartFromString(
                            scenario.getContent(),
                            new com.tann.dice.gameplay.mode.creative.pastey.PasteConfig());
                }
            };
            vt.onSecondary = new Runnable() {
                @Override
                public void run() {
                    com.tann.dice.util.ui.standardButton.StandardButton button =
                            findCardButton(scenario.getTitle());
                    if (button == null) {
                        host.speech().speak(Loc.get("ui", "title.unavailable"), true);
                        return;
                    }
                    GameUi.info(button); // the game's delete-confirm dialog
                }
            };
            b.addItem(ControlId.referenced(scenario, CompositeKey.of("card", "scenario", i)), vt);
        }
        buildContinue(b, mode);
    }

    // A card button the game builds inline (no field): found by its visible
    // text, activated through its own listener.
    private NodeVtable cardButtonByText(final String text) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return GameText.t(text);
            }
        }, AnnouncementKinds.LABEL));
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                com.tann.dice.util.ui.standardButton.StandardButton button = findCardButton(text);
                if (button == null) {
                    SndLog.error("card button not found: " + text, null);
                    host.speech().speak(Loc.get("ui", "title.unavailable"), true);
                    return;
                }
                GameUi.activate(button);
            }
        };
        return vt;
    }

    private static com.tann.dice.util.ui.standardButton.StandardButton findCardButton(String cleanText) {
        com.tann.dice.screens.Screen screen = com.tann.dice.Main.getCurrentScreen();
        if (!(screen instanceof TitleScreen)) {
            return null;
        }
        return GameUi.findButtonByText((TitleScreen) screen, cleanText);
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
        buildContinue(b, mode);
    }

    // The mode's single autosave slot: one Continue node when it exists.
    private void buildContinue(GraphBuilder b, Mode mode) {
        List<ContextConfig> configs;
        try {
            configs = mode.getConfigs();
        } catch (Throwable t) {
            SndLog.error("getConfigs failed for " + mode.getName(), t);
            return;
        }
        if (configs.isEmpty() || !configs.get(0).hasSave()) {
            return;
        }
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
