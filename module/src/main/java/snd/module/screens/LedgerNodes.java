package snd.module.screens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.tann.dice.gameplay.content.ent.EntSize;
import com.tann.dice.gameplay.content.ent.type.EntType;
import com.tann.dice.gameplay.content.ent.type.HeroType;
import com.tann.dice.gameplay.content.ent.type.MonsterType;
import com.tann.dice.gameplay.content.item.Item;
import com.tann.dice.gameplay.progress.chievo.unlock.UnUtil;
import com.tann.dice.gameplay.progress.chievo.unlock.Unlockable;
import com.tann.dice.screens.dungeon.panels.book.page.ledgerPage.LedgerPage.LedgerPageType;
import com.tann.dice.screens.dungeon.panels.book.views.HeroLedgerView;
import com.tann.dice.screens.dungeon.panels.book.views.ItemLedgerView;
import com.tann.dice.screens.dungeon.panels.book.views.MonsterLedgerView;
import com.tann.dice.util.ImageActor;
import com.tann.dice.util.Rectactor;
import com.tann.dice.util.ui.TextWriter;

import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.module.GameText;
import snd.module.GameUi;

/**
 * The almanac's hero, monster and item tabs (LedgerUtils.makeHeroGroup /
 * makeMonsterGroup / makeItemsGroup): a wall of portrait tiles whose order is
 * the only thing that says what they are — heroes by colour and tier,
 * monsters by size, items under a bare tier number. Which tiles there are, and
 * in what order, is the game's: they are the tiles it laid out. What one is
 * comes from the type or item it holds, which also names the group it opens
 * (a jump region) and keys the node, so focus survives the page being rebuilt.
 *
 * <p>What the game decided while building the page and kept nowhere (the
 * veil over one not met yet, the seen and banned marks of the current run)
 * comes from {@link LedgerFacts}: the page's stats and the run, never the
 * drawn marks. Tiles shown outside the almanac carry no marks.
 */
final class LedgerNodes {
    private LedgerNodes() {
    }

    /**
     * False when the content is not this tab's tiles (another tab, or a page
     * of another kind shown in its place): the generic walk reads it.
     */
    static boolean emit(GraphBuilder b, Actor content, LedgerPageType tab, LedgerFacts facts) {
        Class<? extends Actor> tile = tileClass(tab);
        if (tile == null || !holds(content, tile)) {
            return false;
        }
        Walk walk = new Walk(b, tile, facts);
        walk.visit(content);
        walk.closeGroup();
        return true;
    }

    private static Class<? extends Actor> tileClass(LedgerPageType tab) {
        switch (tab) {
            case Hero:
                return HeroLedgerView.class;
            case Monster:
                return MonsterLedgerView.class;
            case Item:
                return ItemLedgerView.class;
            default:
                return null;
        }
    }

