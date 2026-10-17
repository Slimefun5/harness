package io.github.slimefun5.harness.probe;

import java.util.Optional;

import io.github.slimefun5.harness.simulation.SimulatedPlayer;
import io.github.slimefun5.harness.simulation.Simulation;
import io.github.thebusybiscuit.slimefun5.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun5.api.researches.Research;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/** A player whose Slimefun profile is loaded and has every research, so no item refuses them. */
final class ResearchedPlayer {

    /** Profiles load on Slimefun's own thread, so the wait is real time, not ticks. */
    private static final long PROFILE_TIMEOUT_MILLIS = 10_000;

    private ResearchedPlayer() {}

    static SimulatedPlayer join(Simulation simulation, String name, String locale) throws InterruptedException {
        SimulatedPlayer player = simulation.join(name, locale);
        PlayerProfile.request(player.player());
        long deadline = System.currentTimeMillis() + PROFILE_TIMEOUT_MILLIS;
        Optional<PlayerProfile> profile = PlayerProfile.find(player.player());
        while (!profile.isPresent()) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("The profile of " + name + " did not load in " + PROFILE_TIMEOUT_MILLIS + " ms");
            }
            Thread.sleep(5);
            simulation.advanceTicks(1);
            profile = PlayerProfile.find(player.player());
        }
        for (Research research : Slimefun.getRegistry().getResearches()) {
            profile.get().setResearched(research, true);
        }
        return player;
    }
}
