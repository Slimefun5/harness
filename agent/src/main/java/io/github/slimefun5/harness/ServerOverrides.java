package io.github.slimefun5.harness;

import java.io.File;
import java.util.List;
import java.util.UUID;

import net.bytebuddy.implementation.bind.annotation.RuntimeType;

import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import io.github.slimefun5.harness.simulation.Simulation;

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
            return Simulation.onlinePlayers();
        }
    }

    /** A live server looks players up in its player list, which only real connections fill. */
    public static final class PlayerLookup {

        private PlayerLookup() {}

        public static Player getPlayer(UUID uniqueId) {
            return Simulation.onlinePlayer(uniqueId);
        }

        public static Player getPlayer(String name) {
            return Simulation.onlinePlayer(name);
        }

        public static Player getPlayerExact(String name) {
            return Simulation.onlinePlayer(name);
        }
    }

    public static final class ConsoleSender {

        private ConsoleSender() {}

        public static ConsoleCommandSender getConsoleSender() {
            return HarnessConsole.get();
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
            return Simulation.onlinePlayers().toArray(new Player[0]);
        }
    }
}
