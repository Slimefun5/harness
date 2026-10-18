plugins {
    java
    id("com.gradleup.shadow") version "9.3.2"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    // The oldest supported API: anything the agent calls directly must exist on every version. Everything
    // version-specific is reached by reflection against the real server jar on the classpath at run time.
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT") {
        isTransitive = false
    }
    implementation("net.bytebuddy:byte-buddy:1.17.5")
    implementation("net.bytebuddy:byte-buddy-agent:1.17.5")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    jar {
        // The harness runs the shadow jar, which must not share this one's file.
        archiveClassifier.set("plain")
        manifest {
            attributes(
                "Premain-Class" to "io.github.slimefun5.harness.HarnessAgent",
                "Main-Class" to "io.github.slimefun5.harness.HarnessMain",
                "Can-Retransform-Classes" to "true"
            )
        }
    }
    shadowJar {
        archiveClassifier.set("")
        relocate("net.bytebuddy", "io.github.slimefun5.harness.libs.bytebuddy")
    }
    build {
        dependsOn(shadowJar)
    }
}