    private static boolean holds(Actor actor, Class<? extends Actor> tile) {
        if (tile.isInstance(actor)) {
            return true;
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                if (holds(child, tile)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class Walk {
        private final GraphBuilder b;
        private final Class<? extends Actor> tile;
        private final LedgerFacts facts;
        private Object openGroup;

        Walk(GraphBuilder b, Class<? extends Actor> tile, LedgerFacts facts) {
            this.b = b;
            this.tile = tile;
            this.facts = facts;
        }

        void visit(Actor actor) {
            if (!actor.isVisible()) {
                return;
            }
            if (tile.isInstance(actor)) {
                tile(actor);
                return;
            }
            if (actor instanceof Group && holds(actor, tile)) {
                // The bare number over a run of item tiles is their tier, a
                // curse tier told apart by colour alone: the group the tiles
                // open says it, sign included.
                boolean tierHeadings = tile == ItemLedgerView.class && holdsDirectly((Group) actor);
                for (Actor child : ((Group) actor).getChildren()) {
                    if (tierHeadings && child instanceof TextWriter) {
                        continue;
                    }
                    visit(child);
                }
                return;
            }
            closeGroup();
            ActorNodes.emit(b, actor); // the "10/128 heroes found" line, anything the page gains
        }

        private boolean holdsDirectly(Group group) {
            for (Actor child : group.getChildren()) {
                if (tile.isInstance(child)) {
                    return true;
                }
            }
            return false;
        }

        private void tile(Actor actor) {
            HeroType hero = ActorNodes.heroTypeOf(actor);
            MonsterType monster = ActorNodes.monsterTypeOf(actor);
            Item item = ActorNodes.itemOf(actor);
            if (hero != null) {
                group(CompositeKey.of("ledger-heroes", hero.heroCol, hero.level),
                        Loc.get("ui", "book.hero_group", "colour", GameText.t(hero.heroCol.colName),
                                "tier", hero.level));
                b.addItem(ControlId.referenced(hero, CompositeKey.of("ledger-hero", hero.getName(false))),
                        tileNode(actor, hero, entName(hero), hero, facts));
            } else if (monster != null) {
                group(CompositeKey.of("ledger-monsters", monster.size), sizeName(monster.size));
                b.addItem(ControlId.referenced(monster, CompositeKey.of("ledger-monster", monster.getName(false))),
                        tileNode(actor, monster, entName(monster), monster, facts));
            } else if (item != null) {
                String tier = ChoosablePanelNodes.tierText(item);
                group(CompositeKey.of("ledger-items", item.getTier()),
                        tier != null ? tier : Loc.get("ui", "book.no_tier"));
                b.addItem(ControlId.referenced(item, CompositeKey.of("ledger-item", item.getName(false))),
                        tileNode(actor, item, itemName(item), item, facts));
            } else {
                closeGroup();
                ActorNodes.emit(b, actor); // the tile's field could not be read (logged there)
            }
        }

        private void group(Object key, String label) {
            if (key.equals(openGroup)) {
                return;
            }
            closeGroup();
            openGroup = key;
            b.setRegion(key);
            b.pushContext(label, Loc.get("ui", "role.group"));
        }

        void closeGroup() {
            if (openGroup != null) {
                b.popContext();
                b.setRegion(null);
                openGroup = null;
            }
        }
    }

    private static Supplier<String> entName(final com.tann.dice.gameplay.content.ent.type.EntType type) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return GameText.t(type.getName(true));
            }
        };
    }

    private static Supplier<String> itemName(final Item item) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return GameText.t(item.getName(true));
            }
        };
    }

    // The game's words for a size (GlobalSize.makePanelActorI).
    private static String sizeName(EntSize size) {
        return GameText.t(size == EntSize.reg ? "regular" : size.name());
    }

    private static NodeVtable tileNode(final Actor tile, final Unlockable unlockable, final Supplier<String> name,
            final Object subject, final LedgerFacts facts) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.BUTTON;
        // A locked tile is a padlock: the game keeps what it is to itself, and
        // answers a click with how to unlock it.
        vt.subject = UnUtil.isLocked(unlockable) ? null : subject;
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return UnUtil.isLocked(unlockable) ? Loc.get("ui", "icon.locked") : name.get();
                    }
                }, AnnouncementKinds.LABEL),
                // A hero's or monster's hp, which only its opened panel draws.
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return subject instanceof EntType && !UnUtil.isLocked(unlockable)
                                ? UnitLines.restHp((EntType) subject) : null;
                    }
                }, AnnouncementKinds.VALUE),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        return facts != null && !UnUtil.isLocked(unlockable) ? joined(facts.marks(subject)) : null;
                    }
                }, AnnouncementKinds.VALUE));
        vt.details = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return GameUi.infoLines(tile);
            }
        };
        vt.onActivate = new Runnable() {
            @Override
            public void run() {
                GameUi.activate(tile);
            }
        };
        vt.onSecondary = new Runnable() {
            @Override
            public void run() {
                GameUi.info(tile);
            }
        };
        return vt;
    }

    private static String joined(List<String> parts) {
        if (parts.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(part);
        }
        return sb.toString();
    }
}
