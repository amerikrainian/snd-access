package snd.module.screens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.tann.dice.gameplay.content.ent.Hero;
import com.tann.dice.gameplay.content.ent.group.Party;
import com.tann.dice.gameplay.content.item.Item;
import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.screens.dungeon.DungeonScreen;
import com.tann.dice.screens.generalPanels.PartyManagementPanel;

import snd.core.HostServices;
import snd.core.loc.Loc;
import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.nav.AccessScreen;
import snd.core.nav.KeyOffer;
import snd.module.GameKeys;
import snd.module.GameText;
import snd.module.GameUi;

/**
 * The party equipment screen (PartyManagementPanel): a grid of hero rows with
 * their item slots, the item bag, and randomize/done. The game's only equip
 * path is drag-and-drop with the destination decided by drop position; here
 * it becomes pick-and-place — activate an item (bag or slot) to pick it up,
 * activate a hero slot to equip it there, or the put-away button to return it
 * to the bag. Placement goes through the panel's own {@code equip} (the exact
 * drop code: swaps, displaced items, refresh, flashes), so outcomes match a
 * mouse drag exactly.
 */
public class InventoryScreen extends AccessScreen {
    private final HostServices host;
    private Item held;

    public InventoryScreen(HostServices host) {
        this.host = host;
    }

    @Override
    public String key() {
        return "inventory";
    }

    @Override
    public int layer() {
        return 20; // replaces the generic modal reader for this panel
    }

    @Override
    public boolean isActive() {
        return GameUi.topModal() instanceof PartyManagementPanel;
    }

    @Override
    public String screenName() {
        return GameText.t("Inventory");
    }

    @Override
    public boolean wrap() {
        return true;
    }

    // PartyManagementPanel.keyPress: R randomises, I and Escape are Done.
    // Enter (Done too) is the navigator's here.
    @Override
    public List<KeyOffer> keys() {
        List<KeyOffer> keys = new ArrayList<KeyOffer>();
        keys.add(GameKeys.key("randomize", "R", Loc.get("ui", "inv.randomize"), GameKeys.R));
        keys.add(GameKeys.key("done", Loc.get("ui", "help.keys_or", "a", "I", "b", Loc.get("ui", "key.escape")),
                Loc.get("ui", "inv.done"), GameKeys.ESCAPE));
        return keys;
    }

    @Override
    public void build(GraphBuilder b) {
        final DungeonScreen ds = DungeonScreen.get();
        if (ds == null || ds.getFightLog() == null) {
            return;
        }
        final PartyManagementPanel panel = ds.partyManagementPanel;
        final Party party = ds.getDungeonContext().getParty();

        // A picked-up item that meanwhile left the party (undo, randomize) is
        // no longer placeable.
        if (held != null && party.getEquippee(held) == null && !party.getItems(false).contains(held)) {
            held = null;
        }

        if (held != null) {
            final Item h = held;
            b.addLabel(ControlId.structural(CompositeKey.of("inv", "holding")),
                    new Supplier<String>() {
                        @Override
                        public String get() {
                            return Loc.get("ui", "inv.holding", "item", GameText.t(h.getName(true)));
                        }
                    });
        }

        buildHeroes(b, ds, panel, party);
        buildBag(b, ds, panel, party);
        buildActions(b, panel);
    }

    // ---- hero rows: one row per hero, slots to the right ----

