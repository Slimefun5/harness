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
        }
    }

    /**
     * Loads vanilla's built-in datapack into the allocated console through this version's compiled loader,
     * then gives {@code CraftRegistry} the console's registry access exactly as the {@code CraftServer}
     * constructor does. A version with no loader gets only the built-in registries.
     *
     * @implNote From 1.20.5 item meta resolves its item type through {@code CraftRegistry}, so every Mojang-
     *           mapped version needs one of the two.
     */
    static void loadVanillaData(ServerLayout layout, Object console) throws ReflectiveOperationException {
        Class<?> loader = Reflect.firstClass(DATAPACK_LOADER);
        if (loader != null) {
            loader.getMethod("load", Object.class).invoke(null, console);
            if (layout.era == ServerLayout.Era.MOJANG_MAPPED) {
                Object access = Reflect.findMethod(console.getClass(), "registryAccess").invoke(console);
                setCraftRegistry(layout, access);
            }
        } else if (layout.era == ServerLayout.Era.MOJANG_MAPPED) {
            installBuiltInRegistries(layout);
        }
    }

    private static final String DATAPACK_LOADER = "io.github.slimefun5.harness.loader.VanillaDatapack";

    private static void setCraftRegistry(ServerLayout layout, Object access) throws ReflectiveOperationException {
        Class<?> registryAccess = Class.forName("net.minecraft.core.RegistryAccess");
        Class.forName(layout.craft("CraftRegistry")).getMethod("setMinecraftRegistry", registryAccess).invoke(null, access);
    }

    private static void installBuiltInRegistries(ServerLayout layout) throws ReflectiveOperationException {
        Class<?> registryAccess = Class.forName("net.minecraft.core.RegistryAccess");
        Class<?> registry = Class.forName("net.minecraft.core.Registry");
        Object registryOfRegistries = Class.forName("net.minecraft.core.registries.BuiltInRegistries").getField("REGISTRY").get(null);
        Object access = registryAccess.getMethod("fromRegistryOfRegistries", registry).invoke(null, registryOfRegistries);
        setCraftRegistry(layout, registryAccess.getMethod("freeze").invoke(access));
    }
}
