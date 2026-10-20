package io.github.slimefun5.harness;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Replacement bodies for {@code CraftServer} methods that read state only a running game has. */
public final class ServerOverrides {

    private ServerOverrides() {}

    /** The harness runs everything on its own main thread, which plays the server thread. */
    public static final class PrimaryThread {

        static volatile Thread mainThread;

        private PrimaryThread() {}

        public static boolean isPrimaryThread() {
            return Thread.currentThread() == mainThread;
        }
    }

    /**
     * @implNote Returns a {@code List} so it binds to every declared form: {@code Collection} on modern
     *           versions and Paper 1.8.8's {@code List<CraftPlayer>}, which its {@code Collection} bridge calls.
     */
    public static final class OnlinePlayers {

        private OnlinePlayers() {}

        public static List<?> getOnlinePlayers() {
            return SandboxPlayers.online();
        }
    }

    /** A live server looks players up in its player list, which only real connections fill. */
    public static final class PlayerLookup {

        private PlayerLookup() {}

        public static Player getPlayer(UUID uniqueId) {
            return SandboxPlayers.byId(uniqueId);
        }

        public static Player getPlayer(String name) {
            return SandboxPlayers.byName(name);
        }

        public static Player getPlayerExact(String name) {
            return SandboxPlayers.byName(name);
        }
    }

    public static final class OfflinePlayerLookup {

        private OfflinePlayerLookup() {}

        public static OfflinePlayer getOfflinePlayer(UUID uniqueId) {
            Player online = SandboxPlayers.byId(uniqueId);
            return online != null ? online : OfflinePlayers.byId(uniqueId);
        }

        public static OfflinePlayer getOfflinePlayer(String name) {
            Player online = SandboxPlayers.byName(name);
            return online != null ? online : OfflinePlayers.byName(name);
        }
    }

    /**
     * Paper decides "main thread" by the thread's class, a {@code TickThread} the harness's main thread is not.
     */
    public static final class TickThreadCheck {

        private TickThreadCheck() {}

        public static boolean isTickThread() {
            return PrimaryThread.isPrimaryThread();
        }
    }

    /** Spigot compares against the server's own thread, which only a running game starts; still catches async calls. */
    public static final class AsyncCatch {

        private AsyncCatch() {}

        public static void catchOp(String reason) {
            if (!PrimaryThread.isPrimaryThread()) {
                throw new IllegalStateException("Asynchronous " + reason + "!");
            }
        }
    }

    public static final class ConsoleSender {

        private ConsoleSender() {}

        public static ConsoleCommandSender getConsoleSender() {
            return HarnessConsole.get();
        }
    }

    /**
     * Keeps a recipe that was registered when only the resend to online players failed: from 1.21.2 that resend
     * always goes through a player list only a running game has, after the recipe is already in the manager.
     */
    public static final class RecipeResend {

        private RecipeResend() {}

        @Advice.OnMethodExit(onThrowable = NullPointerException.class)
        static void exit(@Advice.This Object server, @Advice.Thrown(readOnly = false) Throwable thrown,
                         @Advice.Return(readOnly = false) boolean added) {
            if (thrown != null && hasNoPlayerList(server)) {
                thrown = null;
                added = true;
            }
        }

        public static boolean hasNoPlayerList(Object server) {
            try {
                java.lang.reflect.Field playerList = server.getClass().getDeclaredField("playerList");
                playerList.setAccessible(true);
                return playerList.get(server) == null;
            } catch (ReflectiveOperationException absent) {
                return false;
            }
        }
    }

    /** Paper 1.20.5+ reads it from {@code server.properties} whenever a {@code WorldCreator} is constructed. */
    public static final class MainLevelName {

        private MainLevelName() {}

        public static String getMainLevelName() {
            return "world";
        }
    }

    /** Paper 1.17+ reads the folder from launch options a harness never has. */
    public static final class PluginsFolder {

        private PluginsFolder() {}

        public static File getPluginsFolder() {
            return new File("plugins").getAbsoluteFile();
        }
    }

    /**
     * Paper 1.20.5+ backs the command map with a Brigadier dispatcher a live server builds from vanilla's
     * commands; an empty real one lets plugin commands register through Paper's real bridge.
     */
    public static final class CommandDispatcherHolder {

        private static Object dispatcher;

        private CommandDispatcherHolder() {}

        @RuntimeType
        public static synchronized Object getDispatcherInternal() throws ReflectiveOperationException {
            if (dispatcher == null) {
                dispatcher = Class.forName("com.mojang.brigadier.CommandDispatcher").getConstructor().newInstance();
            }
            return dispatcher;
        }
    }

    /** Paper 1.8.8 still carries the pre-1.7.10 {@code Player[]} form. */
    public static final class OnlinePlayerArray {

        private OnlinePlayerArray() {}

        public static Player[] getOnlinePlayers() {
            return SandboxPlayers.online().toArray(new Player[0]);
        }
    }

    /**
     * The players of core's checks sandbox, the only players a harness run has.
     *
     * @implNote Core is loaded by its plugin's class loader long after this agent, so its {@code Sandbox} is
     *           looked up by name once Slimefun is loaded; before that there is no sandbox and so no player.
     */
    static final class SandboxPlayers {

        private static final String SLIMEFUN_PLUGIN = "Slimefun";
        private static final String SANDBOX_CLASS = "io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox";

        private static volatile SandboxMethods methods;

        private SandboxPlayers() {}

        @SuppressWarnings("unchecked")
        static List<Player> online() {
            SandboxMethods resolved = methods();
            return resolved == null ? Collections.<Player>emptyList() : (List<Player>) invoke(resolved.online);
        }

        static Player byId(UUID uniqueId) {
            SandboxMethods resolved = methods();
            return resolved == null ? null : (Player) invoke(resolved.byId, uniqueId);
        }

        static Player byName(String name) {
            SandboxMethods resolved = methods();
            return resolved == null ? null : (Player) invoke(resolved.byName, name);
        }

        private static SandboxMethods methods() {
            if (methods == null) {
                PluginManager plugins = Bukkit.getServer() == null ? null : Bukkit.getPluginManager();
                Plugin slimefun = plugins == null ? null : plugins.getPlugin(SLIMEFUN_PLUGIN);
                if (slimefun == null) {
                    return null;
                }
                methods = new SandboxMethods(slimefun.getClass().getClassLoader());
            }
            return methods;
        }

        private static Object invoke(Method method, Object... arguments) {
            try {
                return method.invoke(null, arguments);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not ask core's sandbox for its players", failure);
            }
        }
    }

    private static final class SandboxMethods {

        final Method online;
        final Method byId;
        final Method byName;

        SandboxMethods(ClassLoader slimefunClasses) {
            try {
                Class<?> sandbox = slimefunClasses.loadClass(SandboxPlayers.SANDBOX_CLASS);
                online = sandbox.getMethod("onlinePlayers");
                byId = sandbox.getMethod("onlinePlayer", UUID.class);
                byName = sandbox.getMethod("onlinePlayer", String.class);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Slimefun has no checks sandbox, so the harness has no players to offer", failure);
            }
        }
    }
}
