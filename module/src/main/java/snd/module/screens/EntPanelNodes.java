package snd.module.screens;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.tann.dice.gameplay.content.ent.Ent;
import com.tann.dice.gameplay.content.ent.Hero;
import com.tann.dice.gameplay.content.item.Item;
import com.tann.dice.gameplay.effect.Trait;
import com.tann.dice.gameplay.effect.eff.Eff;
import com.tann.dice.gameplay.fightLog.EntState;
import com.tann.dice.gameplay.fightLog.FightLog;
import com.tann.dice.gameplay.trigger.personal.Personal;
import com.tann.dice.screens.dungeon.panels.Explanel.EntPanelInventory;

import snd.core.graph.AnnouncementKinds;
import snd.core.graph.CompositeKey;
import snd.core.graph.ControlId;
import snd.core.graph.ControlTypes;
import snd.core.graph.GraphBuilder;
import snd.core.graph.NodeAnnouncement;
import snd.core.graph.NodeVtable;
import snd.core.loc.Loc;
import snd.module.GameText;

/**
 * A unit's panel (EntPanelInventory) wherever the generic walk meets it: a
 * class's details from the Choose-Party picker, the almanac's hero and
 * monster pages, a dialog's hero. Read from the unit the panel shows, as the
 * character sheet is: its name, colour, level and hp in one line; the sides by
 * where they sit on the die, in the sheet's reading order; a hero's item
 * slots (NetPanel's, filled or empty); every status the sheet lists; the
 * traits beside the net, a spell as its card. Every node concerns the unit,
 * so the hero buffer and the side keys read it. Nodes are keyed on the unit,
 * since a dialog can show two panels of one class.
 */
final class EntPanelNodes {
    private EntPanelNodes() {
    }

    static void emit(GraphBuilder b, EntPanelInventory panel) {
        final Ent ent = panel.ent;

        NodeVtable header = unitNode(ent);
        header.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return UnitLines.restHeader(ent, state(ent));
            }
        }, AnnouncementKinds.LABEL));
        b.addItem(id(ent, "header"), header);

        int[] order = SideText.readingOrder();
        for (int digit = 0; digit < order.length; digit++) {
            if (order[digit] < ent.getSides().length) {
                b.addItem(id(ent, "side", order[digit]), side(ent, digit, order[digit]));
            }
        }

        if (ent instanceof Hero) {
            for (int slot = 0; slot < ent.getNumberItemSlots(); slot++) {
                b.addItem(id(ent, "item", slot), item(ent, slot));
            }
        }

        EntState state = state(ent);
        int n = 0;
        for (Personal p : UnitLines.sheetPersonals(state)) {
            b.addItem(id(ent, "status", n++), status(ent, p));
        }
        n = 0;
        for (Trait t : UnitLines.netTraits(ent, state)) {
            b.addItem(id(ent, "trait", n++), trait(ent, t));
        }

        // The copy button the panel gains under the game's option
        // (EntPanelInventory.layout → APIUtils.addCopyButton, which skips a
        // name of "curse"): it copies the unit's name.
        final String name = ent.getName(false);
        if (com.tann.dice.gameplay.save.settings.option.OptionUtils.shouldShowCopy()
                && !name.equalsIgnoreCase("curse")) {
            NodeVtable copy = unitNode(ent);
            copy.controlType = ControlTypes.BUTTON;
            copy.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
                @Override
                public String get() {
                    return GameText.t("copy");
                }
            }, AnnouncementKinds.LABEL));
            copy.onActivate = new Runnable() {
                @Override
                public void run() {
                    com.tann.dice.util.ui.ClipboardUtils.copyWithSoundAndToast(name);
                }
            };
            b.addItem(id(ent, "copy"), copy);
        }
    }

    private static EntState state(Ent ent) {
        return ent.getState(FightLog.Temporality.Present);
    }

    private static ControlId id(Ent ent, Object... parts) {
        Object[] key = new Object[parts.length + 2];
        key[0] = "unit-panel";
        key[1] = ent;
        System.arraycopy(parts, 0, key, 2, parts.length);
        return ControlId.structural(CompositeKey.of(key));
    }

    private static NodeVtable side(final Ent ent, final int digit, final int index) {
        NodeVtable vt = unitNode(ent);
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return UnitLines.sideLine(ent, digit);
            }
        }, AnnouncementKinds.LABEL));
        vt.details = CombatScreen.ruleDetails(new Supplier<Eff>() {
            @Override
            public Eff get() {
                return ent.getSides()[index].findState(FightLog.Temporality.Present, ent).getCalculatedEffect();
            }
        });
        return vt;
    }

    // The slot as NetPanel fills it: an item the unit's state ignores is
    // drawn as an empty slot.
    private static Item itemIn(Ent ent, int slot) {
        Item item = ent.getItems(slot);
        return item != null && state(ent).ignoreItem(item) ? null : item;
    }

    private static NodeVtable item(final Ent ent, final int slot) {
        NodeVtable vt = unitNode(ent);
        vt.announcements = Arrays.asList(
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        Item item = itemIn(ent, slot);
                        return item != null ? GameText.t(item.getName()) : Loc.get("ui", "modal.empty_slot");
                    }
                }, AnnouncementKinds.LABEL),
                NodeAnnouncement.kinded(new Supplier<String>() {
                    @Override
                    public String get() {
                        Item item = itemIn(ent, slot);
                        String desc = item != null ? item.getDescription() : null;
                        return desc == null || desc.trim().isEmpty() ? null : GameText.t(desc);
                    }
                }, AnnouncementKinds.VALUE));
        vt.details = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                Item item = itemIn(ent, slot);
                if (item == null) {
                    return new java.util.ArrayList<String>();
                }
                List<String> lines = UnitLines.taughtAbilities(item);
                lines.addAll(Terms.forItem(item));
                return lines;
            }
        };
        return vt;
    }

    private static NodeVtable status(Ent ent, final Personal personal) {
        NodeVtable vt = unitNode(ent);
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return SpecialPips.describe(personal);
            }
        }, AnnouncementKinds.LABEL));
        vt.details = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return Terms.forPersonal(personal);
            }
        };
        return vt;
    }

    private static NodeVtable trait(Ent ent, final Trait trait) {
        NodeVtable vt = unitNode(ent);
        vt.announcements = Arrays.asList(NodeAnnouncement.kinded(new Supplier<String>() {
            @Override
            public String get() {
                return UnitLines.netTraitLine(trait);
            }
        }, AnnouncementKinds.LABEL));
        vt.details = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return UnitLines.netTraitRules(trait);
            }
        };
        return vt;
    }

    private static NodeVtable unitNode(Ent ent) {
        NodeVtable vt = new NodeVtable();
        vt.controlType = ControlTypes.TEXT;
        vt.subject = ent;
        return vt;
    }
}
