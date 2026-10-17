package io.github.slimefun5.harness.simulation;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * An open window: a top inventory over the player's own, with vanilla's raw slot numbering (the top's slots,
 * then the player's 9 to 35, then the hotbar).
 */
final class SimulatedView {

    private static final int PLAYER_SLOTS = 36;

    private final SimulatedPlayer player;
    private final Inventory top;
    private final InventoryType type;
    private final String originalTitle;
    private String title;

    private SimulatedView(SimulatedPlayer player, Inventory top, InventoryType type, String title) {
        this.player = player;
        this.top = top;
        this.type = type;
        this.originalTitle = title;
        this.title = title;
    }

    static InventoryView create(SimulatedPlayer player, Inventory top, InventoryType type, String title) {
        return Facade.create(InventoryView.class, new SimulatedView(player, top, type, title).new Handler());
    }

    final class Handler {

        Inventory getTopInventory() {
            return top;
        }

        Inventory getBottomInventory() {
            return player.player().getInventory();
        }

        HumanEntity getPlayer() {
            return player.player();
        }

        InventoryType getType() {
            return type;
        }

        String getTitle() {
            return title;
        }

        String getOriginalTitle() {
            return originalTitle;
        }

        void setTitle(String newTitle) {
            title = newTitle;
        }

        ItemStack getCursor() {
            return player.cursor();
        }

        void setCursor(ItemStack item) {
            player.setCursor(item);
        }

        int countSlots() {
            return top.getSize() + PLAYER_SLOTS;
        }

        int convertSlot(int rawSlot) {
            if (rawSlot < top.getSize()) {
                return rawSlot;
            }
            int slot = rawSlot - top.getSize();
            return slot >= 27 ? slot - 27 : slot + 9;
        }

        Inventory getInventory(int rawSlot) {
            if (rawSlot < 0 || rawSlot >= countSlots()) {
                return null;
            }
            return rawSlot < top.getSize() ? top : getBottomInventory();
        }

        ItemStack getItem(int rawSlot) {
            Inventory inventory = getInventory(rawSlot);
            return inventory == null ? null : inventory.getItem(convertSlot(rawSlot));
        }

        void setItem(int rawSlot, ItemStack item) {
            Inventory inventory = getInventory(rawSlot);
            if (inventory != null) {
                inventory.setItem(convertSlot(rawSlot), item);
            } else if (item != null) {
                player.player().getWorld().dropItemNaturally(player.player().getLocation(), item);
            }
        }

        InventoryType.SlotType getSlotType(int rawSlot) {
            if (rawSlot < 0 || rawSlot >= countSlots()) {
                return InventoryType.SlotType.OUTSIDE;
            }
            if (rawSlot >= top.getSize() && convertSlot(rawSlot) < 9) {
                return InventoryType.SlotType.QUICKBAR;
            }
            return InventoryType.SlotType.CONTAINER;
        }

        void close() {
            player.player().closeInventory();
        }

        @Override
        public String toString() {
            return "SimulatedView{" + title + ", " + type + "}";
        }
    }
}
