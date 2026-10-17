package io.github.slimefun5.harness;

/**
 * Loads vanilla's static game data (blocks, items, materials, components) and nothing else: no world, no
 * network, no ticking.
 */
final class GameBootstrap {

    private GameBootstrap() {}

    static void run(ServerLayout layout) throws ReflectiveOperationException {
        switch (layout.era) {
            case VERSIONED:
                Reflect.invokeStatic(layout.legacyNmsPackage + ".DispenserRegistry", "c");
                break;
            case SPIGOT_MAPPED:
                Reflect.invokeStatic("net.minecraft.SharedConstants", "a");
                Reflect.invokeFirstStatic("net.minecraft.server.DispenserRegistry", "init", "a");
                break;
            default:
                Reflect.invokeStatic("net.minecraft.SharedConstants", "tryDetectVersion");
                Reflect.invokeStatic("net.minecraft.server.Bootstrap", "bootStrap");
                installBuiltInRegistries(layout);
        }
    }

    /**
     * @implNote From 1.20.5 item meta resolves its item type through {@code CraftRegistry}, which a live
     *           server fills with the full registry access during world load. The built-in registries are
     *           enough for items and materials.
     */
    private static void installBuiltInRegistries(ServerLayout layout) throws ReflectiveOperationException {
        Class<?> registryAccess = Class.forName("net.minecraft.core.RegistryAccess");
        Class<?> registry = Class.forName("net.minecraft.core.Registry");
        Object registryOfRegistries = Class.forName("net.minecraft.core.registries.BuiltInRegistries").getField("REGISTRY").get(null);
        Object access = registryAccess.getMethod("fromRegistryOfRegistries", registry).invoke(null, registryOfRegistries);
        Object frozen = registryAccess.getMethod("freeze").invoke(access);
        Class.forName(layout.craft("CraftRegistry"))
            .getMethod("setMinecraftRegistry", registryAccess).invoke(null, frozen);
    }
}
