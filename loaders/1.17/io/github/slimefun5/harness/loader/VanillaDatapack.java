package io.github.slimefun5.harness.loader;

import java.lang.reflect.Field;

import net.minecraft.SystemUtils;
import net.minecraft.commands.CommandDispatcher;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.server.DataPackResources;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.EnumResourcePackType;
import net.minecraft.server.packs.repository.ResourcePackRepository;
import net.minecraft.server.packs.repository.ResourcePackSourceVanilla;
import net.minecraft.world.level.DataPackConfiguration;

/**
 * Loads vanilla's built-in datapack (recipes, tags, loot) the way {@code Main} does before any world exists, and
 * hands the result to the allocated server. 1.17.x, whose server members carry Spigot's obfuscated names.
 */
public final class VanillaDatapack {

    /** {@code server.properties}' default {@code function-permission-level}. */
    private static final int DEFAULT_FUNCTION_PERMISSION_LEVEL = 2;

    private VanillaDatapack() {}

    public static void load(Object console) throws Exception {
        MinecraftServer server = (MinecraftServer) console;
        IRegistryCustom.Dimension registries = server.l != null ? server.l : IRegistryCustom.a();
        ResourcePackRepository packs = new ResourcePackRepository(EnumResourcePackType.b, new ResourcePackSourceVanilla());
        MinecraftServer.a(packs, DataPackConfiguration.a, false);
        DataPackResources resources = DataPackResources.a(packs.f(), registries, CommandDispatcher.ServerType.b,
            DEFAULT_FUNCTION_PERMISSION_LEVEL, SystemUtils.f(), Runnable::run).get();
        resources.j();
        if (server.l == null) {
            setRegistries(server, registries);
        }
        server.aB = resources;
    }

    /** The constructor a harness server never runs is what sets this final field. */
    private static void setRegistries(MinecraftServer server, IRegistryCustom.Dimension registries) throws ReflectiveOperationException {
        Field field = MinecraftServer.class.getDeclaredField("l");
        field.setAccessible(true);
        field.set(server, registries);
    }
}
