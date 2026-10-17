package io.github.slimefun5.harness.probe;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import io.github.slimefun5.harness.simulation.HarnessContext;
import io.github.slimefun5.harness.simulation.InventoryClicks;
import io.github.slimefun5.harness.simulation.ItemTally;
import io.github.slimefun5.harness.simulation.Scenario;
import io.github.slimefun5.harness.simulation.SimulatedPlayer;
import io.github.slimefun5.harness.simulation.SimulatedWorld;
import io.github.slimefun5.harness.simulation.Simulation;

/**
 * Clicks every slot of every machine menu with every click type, once with the machine's storage full and
 * the cursor empty and once the other way round, and fails when a click changes how many items exist: what
 * the player holds, the cursor, what was dropped, and the machine's own storage.
 *
 * @implNote Decorative and button slots are left out of the count because a button may re-render itself;
 *           an item taken out of one still shows, as a gain on the player's side.
 */
final class MenuClickScenario implements Scenario {

    private static final String CHECK = "menu-click";
    private static final int HOTBAR_BUTTON = 1;
    private static final int REPORTS_PER_MENU = 5;
    private static final List<ClickType> CLICKS = Arrays.asList(
        ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY, ClickType.DROP, ClickType.DOUBLE_CLICK);

    private final Logger logger;

    MenuClickScenario(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String name() {
        return CHECK;
    }

    @Override
    public void run(Simulation simulation) throws InterruptedException {
        Findings findings = new Findings(logger);
        SimulatedWorld world = simulation.world();
        List<MachinePlacement.PlacedMenu> menus = MachinePlacement.all(simulation, findings);
        SimulatedPlayer clicker = simulation.join("clicker", "en_us");
        int clicks = 0;
        for (MachinePlacement.PlacedMenu placed : menus) {
            clicks += clickEverything(placed, clicker, world, findings);
            clicker.player().closeInventory();
            simulation.advanceTicks(1);
        }
        simulation.quit(clicker);
        logger.info("Menu clicks checked: " + menus.size() + " menus, " + clicks + " clicks, " + findings.count() + " problem(s)");
    }

    private static int clickEverything(MachinePlacement.PlacedMenu placed, SimulatedPlayer clicker, SimulatedWorld world, Findings findings) {
        String id = placed.preset.getID();
        int[] storage = placed.storageSlots();
        int topSize = placed.menu.toInventory().getSize();
        int[] rawSlots = rawSlotsToClick(topSize);
        int reported = 0;
        int problems = 0;
        int clicks = 0;
        for (boolean storageFull : new boolean[] {true, false}) {
            for (int rawSlot : rawSlots) {
                for (ClickType click : CLICKS) {
                    String step = click + " on slot " + rawSlot + (storageFull ? " with the storage full" : " with a stack on the cursor");
                    HarnessContext.set(CHECK + " " + id + ": " + step);
                    clicks++;
                    String problem = clickOnce(placed, clicker, world, storage, storageFull, rawSlot, click);
                    if (problem != null) {
                        problems++;
                        if (reported++ < REPORTS_PER_MENU) {
                            findings.report(CHECK, id, step + " " + problem);
                        }
                    }
                }
            }
        }
        if (problems > REPORTS_PER_MENU) {
            findings.report(CHECK, id, (problems - REPORTS_PER_MENU) + " more click(s) with problems like the ones above");
        }
        return clicks;
    }

    /** Every slot of the menu, then one of the player's own slots and one hotbar slot. */
    private static int[] rawSlotsToClick(int topSize) {
        int[] slots = new int[topSize + 2];
        for (int i = 0; i < topSize; i++) {
            slots[i] = i;
        }
        slots[topSize] = topSize;
        slots[topSize + 1] = topSize + 27;
        return slots;
    }

    private static String clickOnce(MachinePlacement.PlacedMenu placed, SimulatedPlayer clicker, SimulatedWorld world,
            int[] storage, boolean storageFull, int rawSlot, ClickType click) {
        Player player = clicker.player();
        try {
            reset(placed, player, world, storage, storageFull);
            if (!openFor(placed, clicker)) {
                return "could not run: the menu did not open";
            }
            ItemTally before = tally(placed, player, world, storage);
            InventoryClicks.click(player, rawSlot, click, HOTBAR_BUTTON);
            player.closeInventory();
            List<String> changes = tally(placed, player, world, storage).changesSince(before);
            return changes.isEmpty() ? null : "changed how many items exist: " + String.join(", ", changes);
        } catch (RuntimeException | LinkageError crash) {
            return "threw " + crash;
        }
    }

    private static boolean openFor(MachinePlacement.PlacedMenu placed, SimulatedPlayer clicker) {
        placed.menu.open(clicker.player());
        return clicker.hasWindowOpen() && clicker.player().getOpenInventory().getTopInventory() == placed.menu.toInventory();
    }

    private static void reset(MachinePlacement.PlacedMenu placed, Player player, SimulatedWorld world, int[] storage, boolean storageFull) {
        player.closeInventory();
        world.drops().clear();
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setItem(HOTBAR_BUTTON, new ItemStack(Material.COBBLESTONE, 8));
        inventory.setItem(9, new ItemStack(Material.IRON_INGOT, 8));
        for (int slot : storage) {
            placed.menu.replaceExistingItem(slot, null);
        }
        if (storageFull) {
            for (int slot : placed.fillableSlots()) {
                placed.menu.replaceExistingItem(slot, new ItemStack(Material.IRON_INGOT, 8));
            }
        }
        player.setItemOnCursor(null);
        if (!storageFull) {
            player.setItemOnCursor(new ItemStack(Material.IRON_INGOT, 8));
        }
    }

    /**
     * @implNote The cursor goes back into the inventory when the window closes, so after a click the count
     *           is taken with the window closed and the cursor is already part of the inventory.
     */
    private static ItemTally tally(MachinePlacement.PlacedMenu placed, Player player, SimulatedWorld world, int[] storage) {
        ItemTally tally = new ItemTally()
            .addAll(player.getInventory().getContents())
            .add(player.getItemOnCursor())
            .addAll(world.drops());
        for (int slot : storage) {
            tally.add(placed.menu.getItemInSlot(slot));
        }
        return tally;
    }
}
