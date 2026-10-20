package io.github.slimefun5.harness;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;

/**
 * The worlds plugins create while the harness runs, each an empty in-memory world from core's checks sandbox.
 *
 * @implNote A world generator is never run: the harness proves a plugin enables and works with its world, not
 *           what that world looks like. Core is reached by reflection because its plugin class loader exists only
 *           once Slimefun is loaded, and every plugin that creates a world depends on Slimefun.
 */
public final class HarnessWorlds {

    private static final Logger LOG = Logger.getLogger("Minecraft");
    private static final String SLIMEFUN_PLUGIN = "Slimefun";
    private static final String SANDBOX_PACKAGE = "io.github.thebusybiscuit.slimefun5.core.checks.sandbox.";

    private static final List<String> created = new ArrayList<>();
    private static SandboxWorlds sandbox;

    private HarnessWorlds() {}

    /** Replaces {@code CraftServer#createWorld}, which needs a running game. */
    public static final class Creation {

        private Creation() {}

        public static World createWorld(WorldCreator creator) {
            return load(creator.name());
        }
    }

    static synchronized World load(String name) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            return existing;
        }
        World world = sandbox().load(name);
        created.add(name);
        return world;
    }

    /** Logs every call into a created world that nothing answered, so it fails the run. */
    static synchronized void reportGaps() {
        if (sandbox == null) {
            return;
        }
        for (String gap : sandbox.recordedGaps()) {
            LOG.log(Level.SEVERE, "[gap] {0} (in a world created by a plugin: {1})", new Object[] { gap, String.join(", ", created) });
        }
    }

    private static SandboxWorlds sandbox() {
        if (sandbox == null) {
            Plugin slimefun = Bukkit.getPluginManager().getPlugin(SLIMEFUN_PLUGIN);
            if (slimefun == null) {
                throw new IllegalStateException("A plugin created a world before Slimefun loaded, and only Slimefun's sandbox can supply one");
            }
            sandbox = new SandboxWorlds(slimefun.getClass().getClassLoader());
        }
        return sandbox;
    }

    private static final class SandboxWorlds {

        private final Object gaps;
        private final Method load;
        private final Method world;
        private final Method recorded;

        SandboxWorlds(ClassLoader slimefunClasses) {
            try {
                Class<?> checkGap = slimefunClasses.loadClass(SANDBOX_PACKAGE + "CheckGap");
                Class<?> sandboxWorld = slimefunClasses.loadClass(SANDBOX_PACKAGE + "SandboxWorld");
                gaps = checkGap.getConstructor().newInstance();
                load = sandboxWorld.getMethod("load", String.class, checkGap);
                world = sandboxWorld.getMethod("world");
                recorded = checkGap.getMethod("recorded");
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Slimefun has no checks sandbox, so the harness cannot create a world", failure);
            }
        }

        World load(String name) {
            try {
                return (World) world.invoke(load.invoke(null, name, gaps));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Core's sandbox could not create the world " + name, failure);
            }
        }

        @SuppressWarnings("unchecked")
        List<String> recordedGaps() {
            try {
                return (List<String>) recorded.invoke(gaps);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not read the gaps of the created worlds", failure);
            }
        }
    }
}
