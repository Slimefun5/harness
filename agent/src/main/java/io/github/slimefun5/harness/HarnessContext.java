package io.github.slimefun5.harness;

/** What the harness is doing right now, attached to every warning so a failure names the menu, slot or item behind it. */
final class HarnessContext {

    private static volatile String current;

    private HarnessContext() {}

    static void set(String description) {
        current = description;
    }

    static void clear() {
        current = null;
    }

    /** {@code " (during <context>)"}, or nothing outside any step. */
    static String suffix() {
        String context = current;
        return context == null ? "" : " (during " + context + ")";
    }
}
