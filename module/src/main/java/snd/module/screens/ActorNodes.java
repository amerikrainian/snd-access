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
        emit(b, actor, NO_GLYPHS);
    }

    static final java.util.Map<String, String> NO_GLYPHS = java.util.Collections.emptyMap();

    /**
     * {@code glyphs}: the shorthand captions of the place being read, whole
     * caption to the ui key that names it ("fs" is "fullscreen" in the cog
     * menu, and nowhere else). The place knows them; the walk does not.
     */
    static void emit(GraphBuilder b, Actor actor, java.util.Map<String, String> glyphs) {
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
        if (interactiveLeaf(actor)) {
            b.addItem(actorId(actor), buttonFor(actor, glyphs));
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
                b.pushContext(section, Loc.get("ui", "role.group"));
                emit(b, group.getChild(0), glyphs);
                b.popContext();
                return;
            }
            boolean dieNet = isDieNetDiagram(group);
            for (Actor child : group.getChildren()) {
                // Text laid over a die-net picture marks POSITIONS on it (the
                // help page's L, M, T, B, r, R on the net's six faces): it
                // means something only to the eye, and the legend beside the
                // picture says the same in words ("L: leftmost").
                if (dieNet && child instanceof TextWriter) {
                    continue;
                }
                emit(b, child, glyphs);
            }
        }
    }

    // A group drawn on one of the game's unfolded-die templates
    // (SpecificSidesType.templateImage): HelpPage.makeDicePositionExplain's
    // labelled net, CopySide's side-swap pictures.
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
        return buttonFor(actor, NO_GLYPHS);
    }

    static NodeVtable buttonFor(final Actor actor, final java.util.Map<String, String> glyphs) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        // What the game's info popup said when this control was last asked
        // (Enter on an info-only row, Backspace anywhere): read on the spot,
        // kept here to step through.
        vt.details = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                List<String> lines = new java.util.ArrayList<String>(GameUi.infoLines(actor));
                lines.addAll(GameUi.infoLines(infoTarget(actor)));
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
                            label = partyLayoutName(actor); // its "?" squares are text too
                        }
                        if (label == null) {
                            label = nowPlayingLabel(actor);
                        }
                        if (label == null) {
                            label = glyphName(GameUi.labelOf(actor), glyphs);
                        }
                        if (label == null) {
                            label = GameUi.iconNameUnder(actor); // icon-only buttons
                        }
                        if (label == null) {
                            label = sideIconName(actor); // bare die-side images
                        }
                        if (label == null && actor instanceof com.tann.dice.screens.dungeon.panels.DieSidePanel) {
                            // Die-net previews in dialogs (level-ups, sheets).
                            label = GameText.t(((com.tann.dice.screens.dungeon.panels.DieSidePanel) actor)
                                    .side.getBaseEffect().describe());
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
                // A row of the game's buttons that works as a radio group:
                // the chosen one is drawn with a light border.
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return chosenAmongButtons(actor) ? Loc.get("ui", "state.selected") : null;
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
                        return partyLayoutColours(actor);
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
                GameUi.info(infoTarget(actor));
            }
        };
        return vt;
    }

    // A ChOption radio row's own info listener is a dead end — the game builds
    // it with null extra text yet it reports the click handled. The option's
    // real description listens on the enclosing panel, so start the bubble
    // above the row.
    private static Actor infoTarget(Actor actor) {
        return findCheckbox(actor) instanceof com.tann.dice.util.ui.RadioCheckbox
                && actor.getParent() != null ? actor.getParent() : actor;
    }

    // A party-layout option ("Choose party layout", GameStart) is an anonymous
    // card: a name over five squares, coloured, or a "?" for a slot filled at
    // random when the run starts. The layout it starts is the one its click
    // listener holds.
    private static String partyLayoutName(Actor actor) {
        com.tann.dice.gameplay.content.ent.group.PartyLayoutType layout = partyLayoutOf(actor);
        if (layout == null) {
            return null;
        }
        String name = GameText.t(layout.name());
        // PartyLayoutType.addRarityIfNecessary
        if (com.tann.dice.gameplay.save.settings.option.OptionLib.SHOW_RARITY.c()
                && layout.getChance() != com.tann.dice.gameplay.trigger.global.chance.RarityUtils.IGNORED_RARITY) {
            name += ", r: " + layout.getChance();
        }
        return name;
    }

    private static java.lang.reflect.Field layoutColsField;

    private static String partyLayoutColours(Actor actor) {
        com.tann.dice.gameplay.content.ent.group.PartyLayoutType layout = partyLayoutOf(actor);
        if (layout == null) {
            return null;
        }
        try {
            if (layoutColsField == null) {
                layoutColsField = com.tann.dice.gameplay.content.ent.group.PartyLayoutType.class
                        .getDeclaredField("cols");
                layoutColsField.setAccessible(true);
            }
            StringBuilder sb = new StringBuilder();
            for (com.tann.dice.gameplay.content.ent.type.HeroCol col
                    : (com.tann.dice.gameplay.content.ent.type.HeroCol[]) layoutColsField.get(layout)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(col == null ? Loc.get("ui", "value.random") : GameText.t(col.colName));
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Throwable t) {
            snd.contracts.SndLog.error("party layout colours read failed", t);
            return null;
        }
    }

    private static com.tann.dice.gameplay.content.ent.group.PartyLayoutType partyLayoutOf(Actor actor) {
        for (com.badlogic.gdx.scenes.scene2d.EventListener listener : actor.getListeners()) {
            if (!(listener instanceof com.tann.dice.util.listener.TannListener)) {
                continue;
            }
            for (java.lang.reflect.Field field : listener.getClass().getDeclaredFields()) {
                if (field.getType() != com.tann.dice.gameplay.content.ent.group.PartyLayoutType.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    return (com.tann.dice.gameplay.content.ent.group.PartyLayoutType) field.get(listener);
                } catch (Throwable t) {
                    snd.contracts.SndLog.error("party layout read failed", t);
                    return null;
                }
            }
        }
        return null;
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

    private static java.lang.reflect.Field buttonColField;

    private static com.badlogic.gdx.graphics.Color buttonCol(Actor button) throws Exception {
        if (buttonColField == null) {
            buttonColField = StandardButton.class.getDeclaredField("col");
            buttonColField.setAccessible(true);
        }
        return (com.badlogic.gdx.graphics.Color) buttonColField.get(button);
    }

    // The game builds a choose-one row out of plain StandardButtons and marks
    // the chosen one by colour alone, in one of two ways.
    //
    // A light BORDER where the others keep their own colour: the almanac's
    // modifier filters (Curses / Blessings / Both), the leaderboard picker.
    //
    // A light CAPTION where every other one is greyed: the TextMod page's
    // info / api / api-2 sections, its type row and its letter row
    // ("[notranslate][light]api" among "[notranslate][grey]info"). The greyed
    // siblings are what tells this from a list that merely has light entries
    // in it — the keyword index captions "cleave" in its keyword colour,
    // light, among others in theirs.
    private static boolean chosenAmongButtons(Actor actor) {
        if (!(actor instanceof StandardButton) || actor.getParent() == null) {
            return false;
        }
        try {
            StandardButton button = (StandardButton) actor;
            boolean border = buttonCol(button) == com.tann.dice.util.Colours.light;
            boolean caption = openingTags(button).endsWith("[light]");
            if (border == caption) {
                return false;
            }
            boolean unlitSibling = false;
            for (Actor sibling : actor.getParent().getChildren()) {
                if (sibling == actor || !(sibling instanceof StandardButton)) {
                    continue;
                }
                if (border) {
                    // Two filter rows share a parent, each with its own chosen one.
                    unlitSibling |= buttonCol(sibling) != com.tann.dice.util.Colours.light;
                    continue;
                }
                String tags = openingTags((StandardButton) sibling);
                if (!tags.contains("[grey]") || tags.endsWith("[light]")) {
                    return false;
                }
                unlitSibling = true;
            }
            return unlitSibling;
        } catch (Throwable t) {
            snd.contracts.SndLog.error("button colour read failed", t);
            return false;
        }
    }

    private static final java.util.regex.Pattern OPENING_TAGS =
            java.util.regex.Pattern.compile("^(\\[[^\\]]*\\])+");

    // The markup tags a button's caption opens with ("[notranslate][light]"
    // of "[notranslate][light]api"); empty for an image button or a bare
    // caption.
    private static String openingTags(StandardButton button) {
        String text = button.getText();
        if (text == null) {
            return "";
        }
        java.util.regex.Matcher tags = OPENING_TAGS.matcher(text);
        return tags.find() ? tags.group() : "";
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

    // Bare die-side images (the graph tab's series icons and add-side popup)
    // carry no text; name them via a texture-to-side map.
    private static java.util.Map<Object, com.tann.dice.gameplay.content.ent.die.side.EntSide> sidesByTexture;

    private static String sideIconName(Actor actor) {
        com.tann.dice.gameplay.content.ent.die.side.EntSide side = sideOf(actor);
        return side != null ? GameText.t(side.getBaseEffect().describe()) : null;
    }

    /** The die side an ImageActor displays, or null. */
    static com.tann.dice.gameplay.content.ent.die.side.EntSide sideOf(Actor actor) {
        if (!(actor instanceof com.tann.dice.util.ImageActor)) {
            return null;
        }
        try {
            if (sidesByTexture == null) {
                java.util.Map<Object, com.tann.dice.gameplay.content.ent.die.side.EntSide> map =
                        new java.util.IdentityHashMap<Object, com.tann.dice.gameplay.content.ent.die.side.EntSide>();
                for (com.tann.dice.gameplay.content.ent.die.side.EntSide side
                        : com.tann.dice.gameplay.content.ent.die.side.EntSidesLib.getAllSidesWithValue()) {
                    map.put(side.getTexture(), side);
                }
                sidesByTexture = map;
            }
            return sidesByTexture.get(((com.tann.dice.util.ImageActor) actor).tr);
        } catch (Throwable t) {
            snd.contracts.SndLog.error("side icon lookup failed", t);
            return null;
        }
    }

    // A Checkbox draws its own tick state; find one under the row.
    private static String checkboxState(Actor actor) {
        com.tann.dice.util.ui.Checkbox box = findCheckbox(actor);
        if (box == null) {
            return null;
        }
        return Loc.get("ui", box.isOn() ? "state.checked" : "state.unchecked");
    }

    private static com.tann.dice.util.ui.Checkbox findCheckbox(Actor actor) {
        if (actor instanceof com.tann.dice.util.ui.Checkbox) {
            return (com.tann.dice.util.ui.Checkbox) actor;
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                com.tann.dice.util.ui.Checkbox found = findCheckbox(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
