package snd.module.screens;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.Array;
import com.tann.dice.gameplay.leaderboard.Leaderboard;
import com.tann.dice.gameplay.leaderboard.LeaderboardDisplay;
import com.tann.dice.gameplay.leaderboard.LeaderboardDisplaySettings;
import com.tann.dice.gameplay.leaderboard.LeaderboardEntry;
import com.tann.dice.util.Tann;
import com.tann.dice.util.ui.standardButton.StandardButton;

import snd.contracts.SndLog;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.GraphSheet;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.module.GameText;

/**
 * A leaderboard's table, graph-native: the visual board is five independent
 * parallel columns associated only by vertical position, so the walk would
 * read all ranks, then all names, then all scores. Rebuilt as a GraphSheet
 * region instead — rows navigate Up/Down with the column preserved, cells
 * Left/Right with the destination column's header spoken, and your own row is
 * marked. While loading or failed the display's own state text reads
 * verbatim; the paging buttons keep working as buttons.
 */
final class LeaderboardNodes {
    private LeaderboardNodes() {
    }

    static void emit(GraphBuilder b, LeaderboardDisplay display) {
        Leaderboard board = boardOf(display);
        if (board == null) {
            ActorNodes.emitChildren(b, display);
            return;
        }
        Array<LeaderboardEntry> entries = board.getEntries();
        if (entries == null) {
            // Loading / failed: the display's own text says which. A page
            // turn keeps its row, so focus stays on the button pressed.
            ActorNodes.emitChildren(b, display);
            paging(b, display, board);
            return;
        }

        b.addLabel(snd.core.graph.ControlId.referenced(display, "lb-title"),
                new Supplier<String>() {
                    @Override
                    public String get() {
                        StringBuilder sb = new StringBuilder(GameText.t(boardOf(display).getColouredName()));
                        String description = boardOf(display).getDescription();
                        if (description != null) {
                            sb.append(", ").append(GameText.t(description));
                        }
                        return sb.toString();
                    }
                });

        // The line under the title until your score qualifies
        // (LeaderboardDisplay.layout): the score needed, and yours if any.
        if (!board.disableSubmit() && !board.isScoreHighEnough(board.getScore())) {
            b.addLabel(snd.core.graph.ControlId.structural(snd.core.graph.CompositeKey.of("lb-qualify")),
                    new Supplier<String>() {
                        @Override
                        public String get() {
                            Leaderboard lb = boardOf(display);
                            StringBuilder sb = new StringBuilder(GameText.t("Qualifying score"))
                                    .append(": ").append(lb.getRequiredScoreString());
                            if (lb.getScore() != 0) {
                                sb.append(" (").append(GameText.t("your score")).append(": ")
                                        .append(lb.getScoreString(lb.getScore())).append(")");
                            }
                            return sb.toString();
                        }
                    });
        }

        long myId = com.tann.dice.Main.getSettings().getHighscoreIdentifier();
        GraphSheet sheet = new GraphSheet(b, "lb");
        sheet.region(GameText.t(board.getName()), new String[]{
                GameText.t(board.getScoreName()),
                GameText.t("submitted"),
                GameText.t("platform")});
        for (int i = 0; i < entries.size; i++) {
            final LeaderboardEntry entry = entries.get(i);
            final Leaderboard rowBoard = board;
            final boolean mine = entryLong(entry, "author_identifier") == myId;
            NodeVtable primary = new NodeVtable();
            primary.controlType = ControlTypes.TEXT;
            primary.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            String author = entryString(entry, "author");
                            if (author == null || author.isEmpty()) {
                                author = "BLANK_NAME"; // the display's own fallback
                            }
                            return "#" + entryInt(entry, "position") + " "
                                    + com.tann.dice.util.ui.TextWriter.stripTags(author);
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return mine ? Loc.get("ui", "board.you") : null;
                        }
                    }, AnnouncementKinds.VALUE));
            sheet.row(primary, entry,
                    new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t(rowBoard.getScoreString(entryInt(entry, "score")));
                        }
                    },
                    new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t(Tann.getTimeDescription(entryString(entry, "submitted_time")));
                        }
                    },
                    new Supplier<String>() {
                        @Override
                        public String get() {
                            String platform = entryString(entry, "platform");
                            return platform != null ? platform : "?";
                        }
                    });
        }
        sheet.finish();

        // The page row and any buttons beneath the table.
        paging(b, display, board);
        emitButtons(b, display);
    }

    // LeaderboardDisplaySettings.makeActor: a paged board draws "<", "page N"
    // and ">" under the table once there is more than one page to show; the
    // arrows are bordered text whose click listener turns the page, and one
    // that leads nowhere is drawn grey with no listener.
    private static void paging(GraphBuilder b, final LeaderboardDisplay display, Leaderboard board) {
        final LeaderboardDisplaySettings settings = (LeaderboardDisplaySettings) snd.module.Captured.field(display,
                LeaderboardDisplay.class, "displaySettings");
        if (settings == null || settings.type != LeaderboardDisplaySettings.LeaderboardDisplaySettingsType.Page) {
            return;
        }
        boolean loading = board.getEntries() == null;
        boolean full = !loading && board.getEntries().size == 10;
        if (settings.arg == 0L && !full && !loading) {
            return;
        }
        b.startRow();
        pageButton(b, display, settings, -1, "board.prev_page");
        pageButton(b, display, settings, 1, "board.next_page");
        b.endRow();
    }

    // The page shown ("page N", drawn between the arrows) and whether the
    // arrow leads anywhere are live: a turn says the page it goes to at once,
    // and the end it reaches once the page has loaded.
    private static void pageButton(GraphBuilder b, final LeaderboardDisplay display,
            final LeaderboardDisplaySettings settings, final int dir, final String labelKey) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return Loc.get("ui", labelKey);
                    }
                }, AnnouncementKinds.LABEL),
                new NodeAnnouncement(new Supplier<String>() {
                    @Override
                    public String get() {
                        return Loc.get("ui", "board.page", "n", settings.arg + 1);
                    }
                }, true, AnnouncementKinds.VALUE),
                new NodeAnnouncement(new Supplier<String>() {
                    @Override
                    public String get() {
                        // While a page loads neither arrow is drawn; that is no end.
                        Leaderboard board = boardOf(display);
                        boolean loaded = board != null && board.getEntries() != null;
                        return loaded && pageArrow(display, dir) == null ? Loc.get("ui", "state.unavailable") : null;
                    }
                }, true, AnnouncementKinds.STATE));
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                Actor arrow = pageArrow(display, dir);
                if (arrow != null) {
                    snd.module.GameUi.activate(arrow);
                }
            }
        };
        b.addItem(snd.core.graph.ControlId.structural(snd.core.graph.CompositeKey.of("lb-page", dir)), vt);
    }

    // The arrow whose listener turns the page by dir, or null where the game
    // drew it without one.
    private static Actor pageArrow(Actor actor, int dir) {
        com.tann.dice.util.listener.TannListener turn = snd.module.Captured.listenerBuiltBy(actor,
                LeaderboardDisplaySettings.class, "makeActor");
        if (turn != null && Integer.valueOf(dir).equals(snd.module.Captured.primitive(turn, int.class))) {
            return actor;
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                Actor found = pageArrow(child, dir);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void emitButtons(GraphBuilder b, Group group) {
        for (Actor child : group.getChildren()) {
            if (child instanceof StandardButton) {
                b.addItem(ActorNodes.actorId(child), ActorNodes.buttonFor(child));
            } else if (child instanceof Group) {
                emitButtons(b, (Group) child);
            }
        }
    }

    private static Field boardField;

    static Leaderboard boardOf(LeaderboardDisplay display) {
        try {
            if (boardField == null) {
                boardField = LeaderboardDisplay.class.getDeclaredField("leaderboard");
                boardField.setAccessible(true);
            }
            return (Leaderboard) boardField.get(display);
        } catch (Throwable t) {
            SndLog.error("failed to read LeaderboardDisplay.leaderboard", t);
            return null;
        }
    }

    // LeaderboardEntry's fields are all package-private.
    private static final java.util.Map<String, Field> entryFields =
            new java.util.HashMap<String, Field>();

    private static Field entryField(String name) throws Exception {
        Field field = entryFields.get(name);
        if (field == null) {
            field = LeaderboardEntry.class.getDeclaredField(name);
            field.setAccessible(true);
            entryFields.put(name, field);
        }
        return field;
    }

    private static String entryString(LeaderboardEntry entry, String name) {
        try {
            return (String) entryField(name).get(entry);
        } catch (Throwable t) {
            SndLog.error("leaderboard entry read failed: " + name, t);
            return null;
        }
    }

    private static int entryInt(LeaderboardEntry entry, String name) {
        try {
            return entryField(name).getInt(entry);
        } catch (Throwable t) {
            SndLog.error("leaderboard entry read failed: " + name, t);
            return 0;
        }
    }

    private static long entryLong(LeaderboardEntry entry, String name) {
        try {
            return entryField(name).getLong(entry);
        } catch (Throwable t) {
            SndLog.error("leaderboard entry read failed: " + name, t);
            return 0L;
        }
    }
}
