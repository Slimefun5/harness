package io.github.slimefun5.harness.simulation;

/** What the harness is doing right now, attached to every warning so a failure names the menu, slot or item behind it. */
public final class HarnessContext {

    private static volatile String current;

    private HarnessContext() {}

    public static void set(String description) {
        current = description;
    }

    public static void clear() {
        current = null;
    }

    public static String current() {
        return current;
    }

    /** {@code " (during <context>)"}, or nothing outside any scenario step. */
    public static String suffix() {
        String context = current;
        return context == null ? "" : " (during " + context + ")";
    }
}
