package io.github.slimefun5.harness.probe;

/** One check derived from what is registered; it never needs code for a particular item. */
interface RegistryCheck {

    /** Stable, used in every finding as {@code [check:<name>]} so the allowlist can address it. */
    String name();

    void run(Findings findings);
}
