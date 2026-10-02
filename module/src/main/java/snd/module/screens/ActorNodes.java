package snd.module.screens;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.tann.dice.util.ui.TextWriter;
import com.tann.dice.util.ui.standardButton.StandardButton;

import snd.core.loc.Loc;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.contracts.speech.TextFilter;
import snd.module.GameText;
import snd.module.GameUi;

/**
 * Turns a game actor tree into graph nodes, in child (visual) order:
 * interactive actors (StandardButton, or anything carrying a TannListener)
 * become buttons labeled by their own text; plain TextWriters become readable
 * lines. Serves both the pushed-modal reader and the dialog-phase screens —
 * the game builds its dialogs the same way in both places.
 */
final class ActorNodes {
    private ActorNodes() {
    }

    // Stable per-actor-instance identity that prints cleanly (game groups
    // override toString with whole-tree dumps, so the actor can't be its own
    // structural key).
    static ControlId actorId(Actor actor) {
        return ControlId.referenced(actor, System.identityHashCode(actor));
    }

    // Walk in child order (Pixl layouts add in reading order). The INNERMOST
    // interactive actor wins: containers often carry their own gesture
    // listeners (self-pop, drag), so an actor only becomes a button when
    // nothing beneath it is interactive — its TextWriters are then its label.
    // A plain TextWriter becomes a readable line.
    static void emit(GraphBuilder b, Actor actor) {
        emit(b, actor, Place.PLAIN);
    }

    /**
     * What the place being read says of its own conventions. The screen that
     * hands an actor to the walk knows where it is (the cog menu by its
     * CogTag, an almanac tab by its identifier); the walk does not, and what
     * holds in one place is applied in no other.
     */
    static final class Place {
        static final Place PLAIN = new Place(java.util.Collections.<String, String>emptyMap(), null, false, false);

        /** Shorthand captions, whole caption to the ui key naming it ("fs" is "fullscreen" in the cog menu). */
        final java.util.Map<String, String> glyphs;
        /**
         * Which of a row of plain buttons is the chosen one, read from what
         * the buttons and the page hold, where the place keeps that nowhere
         * else. Null: no button here is chosen.
         */
        final java.util.function.Predicate<Actor> chosenBy;
        /** Each titled section is a Tab-stop of its own, and so is what lies between two of them. */
        final boolean sectionStops;
        /**
         * The place draws a die net with letters laid over its faces (the
         * help page's Dice section, HelpPage.makeDicePositionExplain): the
         * letters mark positions for the eye, and the legend beside the net
         * says the same in words.
         */
        final boolean dieNetLetters;

        private Place(java.util.Map<String, String> glyphs, java.util.function.Predicate<Actor> chosenBy,
                boolean sectionStops, boolean dieNetLetters) {
            this.glyphs = glyphs;
            this.chosenBy = chosenBy;
            this.sectionStops = sectionStops;
            this.dieNetLetters = dieNetLetters;
        }

        Place withSectionStops() {
            return new Place(glyphs, chosenBy, true, dieNetLetters);
        }

        static Place glyphs(java.util.Map<String, String> glyphs) {
            return new Place(glyphs, null, false, false);
        }

        static Place chosenBy(java.util.function.Predicate<Actor> test) {
            return new Place(java.util.Collections.<String, String>emptyMap(), test, false, false);
        }

        static Place dieNetLetters() {
            return new Place(java.util.Collections.<String, String>emptyMap(), null, false, true);
        }
    }