    private void buildHeroes(GraphBuilder b, final DungeonScreen ds,
            final PartyManagementPanel panel, final Party party) {
        // Mirror the panel's own hero filter.
        List<Hero> heroes = new ArrayList<Hero>(
                ds.getFightLog().getSnapshot(FightLog.Temporality.Present).getAliveHeroEntities());
        for (int i = heroes.size() - 1; i >= 0; i--) {
            if (heroes.get(i).getBlankState().skipEquipScreen()) {
                heroes.remove(i);
            }
        }

        b.beginStop("party").pushContext(Loc.get("combat", "heroes"), Loc.get("ui", "role.list"));
        for (int hi = 0; hi < heroes.size(); hi++) {
            final Hero hero = heroes.get(hi);
            b.startRow("hero");

            NodeVtable heroNode = new NodeVtable();
            heroNode.controlType = ControlTypes.BUTTON;
            heroNode.subject = hero;
            heroNode.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t(hero.getName(true));
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return Loc.get("combat", "level", "n", hero.getLevel());
                        }
                    }, AnnouncementKinds.VALUE));
            Runnable sheet = new Runnable() {
                @Override
                public void run() {
                    host.speech().speak(SheetScreen.sheetText(ds, hero), false);
                }
            };
            heroNode.onActivate = sheet;
            heroNode.onSecondary = sheet;
            b.addItem(ControlId.referenced(hero, CompositeKey.of("inv-hero", hi)), heroNode);

            int slots = hero.getNumberItemSlots();
            for (int si = 0; si < slots; si++) {
                final int slot = si;
                NodeVtable slotNode = new NodeVtable();
                slotNode.controlType = ControlTypes.BUTTON;
                slotNode.subject = hero;
                slotNode.announcements = Arrays.asList(
                        NodeAnnouncement.kinded(new Supplier<String>() {
                            @Override
                            public String get() {
                                return Loc.get("ui", "inv.slot", "n", slot + 1);
                            }
                        }, AnnouncementKinds.LABEL),
                        NodeAnnouncement.kinded(new Supplier<String>() {
                            @Override
                            public String get() {
                                Item item = hero.getItems(slot);
                                if (item == null) {
                                    return Loc.get("ui", "inv.empty");
                                }
                                String text = GameText.t(item.getName(true));
                                return item == held ? text + ", " + Loc.get("ui", "inv.held") : text;
                            }
                        }, AnnouncementKinds.VALUE));
                slotNode.onActivate = new Runnable() {
                    @Override
                    public void run() {
                        slotActivate(ds, panel, party, hero, slot);
                    }
                };
                slotNode.onSecondary = new Runnable() {
                    @Override
                    public void run() {
                        Item item = hero.getItems(slot);
                        host.speech().speak(item != null ? itemDetail(item)
                                : Loc.get("ui", "inv.empty"), false);
                    }
                };
                b.addItem(ControlId.structural(CompositeKey.of("inv-slot", hi, si)), slotNode);
            }

            NodeVtable rename = new NodeVtable();
            rename.controlType = ControlTypes.BUTTON;
            rename.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return Loc.get("ui", "inv.rename");
                }
            }, AnnouncementKinds.LABEL));
            rename.onActivate = new Runnable() {
                @Override
                public void run() {
                    // The title-bar click: opens the game's text input, which
                    // TextEntryWatcher then speaks.
                    com.badlogic.gdx.scenes.scene2d.Actor target =
                            GameUi.heroRenameTarget(hero.getDiePanel());
                    if (target == null) {
                        host.speech().speak(Loc.get("ui", "inv.rename_unavailable"), true);
                        return;
                    }
                    GameUi.activate(target);
                }
            };
            b.addItem(ControlId.structural(CompositeKey.of("inv-rename", hi)), rename);
            b.endRow();
        }
        b.popContext();
    }

    // ---- the bag ----

    private void buildBag(GraphBuilder b, final DungeonScreen ds,
            final PartyManagementPanel panel, final Party party) {
        b.beginStop("bag").pushContext(GameText.t("Items"), Loc.get("ui", "role.list"));
        List<Item> bag = party.getItems(false);
        if (bag.isEmpty()) {
            b.addLabel(ControlId.structural(CompositeKey.of("inv", "empty-bag")),
                    new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t("no items :(");
                        }
                    });
        }
        for (int i = 0; i < bag.size(); i++) {
            final Item item = bag.get(i);
            NodeVtable vt = new NodeVtable();
            vt.controlType = ControlTypes.BUTTON;
            vt.subject = item;
            vt.announcements = Arrays.asList(
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t(item.getName(true));
                        }
                    }, AnnouncementKinds.LABEL),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            StringBuilder sb = new StringBuilder();
                            if (item == held) {
                                sb.append(Loc.get("ui", "inv.held"));
                            }
                            if (item.hasTier()) {
                                if (sb.length() > 0) {
                                    sb.append(", ");
                                }
                                sb.append(Loc.get("ui", "choice.tier", "tier", item.getTier()));
                            }
                            // The red glow: force-equip; the yellow glow: new.
                            if (item.isForceEquip()) {
                                if (sb.length() > 0) {
                                    sb.append(", ");
                                }
                                sb.append(Loc.get("ui", "inv.force_equip"));
                            } else if (item.isNew()) {
                                if (sb.length() > 0) {
                                    sb.append(", ");
                                }
                                sb.append(Loc.get("ui", "inv.new"));
                            }
                            return sb.length() > 0 ? sb.toString() : null;
                        }
                    }, AnnouncementKinds.VALUE),
                    NodeAnnouncement.kinded(new Supplier<String>() {
                        @Override
                        public String get() {
                            return GameText.t(item.getDescription());
                        }
                    }, AnnouncementKinds.TOOLTIP));
            vt.onActivate = new Runnable() {
                @Override
                public void run() {
                    held = item;
                    host.speech().speak(Loc.get("ui", "inv.picked_up",
                            "item", GameText.t(item.getName(true))), true);
                }
            };
            vt.onSecondary = new Runnable() {
                @Override
                public void run() {
                    host.speech().speak(itemDetail(item), false);
                }
            };
            b.addItem(ControlId.referenced(item, CompositeKey.of("inv-bag", i)), vt);
        }

        // Return a held EQUIPPED item to the bag (a bag-held item is already
        // there — placing it back is a no-op the button would double).
        if (held != null && party.getEquippee(held) != null) {
            final Item moving = held;
            NodeVtable putAway = new NodeVtable();
            putAway.controlType = ControlTypes.BUTTON;
            putAway.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return Loc.get("ui", "inv.put_away");
                }
            }, AnnouncementKinds.LABEL));
            putAway.onActivate = new Runnable() {
                @Override
                public void run() {
                    // The drag path's drop-outside-a-panel branch.
                    held = null;
                    party.unequip(moving);
                    party.addItem(moving);
                    com.tann.dice.statics.sound.Sounds.playSound(com.tann.dice.statics.sound.Sounds.drop);
                    ds.save();
                    host.speech().speak(Loc.get("ui", "inv.unequipped",
                            "item", GameText.t(moving.getName(true))), true);
                }
            };
            b.addItem(ControlId.structural(CompositeKey.of("inv", "put-away")), putAway);
        }
        b.popContext();
    }

    // ---- randomize / done, via the panel's own key routes ----

    private void buildActions(GraphBuilder b, final PartyManagementPanel panel) {
        b.beginStop("actions");
        NodeVtable randomize = new NodeVtable();
        randomize.controlType = ControlTypes.BUTTON;
        randomize.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", "inv.randomize");
            }
        }, AnnouncementKinds.LABEL));
        randomize.onActivate = new Runnable() {
            @Override
            public void run() {
                held = null;
                panel.keyPress(46); // the R key
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("inv", "randomize")), randomize);

        NodeVtable done = new NodeVtable();
        done.controlType = ControlTypes.BUTTON;
        done.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return Loc.get("ui", "inv.done");
            }
        }, AnnouncementKinds.LABEL));
        done.onActivate = new Runnable() {
            @Override
            public void run() {
                panel.keyPress(66); // the Enter key: close the panel
            }
        };
        b.addItem(ControlId.structural(CompositeKey.of("inv", "done")), done);
    }

    // Activating a slot: place the held item there, or pick up what's in it.
    private void slotActivate(DungeonScreen ds, PartyManagementPanel panel,
            Party party, Hero hero, int slot) {
        Item inSlot = hero.getItems(slot);
        if (held == null) {
            if (inSlot == null) {
                host.speech().speak(Loc.get("ui", "inv.empty"), true);
                return;
            }
            held = inSlot;
            host.speech().speak(Loc.get("ui", "inv.picked_up",
                    "item", GameText.t(inSlot.getName(true))), true);
            return;
        }
        Item moving = held;
        if (!moving.usableBy(hero)) {
            host.speech().speak(Loc.get("ui", "inv.cannot_use",
                    "hero", GameText.t(hero.getName(true)),
                    "item", GameText.t(moving.getName(true))), true);
            return;
        }
        held = null;
        // The drag flow removes a bag item from the list at pickup; equip()
        // itself only detaches from a previous holder.
        party.removeItem(moving);
        panel.equip(hero, moving, slot);
        ds.save();
        String spoken = Loc.get("ui", "inv.equipped",
                "item", GameText.t(moving.getName(true)),
                "hero", GameText.t(hero.getName(true)),
                "n", slot + 1);
        if (inSlot != null && inSlot != moving) {
            spoken += ", " + Loc.get("ui", "inv.swapped_out",
                    "item", GameText.t(inSlot.getName(true)));
        }
        host.speech().speak(spoken, true);
    }

    private static String itemDetail(Item item) {
        StringBuilder sb = new StringBuilder(GameText.t(item.getName(true)));
        if (item.hasTier()) {
            sb.append(", ").append(Loc.get("ui", "choice.tier", "tier", item.getTier()));
        }
        String desc = item.getDescription();
        if (desc != null && !desc.trim().isEmpty()) {
            sb.append(". ").append(GameText.t(desc));
        }
        return sb.toString();
    }
}
