package snd.module.screens;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.tann.dice.gameplay.content.ent.Hero;
import com.tann.dice.gameplay.content.ent.group.PartyLayoutType;
import com.tann.dice.gameplay.content.item.Item;
import com.tann.dice.gameplay.context.DungeonContext;
import com.tann.dice.gameplay.modifier.Modifier;
import com.tann.dice.gameplay.phase.PhaseManager;
import com.tann.dice.gameplay.phase.endPhase.runEnd.RunEndPhase;
import com.tann.dice.gameplay.phase.endPhase.statsPanel.GameEndStatsPanel;
import com.tann.dice.gameplay.progress.stats.stat.Stat;
import com.tann.dice.gameplay.progress.stats.stat.endOfFight.HeroDeath;
import com.tann.dice.gameplay.progress.stats.stat.endRound.DamageTakenStat;
import com.tann.dice.gameplay.progress.stats.stat.endRound.TurnsTakenStat;
import com.tann.dice.gameplay.progress.stats.stat.miscStat.UndoCountStat;
import com.tann.dice.screens.dungeon.DungeonScreen;
import com.tann.dice.util.Tann;

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
import snd.module.GameText;
import snd.module.GameUi;

/**
 * The end-of-run stats panel, read from the model rather than the actors: the
 * visual panel renders its stats as two parallel columns of separate name and
 * value actors (association is purely spatial) and its heroes as portrait
 * tiles, so the generic modal walk would read names and numbers apart. Every
 * line here is rebuilt from the same DungeonContext and stats-map calls the
 * panel itself uses.
 */
public class RunEndStatsScreen extends AccessScreen {
    @Override
    public String key() {
        return "run-end-stats";
    }

    @Override
    public List<KeyOffer> keys() {
        return GameKeys.escapeOnly();
    }

    @Override
    public int layer() {
        return 25; // above the generic modal reader while the panel is top
    }

    @Override
    public boolean isActive() {
        return statsPanel() != null;
    }

    // The pushed actor is the panel, or whatever the game wrapped it in to
    // fit (Tann.makeScrollpaneIfNecessary).
    private static GameEndStatsPanel statsPanel() {
        Actor modal = GameUi.topModal();
        if (modal instanceof GameEndStatsPanel) {
            return (GameEndStatsPanel) modal;
        }
        return modal instanceof Group ? Tann.findByClass((Group) modal, GameEndStatsPanel.class) : null;
    }

    @Override
    public String screenName() {
        com.tann.dice.gameplay.phase.Phase p = PhaseManager.get().getPhase();
        if (p instanceof RunEndPhase) {
            return DialogPhaseScreen.runEndTitle((RunEndPhase) p);
        }
        return Loc.get("ui", "runend.stats");
    }

    @Override
    public void build(GraphBuilder b) {
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null) {
            return;
        }
        final DungeonContext dc = ds.getDungeonContext();

