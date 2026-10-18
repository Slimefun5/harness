package io.github.slimefun5.harness.probe;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.slimefun5.harness.simulation.Scenario;

/** Offers the harness the scenarios that need simulated players and blocks. */
public final class HarnessProbe extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getServicesManager().register(Scenario.class, new MenuTitleScenario(getLogger()), this, ServicePriority.Normal);
        getServer().getServicesManager().register(Scenario.class, new MenuClickScenario(getLogger()), this, ServicePriority.Normal);
    }
}