    static void emit(GraphBuilder b, Actor actor, Place place) {
        if (actor == null || !actor.isVisible()) {
            return;
        }
        if (actor instanceof com.tann.dice.util.Slider) {
            b.addItem(actorId(actor), sliderFor((com.tann.dice.util.Slider) actor));
            return;
        }
        if (actor instanceof com.tann.dice.gameplay.leaderboard.LeaderboardDisplay) {
            // Five parallel columns re-paired as a table (GraphSheet).
            LeaderboardNodes.emit(b, (com.tann.dice.gameplay.leaderboard.LeaderboardDisplay) actor);
            return;
        }
        if (actor instanceof com.tann.dice.screens.dungeon.panels.Explanel.EntPanelInventory) {
            // A unit's panel, its parts read from the unit.
            EntPanelNodes.emit(b, (com.tann.dice.screens.dungeon.panels.Explanel.EntPanelInventory) actor);
            return;
        }
        com.tann.dice.gameplay.save.settings.option.Option option = optionOf(actor);
        if (option != null) {
            b.addItem(ControlId.referenced(option, CompositeKey.of("option", option.getName())),
                    optionNode(actor, option, place));
            return;
        }
        if (interactiveLeaf(actor)) {
            b.addItem(actorId(actor), buttonFor(actor, place));
            return;
        }
        if (actor instanceof TextWriter) {
            final TextWriter tw = (TextWriter) actor;
            // Nothing to say once the markup is gone: a keyword's almanac
            // page adds its "extra rules" line even when there are none, a
            // bare colour tag, which read as an empty control ("", 3 of 10).
            if (TextFilter.clean(tw.text).isEmpty()) {
                return;
            }
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.TEXT;
            vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return tw.text;
                }
            }, AnnouncementKinds.LABEL));
            b.addItem(actorId(actor), vt);
            return;
        }
        if (actor instanceof Group) {
            Group group = (Group) actor;
            String section = sectionTitle(group);
            if (section != null) {
                if (place.sectionStops) {
                    b.beginStop(CompositeKey.of("section", section));
                }
                b.pushContext(section, Loc.get("ui", "role.group"));
                emit(b, group.getChild(0), place);
                b.popContext();
                if (place.sectionStops) {
                    b.beginStop(CompositeKey.of("after-section", section));
                }
                return;
            }
            boolean dieNet = place.dieNetLetters && isDieNetDiagram(group);
            for (Actor child : group.getChildren()) {
                // The letters over the net's six faces mean something only to
                // the eye; the legend says them in words ("L: leftmost").
                if (dieNet && child instanceof TextWriter) {
                    continue;
                }
                emit(b, child, place);
            }
        }
    }

    // The net itself: a group drawn on one of the game's unfolded-die
    // templates (SpecificSidesType.templateImage).
    private static boolean isDieNetDiagram(Group group) {
        for (Actor child : group.getChildren()) {
            if (!(child instanceof com.tann.dice.util.ImageActor)) {
                continue;
            }
            com.badlogic.gdx.graphics.g2d.TextureRegion drawn = ((com.tann.dice.util.ImageActor) child).tr;
            for (com.tann.dice.gameplay.trigger.personal.affectSideModular.condition.SpecificSidesType type
                    : com.tann.dice.gameplay.trigger.personal.affectSideModular.condition.SpecificSidesType.values()) {
                if (type.templateImage == drawn) {
                    return true;
                }
            }
        }
        return false;
    }

    // DipPanel.makeTopPanelGroup — the game's titled-section idiom (the cog
    // menu's "screen mode"/display/sound panels, the jukebox page's options):
    // two bordered Pixl wrappers, body first, title floated over the body's
    // top edge. The title names a context around the body's controls. A panel
    // whose title is itself interactive (the almanac icon) or unnameable (an
    // unknown icon) keeps the plain walk.
    private static String sectionTitle(Group group) {
        if (group.getChildren().size != 2) {
            return null;
        }
        Actor bodyWrap = group.getChild(0);
        Actor titleWrap = group.getChild(1);
        if (!(bodyWrap instanceof Group) || !(titleWrap instanceof Group)
                || titleWrap.getY() <= bodyWrap.getY()) {
            return null;
        }
        Group bw = (Group) bodyWrap;
        Group tw = (Group) titleWrap;
        if (bw.getChildren().size != 2 || tw.getChildren().size != 2
                || !(bw.getChild(0) instanceof com.tann.dice.util.Rectactor)
                || !(tw.getChild(0) instanceof com.tann.dice.util.Rectactor)) {
            return null;
        }
        Actor title = tw.getChild(1);
        if (GameUi.hasTannListener(title)) {
            return null;
        }
        if (title instanceof TextWriter) {
            String text = ((TextWriter) title).text;
            return text != null && !text.trim().isEmpty() ? text : null;
        }
        return GameUi.iconNameUnder(title);
    }

    // ---- an option's own widgets (Option.makeCogActor, wherever the game
    // puts one: the cog menu, the jukebox, custom mode, the TextMod page):
    // one control read from the option, as the Options tab reads it, pressed
    // through the game's own widgets so warnings and refreshes still run ----

    // A choice option's panel holds it in its info listener
    // (ChOption.makeCogActor); a yes/no option's row holds its check box,
    // whose toggle runnable holds the option (BOption.makeComplexEscMenuActor).
    private static com.tann.dice.gameplay.save.settings.option.Option optionOf(Actor actor) {
        com.tann.dice.gameplay.save.settings.option.ChOption choice =
                snd.module.Captured.byListener(actor, com.tann.dice.gameplay.save.settings.option.ChOption.class);
        if (choice != null) {
            return choice;
        }
        com.tann.dice.util.ui.Checkbox box = snd.module.Captured.byListener(actor, com.tann.dice.util.ui.Checkbox.class);
        return box != null ? snd.module.Captured.value(toggleOf(box),
                com.tann.dice.gameplay.save.settings.option.BOption.class) : null;
    }

    private static NodeVtable optionNode(final Actor actor,
            final com.tann.dice.gameplay.save.settings.option.Option option, final Place place) {
        if (option instanceof com.tann.dice.gameplay.save.settings.option.BOption) {
            return OptionNodes.toggle((com.tann.dice.gameplay.save.settings.option.BOption) option, new Runnable() {
                @Override
                public void run() {
                    GameUi.activate(actor); // the row's listener: its warning, then the toggle
                }
            });
        }
        final com.tann.dice.gameplay.save.settings.option.ChOption choice =
                (com.tann.dice.gameplay.save.settings.option.ChOption) option;
        return OptionNodes.dropdown(choice, new java.util.function.Function<String, String>() {
            @Override
            public String apply(String caption) {
                return glyphName(caption, place.glyphs);
            }
        }, new java.util.function.IntConsumer() {
            @Override
            public void accept(int index) {
                Actor row = choiceRow(actor, choice, index);
                if (row != null) {
                    GameUi.activate(row);
                } else {
                    snd.contracts.SndLog.error("option " + choice.getName() + ": no row for choice " + index, null);
                }
            }
        });
    }

    // The panel's radio row for a choice: the row whose box's toggle runnable
    // holds that choice's index. Searched on a key press only.
    private static Actor choiceRow(Actor actor, com.tann.dice.gameplay.save.settings.option.ChOption choice, int index) {
        com.tann.dice.util.ui.Checkbox box = snd.module.Captured.byListener(actor, com.tann.dice.util.ui.Checkbox.class);
        if (box != null) {
            Runnable toggle = toggleOf(box);
            if (snd.module.Captured.value(toggle, com.tann.dice.gameplay.save.settings.option.ChOption.class) == choice
                    && Integer.valueOf(index).equals(snd.module.Captured.primitive(toggle, int.class))) {
                return actor;
            }
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                Actor row = choiceRow(child, choice, index);
                if (row != null) {
                    return row;
                }
            }
        }
        return null;
    }

    /** Walk an actor's children without re-dispatching on the actor itself. */
    static void emitChildren(GraphBuilder b, Group group) {
        for (Actor child : group.getChildren()) {
            emit(b, child);
        }
    }

    private static boolean interactiveLeaf(Actor actor) {
        if (actor instanceof StandardButton) {
            return true;
        }
        return GameUi.isClickable(actor) && !hasInteractiveDescendant(actor);
    }

    private static boolean hasInteractiveDescendant(Actor actor) {
        if (!(actor instanceof Group)) {
            return false;
        }
        for (Actor child : ((Group) actor).getChildren()) {
            if (child instanceof StandardButton || GameUi.isClickable(child)
                    || hasInteractiveDescendant(child)) {
                return true;
            }
        }
        return false;
    }

    static NodeVtable buttonFor(Actor actor) {
        return buttonFor(actor, Place.PLAIN);
    }

    static NodeVtable buttonFor(final Actor actor, final Place place) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        // A class tile (the Choose-Party picker) concerns its class: the
        // hero buffer and the side keys read it.
        com.tann.dice.gameplay.content.ent.type.HeroType tileClass = heroTypeOf(actor);
        final com.tann.dice.gameplay.content.ent.type.HeroType heroClass = tileClass != null
                && !com.tann.dice.gameplay.progress.chievo.unlock.UnUtil.isLocked(tileClass) ? tileClass : null;
        vt.subject = heroClass;
        // What the game's info popup said when this control was last asked
        // (Enter on an info-only row, Backspace anywhere): read on the spot,
        // kept here to step through. A party-layout card adds the heroes
        // each of its colours can start as.
        vt.details = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                List<String> lines = new java.util.ArrayList<String>(GameUi.infoLines(actor));
                lines.addAll(PartyLayouts.pools(actor));
                return lines;
            }
        };
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        // Model label first: some achievement icons are text
                        // glyphs ("H5") that would win the text search.
                        String label = achievementTileName(actor);
                        if (label == null) {
                            label = PartyLayouts.name(actor); // its "?" squares are text too
                        }
                        if (label == null) {
                            label = nowPlayingLabel(actor);
                        }
                        if (label == null) {
                            label = jukeboxLabel(actor);
                        }
                        if (label == null) {
                            label = glyphName(GameUi.labelOf(actor), place.glyphs);
                        }
                        if (label == null) {
                            label = GameUi.iconNameUnder(actor); // icon-only buttons
                        }
                        if (label == null) {
                            label = sideIconName(actor); // bare die-side tiles
                        }
                        if (label == null && actor instanceof com.tann.dice.screens.dungeon.panels.DieSidePanel) {
                            // Die-net previews in dialogs (level-ups, sheets).
                            label = SideText.of(((com.tann.dice.screens.dungeon.panels.DieSidePanel) actor)
                                    .side.getBaseEffect());
                        }
                        if (label == null && actor instanceof com.tann.dice.screens.dungeon.panels.entPanel.ItemHeroPanel) {
                            // A sheet's item slot: the item's name, or an empty slot.
                            com.tann.dice.gameplay.content.item.Item item =
                                    ((com.tann.dice.screens.dungeon.panels.entPanel.ItemHeroPanel) actor).item;
                            label = item != null ? GameText.t(item.getName())
                                    : Loc.get("ui", "modal.empty_slot");
                        }
                        if (label == null) {
                            label = monsterTileName(actor); // portrait-only ledger tiles
                        }
                        if (label == null) {
                            label = itemTileName(actor);
                        }
                        if (label == null) {
                            label = abilityTileName(actor);
                        }
                        return label != null ? label
                                : Loc.get("ui", "modal.unlabeled", "type", actor.getClass().getSimpleName());
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return heroClass != null ? UnitLines.restHp(heroClass) : null;
                    }
                }, AnnouncementKinds.VALUE),
                // A row of the game's buttons that works as a radio group,
                // in a place that marks the chosen one by colour.
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        boolean chosen = place.chosenBy != null ? place.chosenBy.test(actor) : plottedSide(actor);
                        return chosen ? Loc.get("ui", "state.selected") : null;
                    }
                }, AnnouncementKinds.SELECTED),
                // Checkbox rows (options, jukebox songs): the box's state.
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return checkboxState(actor);
                    }
                }, AnnouncementKinds.VALUE),
                // Item/modifier cards (ConcisePanel) draw their effect text as
                // side views; achievements hide theirs behind right-click.
                // Read both from the model instead.
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        String effect = choosableEffect(actor);
                        if (effect == null) {
                            effect = achievementDescription(actor);
                        }
                        return effect != null ? effect : itemSlotDescription(actor);
                    }
                }, AnnouncementKinds.TOOLTIP),
                // Party-layout picker options: the colours the card draws as
                // squares.
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return PartyLayouts.colours(actor);
                    }
                }, AnnouncementKinds.VALUE));
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                GameUi.activate(actor);
            }
        };
        vt.onSecondary = new Runnable() {
            @Override
            public void run() {
                GameUi.info(actor);
            }
        };
        return vt;
    }

    // The jukebox's currently-playing row: a LiveText (the game's only one)
    // over a progress bar. Name it from the model — the LiveText's own child
    // text is empty until its first act() and lags a frame behind.
    private static String nowPlayingLabel(Actor actor) {
        if (!(actor instanceof Group)) {
            return null;
        }
        for (Actor child : ((Group) actor).getChildren()) {
            if (child instanceof com.tann.dice.util.ui.LiveText) {
                try {
                    return Loc.get("ui", "jukebox.now_playing", "song",
                            ((com.tann.dice.util.ui.LiveText) child).fetchText());
                } catch (Throwable t) {
                    snd.contracts.SndLog.error("now-playing label failed", t);
                    return null;
                }
            }
        }
        return null;
    }

    // ---- the jukebox's songs (JukeboxUtils.makeSong / makeCheckbox /
    // makeMusicianActor): a song row's listener holds its song, a check
    // box's toggle runnable its song or, for the box over a musician's
    // songs, the musician. Named and stated from those, not the three
    // stacked copies of the name the row draws. ----

    private static com.tann.dice.statics.sound.music.MusicData songOf(Actor actor) {
        return snd.module.Captured.byListener(actor, com.tann.dice.statics.sound.music.MusicData.class);
    }

    private static Runnable toggleOf(com.tann.dice.util.ui.Checkbox box) {
        return (Runnable) snd.module.Captured.field(box, com.tann.dice.util.ui.Checkbox.class, "toggleRunnable");
    }

    private static String songName(com.tann.dice.statics.sound.music.MusicData song) {
        return com.tann.dice.statics.sound.music.MusicFormat.getNiceName(song.path);
    }

    private static String jukeboxLabel(Actor actor) {
        if (actor instanceof com.tann.dice.util.ui.Checkbox) {
            Runnable toggle = toggleOf((com.tann.dice.util.ui.Checkbox) actor);
            com.tann.dice.statics.sound.music.MusicData song =
                    snd.module.Captured.value(toggle, com.tann.dice.statics.sound.music.MusicData.class);
            if (song != null) {
                return songName(song);
            }
            com.tann.dice.statics.sound.music.Musician musician =
                    snd.module.Captured.value(toggle, com.tann.dice.statics.sound.music.Musician.class);
            return musician != null ? musician.name : null;
        }
        com.tann.dice.statics.sound.music.MusicData song = songOf(actor);
        if (song == null) {
            return null;
        }
        boolean playing = song == com.tann.dice.statics.sound.music.MusicManager.getCurrentSongData(true)
                && !com.tann.dice.statics.sound.music.MusicManager.isMusicDisabled();
        return playing ? Loc.get("ui", "jukebox.now_playing", "song", songName(song)) : songName(song);
    }

    // Enabled in the jukebox: the song, or all of the musician's songs.
    private static String jukeboxState(Actor actor) {
        if (!(actor instanceof com.tann.dice.util.ui.Checkbox)) {
            return null;
        }
        Runnable toggle = toggleOf((com.tann.dice.util.ui.Checkbox) actor);
        com.tann.dice.gameplay.save.settings.Settings settings = com.tann.dice.Main.getSettings();
        com.tann.dice.statics.sound.music.MusicData song =
                snd.module.Captured.value(toggle, com.tann.dice.statics.sound.music.MusicData.class);
        if (song != null) {
            return Loc.get("ui", settings.isDisabledSong(song) ? "state.unchecked" : "state.checked");
        }
        com.tann.dice.statics.sound.music.Musician musician =
                snd.module.Captured.value(toggle, com.tann.dice.statics.sound.music.Musician.class);
        List<?> songs = musician != null
                ? (List<?>) snd.module.Captured.field(musician, com.tann.dice.statics.sound.music.Musician.class, "songs")
                : null;
        if (songs == null) {
            return null;
        }
        for (Object o : songs) {
            if (settings.isDisabledSong((com.tann.dice.statics.sound.music.MusicData) o)) {
                return Loc.get("ui", "state.unchecked");
            }
        }
        return Loc.get("ui", "state.checked");
    }

    // A caption the place reads as shorthand, named. Whole-caption matches
    // only, so ordinary text never remaps.
    private static String glyphName(String label, java.util.Map<String, String> glyphs) {
        if (label == null) {
            return null;
        }
        String key = glyphs.get(TextFilter.clean(label).trim());
        return key != null ? Loc.get("ui", key) : label;
    }

    private static java.lang.reflect.Field monsterTypeField;
    private static java.lang.reflect.Field heroTypeField;

    // Ledger tiles are portrait-only; their entity type is a field.
    private static String monsterTileName(Actor actor) {
        com.tann.dice.gameplay.content.ent.type.EntType type = monsterTypeOf(actor);
        if (type == null) {
            type = heroTypeOf(actor);
        }
        return type != null ? GameText.t(type.getName(true)) : null;
    }

    static com.tann.dice.gameplay.content.ent.type.MonsterType monsterTypeOf(Actor actor) {
        if (!(actor instanceof com.tann.dice.screens.dungeon.panels.book.views.MonsterLedgerView)) {
            return null;
        }
        try {
            if (monsterTypeField == null) {
                monsterTypeField = com.tann.dice.screens.dungeon.panels.book.views.MonsterLedgerView.class
                        .getDeclaredField("type");
                monsterTypeField.setAccessible(true);
            }
            return (com.tann.dice.gameplay.content.ent.type.MonsterType) monsterTypeField.get(actor);
        } catch (Throwable t) {
            snd.contracts.SndLog.error("monster tile read failed", t);
            return null;
        }
    }

    static com.tann.dice.gameplay.content.ent.type.HeroType heroTypeOf(Actor actor) {
        if (!(actor instanceof com.tann.dice.screens.dungeon.panels.book.views.HeroLedgerView)) {
            return null;
        }
        try {
            if (heroTypeField == null) {
                heroTypeField = com.tann.dice.screens.dungeon.panels.book.views.HeroLedgerView.class
                        .getDeclaredField("h");
                heroTypeField.setAccessible(true);
            }
            return (com.tann.dice.gameplay.content.ent.type.HeroType) heroTypeField.get(actor);
        } catch (Throwable t) {
            snd.contracts.SndLog.error("hero tile read failed", t);
            return null;
        }
    }

    private static java.lang.reflect.Field abilityTileField;

    // A spell or tactic drawn as its round icon alone (the keyword page's
    // abilities that carry the keyword): its title.
    private static String abilityTileName(Actor actor) {
        if (!(actor instanceof com.tann.dice.screens.dungeon.panels.entPanel.AbilityPanel)) {
            return null;
        }
        try {
            if (abilityTileField == null) {
                abilityTileField = com.tann.dice.screens.dungeon.panels.entPanel.AbilityPanel.class
                        .getDeclaredField("ability");
                abilityTileField.setAccessible(true);
            }
            com.tann.dice.gameplay.effect.targetable.ability.Ability ability =
                    (com.tann.dice.gameplay.effect.targetable.ability.Ability) abilityTileField.get(actor);
            return GameText.t(ability.getTitle());
        } catch (Throwable t) {
            snd.contracts.SndLog.error("ability tile read failed", t);
            return null;
        }
    }

    private static java.lang.reflect.Field itemTileField;

    // Item ledger tiles show only the item's art (locked ones a padlock the
    // icon naming already catches).
    private static String itemTileName(Actor actor) {
        com.tann.dice.gameplay.content.item.Item item = itemOf(actor);
        return item != null ? GameText.t(item.getName(true)) : null;
    }

    static com.tann.dice.gameplay.content.item.Item itemOf(Actor actor) {
        if (!(actor instanceof com.tann.dice.screens.dungeon.panels.book.views.ItemLedgerView)) {
            return null;
        }
        try {
            if (itemTileField == null) {
                itemTileField = com.tann.dice.screens.dungeon.panels.book.views.ItemLedgerView.class
                        .getDeclaredField("item");
                itemTileField.setAccessible(true);
            }
            return (com.tann.dice.gameplay.content.item.Item) itemTileField.get(actor);
        } catch (Throwable t) {
            snd.contracts.SndLog.error("item tile read failed", t);
            return null;
        }
    }

    private static java.lang.reflect.Field achievementField;

    private static com.tann.dice.gameplay.progress.chievo.Achievement achievementOf(Actor actor) {
        if (!(actor instanceof com.tann.dice.screens.dungeon.panels.book.page.ledgerPage.AchievementIconView)) {
            return null;
        }
        try {
            if (achievementField == null) {
                achievementField = com.tann.dice.screens.dungeon.panels.book.page.ledgerPage.AchievementIconView.class
                        .getDeclaredField("achievement");
                achievementField.setAccessible(true);
            }
            return (com.tann.dice.gameplay.progress.chievo.Achievement) achievementField.get(actor);
        } catch (Throwable t) {
            snd.contracts.SndLog.error("achievement tile read failed", t);
            return null;
        }
    }

    // Achievement tiles are 18x18 icons whose detail is right-click-only;
    // speak the name, completion state, and description outright.
    private static String achievementTileName(Actor actor) {
        com.tann.dice.gameplay.progress.chievo.Achievement achievement = achievementOf(actor);
        if (achievement == null) {
            return null;
        }
        return GameText.t(achievement.getName()) + ", "
                + Loc.get("ui", achievement.isAchieved() ? "book.achieved" : "book.not_achieved");
    }

    private static String achievementDescription(Actor actor) {
        com.tann.dice.gameplay.progress.chievo.Achievement achievement = achievementOf(actor);
        // The right-click detail's own text, gating rule included.
        return achievement != null ? GameText.t(achievement.getExplanelDescription()) : null;
    }

    // An item slot's description, the same text its right-click panel shows.
    private static String itemSlotDescription(Actor actor) {
        if (!(actor instanceof com.tann.dice.screens.dungeon.panels.entPanel.ItemHeroPanel)) {
            return null;
        }
        com.tann.dice.gameplay.content.item.Item item =
                ((com.tann.dice.screens.dungeon.panels.entPanel.ItemHeroPanel) actor).item;
        return item != null ? GameText.t(item.getDescription()) : null;
    }

    // Bare die-side tiles carry no text: named by the side they hold.
    private static String sideIconName(Actor actor) {
        com.tann.dice.gameplay.content.ent.die.side.EntSide side = sideOf(actor);
        return side != null ? SideText.of(side.getBaseEffect()) : null;
    }

    /**
     * The die side a side tile's listener holds: the Graph tab's plotted
     * series and its add popup (GraphUtils.make), the TextMod tab's side list.
     */
    static com.tann.dice.gameplay.content.ent.die.side.EntSide sideOf(Actor actor) {
        return snd.module.Captured.byListener(actor, com.tann.dice.gameplay.content.ent.die.side.EntSide.class);
    }

    // In the Graph tab's add popup, a tile's listener lives in the "+"
    // button's runnable, which holds the plotted list: a side in it is
    // plotted (GraphUtils.indexOf's test, EntSide.same).
    private static boolean plottedSide(Actor actor) {
        com.tann.dice.gameplay.content.ent.die.side.EntSide side = sideOf(actor);
        if (side == null) {
            return false;
        }
        for (com.badlogic.gdx.scenes.scene2d.EventListener listener : actor.getListeners()) {
            if (!(listener instanceof com.tann.dice.util.listener.TannListener)) {
                continue;
            }
            List<?> plotted = snd.module.Captured.value(snd.module.Captured.value(listener, Runnable.class), List.class);
            if (plotted == null) {
                continue;
            }
            for (Object o : plotted) {
                if (o instanceof com.tann.dice.gameplay.content.ent.die.side.EntSide
                        && ((com.tann.dice.gameplay.content.ent.die.side.EntSide) o).same(side)) {
                    return true;
                }
            }
        }
        return false;
    }

    // A Checkbox draws its own tick state; find one under the row.
    private static String checkboxState(Actor actor) {
        String jukebox = jukeboxState(actor);
        if (jukebox != null) {
            return jukebox;
        }
        // Every check box the game draws is an option's (read as the option)
        // or the jukebox's (above); one that is neither says its own state.
        if (!(actor instanceof com.tann.dice.util.ui.Checkbox)) {
            return null;
        }
        return Loc.get("ui", ((com.tann.dice.util.ui.Checkbox) actor).isOn() ? "state.checked" : "state.unchecked");
    }

    private static java.lang.reflect.Field choosableField;

    private static String choosableEffect(Actor actor) {
        if (!(actor instanceof com.tann.dice.screens.dungeon.panels.entPanel.choosablePanel.ConcisePanel)) {
            return null;
        }
        try {
            if (choosableField == null) {
                choosableField = com.tann.dice.screens.dungeon.panels.entPanel.choosablePanel.ConcisePanel.class
                        .getDeclaredField("choosable");
                choosableField.setAccessible(true);
            }
            Object choosable = choosableField.get(actor);
            if (choosable instanceof com.tann.dice.gameplay.content.item.Item) {
                return GameText.t(((com.tann.dice.gameplay.content.item.Item) choosable).getDescription());
            }
            if (choosable instanceof com.tann.dice.gameplay.modifier.Modifier) {
                return GameText.t(((com.tann.dice.gameplay.modifier.Modifier) choosable).getFullDescription());
            }
            return null;
        } catch (Throwable t) {
            snd.contracts.SndLog.error("choosable panel effect failed", t);
            return null;
        }
    }

    static NodeVtable sliderFor(final com.tann.dice.util.Slider slider) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.SLIDER;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return GameUi.sliderTitle(slider);
                    }
                }, AnnouncementKinds.LABEL),
                new NodeAnnouncement(new Supplier<String>() {
                    @Override
                    public String get() {
                        return Loc.get("ui", "value.percent", "value", GameUi.sliderPercent(slider));
                    }
                }, true, AnnouncementKinds.VALUE));
        vt.onAdjust = new NodeVtable.Adjust() {
            @Override
            public void adjust(int sign, boolean large) {
                GameUi.sliderAdjust(slider, sign, large);
            }
        };
        vt.stateText = new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", "value.percent", "value", GameUi.sliderPercent(slider));
            }
        };
        return vt;
    }
}