        // The header's second line; the title itself is the screen name.
        final String progress = dc.getLevelProgressString(true);
        if (progress != null) {
            addLine(b, ControlId.structural(CompositeKey.of("stats", "progress")),
                    new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t(progress);
                        }
                    });
        }

        buildModifiers(b, dc);
        buildHeroes(b, dc);
        if (!dc.skipStats()) {
            buildStatLines(b, dc);
        }
        buildPartyLayout(b, dc);
    }

    // The modifier list; the panel shows small panels with right-click detail
    // — here the detail is the value part.
    private void buildModifiers(GraphBuilder b, DungeonContext dc) {
        List<Modifier> modifiers = dc.getCurrentModifiers();
        int index = 0;
        for (final Modifier modifier : modifiers) {
            if (modifier.getName().contains("Hidden")) {
                continue; // the panel hides these too
            }
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.TEXT;
            vt.announcements = java.util.Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t(modifier.getName());
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t(modifier.getFullDescription());
                        }
                    }, AnnouncementKinds.TOOLTIP));
            vt.speaksOwnPosition = true;
            b.addItem(ControlId.referenced(modifier, CompositeKey.of("stats-mod", index)), vt);
            index++;
        }
    }

    // Per-hero row: portrait tile + skull count + equipped items, as words.
    private void buildHeroes(GraphBuilder b, final DungeonContext dc) {
        Map<String, Stat> stats = dc.getStatsManager().getStatsMap();
        List<Hero> heroes = dc.getParty().getHeroes();
        for (int i = 0; i < heroes.size(); i++) {
            final Hero hero = heroes.get(i);
            Stat deaths = stats.get(HeroDeath.getNameFromIndex(i));
            final int deathCount = deaths != null ? deaths.getValue() : 0;
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.TEXT;
            vt.announcements = java.util.Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t(hero.getName(true));
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            StringBuilder sb = new StringBuilder(
                                    Loc.get("ui", "runend.deaths", "n", deathCount));
                            for (Item item : hero.getItems()) {
                                sb.append(", ").append(GameText.t(item.getName(true)));
                            }
                            return sb.toString();
                        }
                    }, AnnouncementKinds.VALUE));
            vt.speaksOwnPosition = true;
            b.addItem(ControlId.referenced(hero, CompositeKey.of("stats-hero", i)), vt);
        }
    }

    // The two TwoCol columns, re-paired: each name with its value.
    private void buildStatLines(GraphBuilder b, DungeonContext dc) {
        Map<String, Stat> stats = dc.getStatsManager().getStatsMap();
        addStatLine(b, "time", GameText.t("Time:"),
                Tann.parseSeconds(dc.getFinalTimeSeconds(), false));
        addStatLine(b, "turns", GameText.t("Turns:"), statValue(stats, TurnsTakenStat.NAME));
        addStatLine(b, "undos", GameText.t("Undos:"), statValue(stats, UndoCountStat.NAME));
        addStatLine(b, "rolls", GameText.t("Rolls:"), statValue(stats, "dice-rolled"));
        addStatLine(b, "crosses", Loc.get("ui", "runend.crosses"), statValue(stats, "crosses-rolled"));
        addStatLine(b, "kills", GameText.t("Kills:"), statValue(stats, "total-kills"));
        addStatLine(b, "damage", GameText.t("Dmg Taken:"), statValue(stats, DamageTakenStat.NAME));
        addStatLine(b, "blocked", GameText.t("Blocked:"), statValue(stats, "total-blocked"));
        addStatLine(b, "healed", GameText.t("Healed:"), statValue(stats, "total-healing"));
        addStatLine(b, "abilities", GameText.t("Abilities:"), statValue(stats, "spells-cast"));
    }

    private static String statValue(Map<String, Stat> stats, String key) {
        Stat stat = stats.get(key);
        if (stat == null) {
            SndLog.error("run-end stat missing: " + key, null);
            return "0";
        }
        return String.valueOf(stat.getValue());
    }

    // A read-only line. The summary is a sheet to read down, not a list to
    // count: no line says its position.
    private static void addLine(GraphBuilder b, ControlId id, Supplier<String> text) {
        NodeVtable vt = new NodeVtable();
        vt.announcements = Arrays.asList(new NodeAnnouncement(text));
        vt.speaksOwnPosition = true;
        b.addItem(id, vt);
    }

    private void addStatLine(GraphBuilder b, Object key, final String label, final String value) {
        addLine(b, ControlId.structural(CompositeKey.of("stats-line", key)),
                new Supplier<String>() {
                    @Override
                    public String get() {
                        return label + " " + value;
                    }
                });
    }

    // The 1-px colour swatch, as the layout's name and colours.
    private void buildPartyLayout(GraphBuilder b, DungeonContext dc) {
        final PartyLayoutType plt = dc.getParty().getPLT();
        if (plt == null) {
            return;
        }
        addLine(b, ControlId.structural(CompositeKey.of("stats", "layout")),
                new Supplier<String>() {
                    @Override
                    public String get() {
                        StringBuilder sb = new StringBuilder(
                                Loc.get("ui", "runend.layout", "name", plt.name()));
                        for (com.tann.dice.gameplay.content.ent.type.HeroCol col : plt.getColsInstance()) {
                            sb.append(", ").append(col == null
                                    ? Loc.get("ui", "value.random") : col.name());
                        }
                        return sb.toString();
                    }
                });
    }
}
