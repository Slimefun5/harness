package io.github.slimefun5.harness.probe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import io.github.slimefun5.harness.simulation.HarnessContext;
import io.github.slimefun5.harness.simulation.SimulatedPlayer;
import io.github.slimefun5.harness.simulation.Simulation;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;

/**
 * Places every machine that has a menu in the simulated world the way a player does, once, and keeps the
 * menus Slimefun made for them for every scenario that needs one.
 */
final class MachinePlacement {

    private static final String CHECK = "machine-placement";
    private static final int Y = 64;
    private static final int ROW = 32;

    private static List<PlacedMenu> placed;

    private MachinePlacement() {}

    /** A machine standing in the world with the menu Slimefun made for it. */
    static final class PlacedMenu {

        final BlockMenuPreset preset;
        final DirtyChestMenu menu;
        private final int[] storageSlots;
        private final int[] fillableSlots;

        PlacedMenu(BlockMenuPreset preset, DirtyChestMenu menu) {
            this.preset = preset;
            this.menu = menu;
            TreeSet<Integer> declared = declaredSlots(preset, menu);
            TreeSet<Integer> storage = new TreeSet<>(declared);
            TreeSet<Integer> fillable = new TreeSet<>(declared);
            for (int slot : preset.getInventorySlots()) {
                ItemStack item = menu.getItemInSlot(slot);
                if (item == null || item.getType() == Material.AIR) {
                    storage.add(slot);
                    if (menu.getMenuClickHandler(slot) == null) {
                        fillable.add(slot);
                    }
                }
            }
            this.storageSlots = toArray(storage);
            this.fillableSlots = toArray(fillable);
        }

        /**
         * Where the machine's own items can be: every slot outside the preset's layout that is empty once the
         * machine has been placed and opened, plus whatever it declares to cargo.
         *
         * @implNote Emptiness is what tells storage from a button the machine draws per block (an assembler's
         *           offset, a cargo node's channel), since both sit outside the preset's own layout.
         */
        int[] storageSlots() {
            return storageSlots;
        }

        /**
         * The storage slots an item can really get into: those with no click handler, which a player fills,
         * and those declared to cargo, which machines and cargo fill. A take-only slot such as a result preview
         * is only ever filled by the machine itself.
         */
        int[] fillableSlots() {
            return fillableSlots;
        }

        private static TreeSet<Integer> declaredSlots(BlockMenuPreset preset, DirtyChestMenu menu) {
            TreeSet<Integer> slots = new TreeSet<>();
            ItemStack sample = new ItemStack(Material.IRON_INGOT);
            for (ItemTransportFlow flow : ItemTransportFlow.values()) {
                addAll(slots, preset.getSlotsAccessedByItemTransport(flow));
                addAll(slots, preset.getSlotsAccessedByItemTransport(menu, flow, sample));
            }
            return slots;
        }

        private static void addAll(TreeSet<Integer> slots, int[] declared) {
            if (declared != null) {
                for (int slot : declared) {
                    slots.add(slot);
                }
            }
        }

        private static int[] toArray(TreeSet<Integer> slots) {
            return slots.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    /** Every machine menu, in id order, placed on first use. */
    static synchronized List<PlacedMenu> all(Simulation simulation, Findings findings) throws InterruptedException {
        if (placed == null) {
            SimulatedPlayer placer = ResearchedPlayer.join(simulation, "placer", "en_us");
            placer.setOp(true);
            placed = Collections.unmodifiableList(placeAll(simulation, placer, findings));
            simulation.quit(placer);
        }
        return placed;
    }

    /**
     * @implNote Slimefun fills a new machine's menu one tick after placing it ({@code BlockMenuPreset#newInstance}
     *           runs through {@code runSync}), so the menus are read only after the clock has moved on.
     */
    private static List<PlacedMenu> placeAll(Simulation simulation, SimulatedPlayer placer, Findings findings) {
        Map<BlockMenuPreset, DirtyChestMenu> created = new java.util.LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, BlockMenuPreset> entry : new TreeMap<>(Slimefun.getRegistry().getMenuPresets()).entrySet()) {
            String id = entry.getKey();
            HarnessContext.set(CHECK + " " + id);
            try {
                DirtyChestMenu menu = place(simulation, placer, id, entry.getValue(), index++);
                if (menu == null) {
                    findings.report(CHECK, id, "placing it created no menu");
                } else {
                    created.put(entry.getValue(), menu);
                }
            } catch (IllegalStateException refused) {
                findings.report(CHECK, id, refused.getMessage());
            } catch (RuntimeException | LinkageError crash) {
                findings.report(CHECK, id, "placing it threw " + crash);
            }
        }
        simulation.advanceTicks(2);
        List<PlacedMenu> menus = new ArrayList<>();
        for (Map.Entry<BlockMenuPreset, DirtyChestMenu> entry : created.entrySet()) {
            HarnessContext.set(CHECK + " " + entry.getKey().getID() + ": first open");
            entry.getValue().open(placer.player());
            placer.player().closeInventory();
            menus.add(new PlacedMenu(entry.getKey(), entry.getValue()));
        }
        return menus;
    }

    /**
     * @implNote A preset with no item of its own (an addon's internal menu) cannot be placed by a player, so it
     *           is recorded directly, which is what placing does once the place event has passed.
     */
    private static DirtyChestMenu place(Simulation simulation, SimulatedPlayer placer, String id, BlockMenuPreset preset, int index) {
        if (preset.isUniversal()) {
            return BlockStorage.getUniversalInventory(id);
        }
        Block block = simulation.world().blockAt((index % ROW) * 2, Y, (index / ROW) * 2);
        SlimefunItem item = SlimefunItem.getById(id);
        if (item == null) {
            block.setType(Material.STONE);
            BlockStorage.addBlockInfo(block, "id", id, true);
        } else {
            int messagesBefore = placer.messages().size();
            if (!placer.place(block, BlockFace.WEST, item.getItem().clone(), blockTypeOf(item.getItem().getType()))) {
                List<String> told = placer.messages().subList(messagesBefore, placer.messages().size());
                throw new IllegalStateException("its place event was cancelled" + (told.isEmpty() ? "" : ", telling the player " + told));
            }
        }
        return BlockStorage.getInventory(block);
    }

    /** What placing an item of this material puts in the world. */
    private static Material blockTypeOf(Material itemType) {
        if (itemType.isBlock()) {
            return itemType;
        }
        Material legacySkull = Material.getMaterial("SKULL");
        return itemType.name().equals("SKULL_ITEM") && legacySkull != null ? legacySkull : Material.STONE;
    }
}
