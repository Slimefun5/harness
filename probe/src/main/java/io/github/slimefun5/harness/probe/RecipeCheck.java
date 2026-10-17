package io.github.slimefun5.harness.probe;

import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Every enabled item's declared recipe can actually be followed: it has a recipe type, it yields something,
 * and no Slimefun ingredient is unregistered or disabled, which would leave the item uncraftable.
 */
final class RecipeCheck implements RegistryCheck {

    @Override
    public String name() {
        return "recipe";
    }

    @Override
    public void run(Findings findings) {
        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            RecipeType type = item.getRecipeType();
            if (type == null) {
                findings.report(this, item.getId(), "has no recipe type");
                continue;
            }
            if (type == RecipeType.NULL) {
                continue;
            }
            ItemStack output = item.getRecipeOutput();
            if (output == null || output.getType() == Material.AIR || output.getAmount() < 1) {
                findings.report(this, item.getId(), "has a recipe that yields nothing");
            }
            ItemStack[] recipe = item.getRecipe();
            if (recipe == null) {
                continue;
            }
            for (ItemStack ingredient : recipe) {
                checkIngredient(findings, item, ingredient);
            }
        }
    }

    private void checkIngredient(Findings findings, SlimefunItem item, ItemStack ingredient) {
        if (ingredient == null || ingredient.getType() == Material.AIR) {
            return;
        }
        Optional<String> id = Slimefun.getItemDataService().getItemData(ingredient);
        if (!id.isPresent()) {
            return;
        }
        SlimefunItem used = SlimefunItem.getById(id.get());
        if (used == null) {
            findings.report(this, item.getId(), "needs " + id.get() + ", which is not registered");
        } else if (used.isDisabled()) {
            findings.report(this, item.getId(), "needs " + id.get() + ", which is disabled on this server");
        }
    }
}
