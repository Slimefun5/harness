package io.github.slimefun5.harness;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Where this version keeps its classes, which decides how it is bootstrapped. */
final class ServerLayout {

    enum Era {
        /** 1.8 to 1.16: everything under {@code net.minecraft.server.<version>}. */
        VERSIONED,
        /** 1.17 to 1.20.4: Mojang packages, Spigot class names, obfuscated members. */
        SPIGOT_MAPPED,
        /** 1.20.5 and later on Paper: Mojang names throughout. */
        MOJANG_MAPPED
    }

    final Era era;
    /** {@code org.bukkit.craftbukkit} or {@code org.bukkit.craftbukkit.v1_8_R3}, without a trailing dot. */
    final String craftPackage;
    /** The pre-1.17 NMS package, or {@code null} from 1.17 on. */
    final String legacyNmsPackage;

    private ServerLayout(Era era, String craftPackage, String legacyNmsPackage) {
        this.era = era;
        this.craftPackage = craftPackage;
        this.legacyNmsPackage = legacyNmsPackage;
    }

    static ServerLayout detect() throws IOException {
        String craftPackage = Reflect.exists("org.bukkit.craftbukkit.CraftServer")
            ? "org.bukkit.craftbukkit" : versionedCraftPackage();
        if (Reflect.exists("net.minecraft.server.Bootstrap")) {
            return new ServerLayout(Era.MOJANG_MAPPED, craftPackage, null);
        }
        if (Reflect.exists("net.minecraft.server.DispenserRegistry")) {
            return new ServerLayout(Era.SPIGOT_MAPPED, craftPackage, null);
        }
        String version = craftPackage.substring(craftPackage.lastIndexOf('.') + 1);
        return new ServerLayout(Era.VERSIONED, craftPackage, "net.minecraft.server." + version);
    }

    String craft(String simpleName) {
        return craftPackage + '.' + simpleName;
    }

    private static String versionedCraftPackage() throws IOException {
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (!entry.endsWith(".jar")) {
                continue;
            }
            try (JarFile jar = new JarFile(entry)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.startsWith("org/bukkit/craftbukkit/v") && name.endsWith("/CraftServer.class")) {
                        return name.substring(0, name.length() - "/CraftServer.class".length()).replace('/', '.');
                    }
                }
            }
        }
        throw new IllegalStateException("No CraftServer on the classpath");
    }
}
