package io.github.slimefun5.harness.probe;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.thebusybiscuit.slimefun5.api.events.SlimefunItemRegistryFinalizedEvent;

/**
 * Runs every registry check once Slimefun has finished registering core's and every addon's content. Each
 * failure is logged at SEVERE, which the harness gate fails on unless it is allowlisted.
 */
public final class HarnessProbe extends JavaPlugin implements Listener {

    private final List<RegistryCheck> checks = Arrays.asList(
        new ItemNameCheck(),
        new ItemRegistrationCheck(),
        new RecipeCheck(),
        new MenuPresetCheck()
    );

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onRegistryFinalized(SlimefunItemRegistryFinalizedEvent event) {
        Findings findings = new Findings(getLogger());
        for (RegistryCheck check : checks) {
            try {
                check.run(findings);
            } catch (RuntimeException | LinkageError crash) {
                getLogger().log(Level.SEVERE, "[check:" + check.name() + "] the check itself crashed", crash);
            }
        }
        getLogger().info("Registry checks finished: " + findings.count() + " problem(s)");
    }
}
