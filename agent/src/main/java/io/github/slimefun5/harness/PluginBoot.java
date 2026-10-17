package io.github.slimefun5.harness;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Loads real plugin jars the way a server's start-up does, then enables them in the given order. */
final class PluginBoot {

    private PluginBoot() {}

    static List<Plugin> loadAndEnable(PluginManager manager, List<File> jars, File pluginsFolder, WarningLog log) throws IOException {
        pluginsFolder.mkdirs();
        List<Plugin> loaded = new ArrayList<>();
        for (File jar : jars) {
            File copy = new File(pluginsFolder, jar.getName());
            Files.copy(jar.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try {
                Plugin plugin = manager.loadPlugin(copy);
                plugin.onLoad();
                loaded.add(plugin);
            } catch (Throwable failure) {
                log.harnessFailure("could not load " + jar.getName(), failure);
            }
        }
        for (Plugin plugin : loaded) {
            try {
                manager.enablePlugin(plugin);
            } catch (Throwable failure) {
                log.harnessFailure("could not enable " + plugin.getName(), failure);
            }
        }
        return loaded;
    }
}
