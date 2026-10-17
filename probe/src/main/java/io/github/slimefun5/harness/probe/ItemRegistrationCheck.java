package io.github.slimefun5.harness.probe;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.researches.Research;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/** Every enabled item has a real stack, sits in a registered item group, and unlocks through a registered research. */
final class ItemRegistrationCheck implements RegistryCheck {

    @Override
    public String name() {
        return "item-registration";
    }

    @Override
    public void run(Findings findings) {
        Set<ItemGroup> groups = new HashSet<>(Slimefun.getRegistry().getAllItemGroups());
        Set<Research> researches = new HashSet<>(Slimefun.getRegistry().getResearches());
        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            ItemStack stack = item.getItem();
            if (stack == null || stack.getType() == Material.AIR) {
                findings.report(this, item.getId(), "has no item stack");
            }
            if (item.getItemGroup() == null || !groups.contains(item.getItemGroup())) {
                findings.report(this, item.getId(), "is in an item group that is not registered");
            }
            Research research = item.getResearch();
            if (research != null && !researches.contains(research)) {
                findings.report(this, item.getId(), "unlocks through research " + research.getKey() + ", which is not registered");
            }
        }
    }
}
