package io.github.slimefun5.harness.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;

import org.bukkit.entity.Player;

/**
 * What a {@link Scenario} acts through once every plugin is enabled: one in-memory world, players who join
 * it, and the virtual clock.
 */
public final class Simulation {

    private static final String WORLD_NAME = "harness_world";
    private static final List<Player> ONLINE = new CopyOnWriteArrayList<>();

    private final IntConsumer clock;
    private SimulatedWorld world;

    public Simulation(IntConsumer clock) {
        this.clock = clock;
    }

    /** The server's only world, created and announced to the plugins on first use. */
    public SimulatedWorld world() {
        if (world == null) {
            world = SimulatedWorld.load(WORLD_NAME);
        }
        return world;
    }

    /**
     * A player who is online in {@link #world()} from now on.
     *
     * @param locale the client's locale, such as {@code de_de}; the language a plugin reads from the client
     */
    public SimulatedPlayer join(String name, String locale) {
        SimulatedPlayer player = SimulatedPlayer.create(name, locale, world());
        ONLINE.add(player.player());
        return player;
    }

    public void quit(SimulatedPlayer player) {
        player.player().closeInventory();
        ONLINE.remove(player.player());
    }

    /** Runs {@code ticks} server ticks of scheduled tasks, as fast as they run. */
    public void advanceTicks(int ticks) {
        clock.accept(ticks);
    }

    public static List<Player> onlinePlayers() {
        return new ArrayList<>(ONLINE);
    }

    public static Player onlinePlayer(UUID uniqueId) {
        for (Player player : ONLINE) {
            if (player.getUniqueId().equals(uniqueId)) {
                return player;
            }
        }
        return null;
    }

    public static Player onlinePlayer(String name) {
        for (Player player : ONLINE) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }
}
