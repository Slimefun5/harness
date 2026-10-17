package io.github.slimefun5.harness;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Loads the plugin jars exactly as a server's start-up does: all of them from the plugins folder in one
 * {@code loadPlugins} call, which builds the dependency graph and the load order, then {@code onLoad} and
 * enable in that order.
 */
final class PluginBoot {

    private PluginBoot() {}

    static Plugin[] loadAndEnable(PluginManager manager, List<File> jars, File pluginsFolder, WarningLog log) throws IOException {
        pluginsFolder.mkdirs();
        for (File jar : jars) {
            Files.copy(jar.toPath(), new File(pluginsFolder, jar.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        Plugin[] plugins = manager.loadPlugins(pluginsFolder);
        if (plugins.length < jars.size()) {
            log.harnessFailure("loaded " + plugins.length + " of " + jars.size() + " plugin jars",
                new IllegalStateException("see the server log above for why a jar was rejected"));
        }
        for (Plugin plugin : plugins) {
            try {
                plugin.onLoad();
            } catch (Throwable failure) {
                log.harnessFailure("onLoad failed for " + plugin.getName(), failure);
            }
        }
        for (Plugin plugin : plugins) {
            try {
                manager.enablePlugin(plugin);
            } catch (Throwable failure) {
                log.harnessFailure("could not enable " + plugin.getName(), failure);
            }
        }
        return plugins;
    }
}
