package io.github.slimefun5.harness;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.SimpleServicesManager;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.plugin.messaging.StandardMessenger;

/**
 * The version's real {@code CraftServer}, allocated without its constructor and given its own real
 * subsystems (scheduler, plugin manager, services, messenger, commands). Everything that only needs data
 * behaves as on a live server; what needs a running game is rewritten by {@link HarnessAgent}.
 */
final class HarnessServer {

    final Server server;
    final Object scheduler;

    private HarnessServer(Server server, Object scheduler) {
        this.server = server;
        this.scheduler = scheduler;
    }

    static HarnessServer install(ServerLayout layout, String version, Logger logger) throws ReflectiveOperationException {
        Class<?> craftServer = Class.forName(layout.craft("CraftServer"));
        Server server = (Server) Reflect.allocate(craftServer);
        Reflect.set(server, "logger", logger);
        Object console = Reflect.allocate(dedicatedServerClass(layout));
        Reflect.set(server, "console", console);
        linkConsole(console, server);
        Reflect.set(server, "serverName", "Harness");
        Reflect.set(server, "serverVersion", version);
        Reflect.set(server, "bukkitVersion", version + "-R0.1-SNAPSHOT");
        Reflect.set(server, "worlds", new LinkedHashMap<String, Object>());
        setDefaultMinimumApi(server);
        Object scheduler = Class.forName(layout.craft("scheduler.CraftScheduler")).getConstructor().newInstance();
        Reflect.set(server, "scheduler", scheduler);
        Reflect.set(server, "servicesManager", new SimpleServicesManager());
        Reflect.set(server, "messenger", new StandardMessenger());
        SimpleCommandMap commandMap = commandMap(layout, server);
        Reflect.set(server, "commandMap", commandMap);
        SimplePluginManager pluginManager = pluginManager(server, commandMap);
        Reflect.set(server, "pluginManager", pluginManager);
        attachPaperPluginManager(server, commandMap, pluginManager);
        Bukkit.setServer(server);
        registerConstructorStatics(layout);
        return new HarnessServer(server, scheduler);
    }

    /**
     * Repeats the data-only steps of the {@code CraftServer} constructor that the allocation skipped: touching
     * the NMS enchantment and effect classes registers their Bukkit wrappers, then registration is closed,
     * exactly as on a live server.
     */
    private static void registerConstructorStatics(ServerLayout layout) throws ReflectiveOperationException {
        if (layout.era != ServerLayout.Era.VERSIONED) {
            return;
        }
        ClassLoader loader = HarnessServer.class.getClassLoader();
        Class.forName(layout.legacyNmsPackage + ".Enchantment", true, loader);
        Class.forName(layout.legacyNmsPackage + ".MobEffectList", true, loader);
        Class<?> brewer = Reflect.firstClass(layout.craft("potion.CraftPotionBrewer"));
        if (brewer != null) {
            Class<?> brewerInterface = Class.forName("org.bukkit.potion.PotionBrewer");
            Class.forName("org.bukkit.potion.Potion").getMethod("setPotionBrewer", brewerInterface)
                .invoke(null, brewer.getConstructor().newInstance());
        }
        Reflect.invokeStatic("org.bukkit.enchantments.Enchantment", "stopAcceptingRegistrations");
        Reflect.invokeStatic("org.bukkit.potion.PotionEffectType", "stopAcceptingRegistrations");
    }

    private static Class<?> dedicatedServerClass(ServerLayout layout) {
        return Reflect.firstClass(layout.legacyNmsPackage + ".DedicatedServer", "net.minecraft.server.dedicated.DedicatedServer");
    }

    private static SimpleCommandMap commandMap(ServerLayout layout, Server server) throws ReflectiveOperationException {
        Class<?> craftCommandMap = Reflect.firstClass(layout.craft("command.CraftCommandMap"));
        Class<?> type = craftCommandMap != null ? craftCommandMap : SimpleCommandMap.class;
        Constructor<?> constructor = type.getConstructor(Server.class);
        return (SimpleCommandMap) constructor.newInstance(server);
    }

    private static SimplePluginManager pluginManager(Server server, SimpleCommandMap commandMap) {
        SimplePluginManager manager = new SimplePluginManager(server, commandMap);
        manager.registerInterface(JavaPluginLoader.class);
        return manager;
    }

    /**
     * The console a live server is: {@code MinecraftServer.getServer()} returns it and its {@code server}
     * field is the {@code CraftServer}, both read by version checks such as {@code CraftMagicNumbers}.
     */
    private static void linkConsole(Object console, Server server) throws ReflectiveOperationException {
        Reflect.set(console, "server", server);
        Class<?> minecraftServer = console.getClass();
        while (minecraftServer.getSuperclass() != null && !minecraftServer.getSimpleName().equals("MinecraftServer")) {
            minecraftServer = minecraftServer.getSuperclass();
        }
        Reflect.setStaticOfType(minecraftServer, minecraftServer, console);
    }

    /**
     * {@code bukkit.yml}'s default {@code minimum-api: none}, which plugin loading compares every
     * {@code api-version} against: a string up to 1.19, an {@code ApiVersion} after.
     */
    private static void setDefaultMinimumApi(Server server) throws ReflectiveOperationException {
        java.lang.reflect.Field field = Reflect.field(server.getClass(), "minimumAPI");
        if (field == null) {
            return;
        }
        Object none = field.getType() == String.class ? "none" : field.getType().getField("NONE").get(null);
        field.set(server, none);
    }

    /** What Paper's {@code Main} builds from the {@code --plugins} launch option. */
    private static void installPaperPluginDirectories() throws ReflectiveOperationException {
        Class<?> initializer = Class.forName("io.papermc.paper.plugin.PluginInitializerManager");
        Constructor<?> constructor = initializer.getDeclaredConstructor(Path.class, Path.class);
        constructor.setAccessible(true);
        Path plugins = Paths.get("plugins").toAbsolutePath();
        Object instance = constructor.newInstance(plugins, plugins.resolve("update"));
        Reflect.setStaticOfType(initializer, initializer, instance);
    }

    /** Paper 1.19.3+ loads every plugin through its own manager, which {@code SimplePluginManager} forwards to. */
    private static void attachPaperPluginManager(Server server, SimpleCommandMap commandMap, SimplePluginManager manager)
            throws ReflectiveOperationException {
        Class<?> paperManager = Reflect.firstClass("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
        if (paperManager == null) {
            return;
        }
        installPaperPluginDirectories();
        Object instance = paperManager.getConstructor(Server.class, Class.forName("org.bukkit.command.CommandMap"), SimplePluginManager.class)
            .newInstance(server, commandMap, manager);
        Reflect.set(server, "paperPluginManager", instance);
        Reflect.set(manager, "paperPluginManager", instance);
    }
}
