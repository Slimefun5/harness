package io.github.slimefun5.harness.probe;

import java.util.Map;

import org.bukkit.ChatColor;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

/**
 * Every registered machine menu has a title and, when it fixes its own size, one a chest inventory can have.
 *
 * @implNote The stored title is not what a player reads: under the id-only rule it is the machine's baked
 *           name, and core translates it per viewer when the menu opens; {@link MenuTitleScenario} checks
 *           the title each viewer is shown.
 */
final class MenuPresetCheck implements RegistryCheck {

    /** {@link BlockMenuPreset#getSize()} before the size is inferred from the slots in use. */
    private static final int INFERRED_SIZE = -1;

    @Override
    public String name() {
        return "menu-preset";
    }

    @Override
    public void run(Findings findings) {
        for (Map.Entry<String, BlockMenuPreset> entry : Slimefun.getRegistry().getMenuPresets().entrySet()) {
            BlockMenuPreset preset = entry.getValue();
            String title = preset.getTitle() == null ? "" : ChatColor.stripColor(preset.getTitle()).trim();
            if (title.isEmpty()) {
                findings.report(this, entry.getKey(), "has an empty title");
            }
            int size = preset.getSize();
            if (size != INFERRED_SIZE && (size < 9 || size > 54 || size % 9 != 0)) {
                findings.report(this, entry.getKey(), "has a size of " + size + ", which no chest inventory has");
            }
        }
    }
}
