package io.github.slimefun5.harness.probe;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Logs each problem as one SEVERE line: {@code [check:<check>] <subject>: <problem>}. */
final class Findings {

    private final Logger logger;
    private int count;

    Findings(Logger logger) {
        this.logger = logger;
    }

    void report(RegistryCheck check, String subject, String problem) {
        count++;
        logger.log(Level.SEVERE, "[check:" + check.name() + "] " + subject + ": " + problem);
    }

    int count() {
        return count;
    }
}
