package io.github.slimefun5.harness;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.Warning;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.file.YamlConfiguration;
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
    final Object console;
    final Object scheduler;

    private HarnessServer(Server server, Object console, Object scheduler) {
        this.server = server;
        this.console = console;
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
        Reflect.set(server, "configuration", bundledDefaults("configurations/bukkit.yml"));
        Reflect.set(server, "commandsConfiguration", bundledDefaults("configurations/commands.yml"));
        setWarningState(server);
        Object scheduler = Class.forName(layout.craft("scheduler.CraftScheduler")).getConstructor().newInstance();
        Reflect.set(server, "scheduler", scheduler);
        Reflect.set(server, "servicesManager", new SimpleServicesManager());
        Reflect.set(server, "messenger", new StandardMessenger());
        SimpleCommandMap commandMap = commandMap(layout, server);
        Reflect.set(server, "commandMap", commandMap);
        Reflect.set(server, "helpMap", Class.forName(layout.craft("help.SimpleHelpMap")).getConstructor(craftServer).newInstance(server));
        SimplePluginManager pluginManager = pluginManager(server, commandMap);
        Reflect.set(server, "pluginManager", pluginManager);
        attachPaperPluginManager(server, commandMap, pluginManager);
        Bukkit.setServer(server);
        registerConstructorStatics(layout, server);
        return new HarnessServer(server, console, scheduler);
    }

    /**
     * Repeats the data-only steps of the {@code CraftServer} constructor that the allocation skipped: touching
     * the NMS enchantment and effect classes registers their Bukkit wrappers, then registration is closed,
     * exactly as on a live server.
     */
    private static void registerConstructorStatics(ServerLayout layout, Server server) throws ReflectiveOperationException {
        if (layout.era != ServerLayout.Era.VERSIONED) {
            installCommodoreReroutes(layout, server);
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
     * Arms the bytecode rewriter that upgrades older plugins' API calls (1.20.5+), as the constructor does from
     * {@code bukkit.yml}'s compatibility list, which is empty by default.
     */
    private static void installCommodoreReroutes(ServerLayout layout, Server server) throws ReflectiveOperationException {
        Class<?> magicNumbers = Reflect.firstClass(layout.craft("util.CraftMagicNumbers"));
        if (magicNumbers == null || !hasMethod(magicNumbers, "getCommodore")) {
            return;
        }
        java.lang.reflect.Method getCommodore = Reflect.findMethod(magicNumbers, "getCommodore");
        Set<String> activeCompatibilities = new HashSet<>();
        Reflect.set(server, "activeCompatibilities", activeCompatibilities);
        Object commodore = getCommodore.invoke(magicNumbers.getField("INSTANCE").get(null));
        Predicate<String> isActive = activeCompatibilities::contains;
        commodore.getClass().getMethod("updateReroute", Predicate.class).invoke(commodore, isActive);
    }

    private static boolean hasMethod(Class<?> type, String name) {
        try {
            Reflect.findMethod(type, name);
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }

    /** The configuration a fresh server starts from: the defaults the version's own jar ships. */
    private static YamlConfiguration bundledDefaults(String resource) throws ReflectiveOperationException {
        InputStream in = HarnessServer.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new ReflectiveOperationException("The server jar ships no " + resource);
        }
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.setDefaults(defaults);
        configuration.options().copyDefaults(true);
        return configuration;
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

    /** {@code bukkit.yml}'s {@code settings.deprecated-verbose}, which registering any listener consults. */
    private static void setWarningState(Server server) throws ReflectiveOperationException {
        String configured = ((YamlConfiguration) Reflect.get(server, "configuration")).getString("settings.deprecated-verbose");
        Reflect.set(server, "warningState", Warning.WarningState.value(configured));
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
