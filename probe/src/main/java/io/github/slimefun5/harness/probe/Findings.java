package io.github.slimefun5.harness.probe;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.github.slimefun5.harness.simulation.HarnessContext;

/** Logs each problem as one SEVERE line: {@code [check:<check>] <subject>: <problem>}. */
final class Findings {

    private final Logger logger;
    private int count;

    Findings(Logger logger) {
        this.logger = logger;
    }

    /** A finding names what it is about itself, so the harness's step context is left off it. */
    void report(String check, String subject, String problem) {
        count++;
        String context = HarnessContext.current();
        HarnessContext.clear();
        try {
            logger.log(Level.SEVERE, "[check:" + check + "] " + subject + ": " + problem);
        } finally {
            HarnessContext.set(context);
        }
    }

    int count() {
        return count;
    }
}
