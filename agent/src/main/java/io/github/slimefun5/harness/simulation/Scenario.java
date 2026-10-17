package io.github.slimefun5.harness.simulation;

/**
 * Something the harness runs against the enabled plugins, after start-up. A plugin offers one by registering
 * it with the server's {@code ServicesManager} under this interface.
 */
public interface Scenario {

    String name();

    void run(Simulation simulation) throws Exception;
}
