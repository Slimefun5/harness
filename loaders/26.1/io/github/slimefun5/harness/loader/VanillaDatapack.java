package io.github.slimefun5.harness.loader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.Executor;

import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.Util;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Loads vanilla's built-in datapack (registries, tags, item components, recipes, loot) the way {@code Main}
 * does before any world exists, and hands the result to the allocated server. 26.1 and later.
 */
public final class VanillaDatapack {

    private VanillaDatapack() {}

    public static void load(Object console) throws Exception {
        WorldLoader.PackConfig packs = new WorldLoader.PackConfig(
            ServerPacksSource.createVanillaTrustedRepository(), WorldDataConfiguration.DEFAULT, false, true);
        WorldLoader.InitConfig config = new WorldLoader.InitConfig(
            packs, Commands.CommandSelection.DEDICATED, LevelBasedPermissionSet.GAMEMASTER);
        Executor sameThread = Runnable::run;
        Loaded loaded = WorldLoader.<Void, Loaded>load(config, VanillaDatapack::defaultDimensions, Loaded::new,
            Util.backgroundExecutor(), sameThread).get();
        install((MinecraftServer) console, loaded);
    }

    private static WorldLoader.DataLoadOutput<Void> defaultDimensions(WorldLoader.DataLoadContext context) {
        Registry<LevelStem> levelStems = context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM);
        return new WorldLoader.DataLoadOutput<>(null,
            WorldPresets.createNormalWorldDimensions(context.datapackWorldgen()).bake(levelStems).dimensionsRegistryAccess());
    }

    /** @implNote {@code ReloadableResources} is package-private from 26.1, so it is built by reflection. */
    private static void install(MinecraftServer console, Loaded loaded) throws ReflectiveOperationException {
        set(console, "registries", loaded.registries);
        Class<?> reloadable = Class.forName("net.minecraft.server.MinecraftServer$ReloadableResources");
        Constructor<?> constructor = reloadable.getDeclaredConstructor(CloseableResourceManager.class, ReloadableServerResources.class);
        constructor.setAccessible(true);
        set(console, "resources", constructor.newInstance(loaded.resourceManager, loaded.resources));
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = MinecraftServer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Loaded {
        final CloseableResourceManager resourceManager;
        final ReloadableServerResources resources;
        final LayeredRegistryAccess<RegistryLayer> registries;

        Loaded(CloseableResourceManager resourceManager, ReloadableServerResources resources,
                LayeredRegistryAccess<RegistryLayer> registries, Void cookie) {
            this.resourceManager = resourceManager;
            this.resources = resources;
            this.registries = registries;
        }
    }
}
