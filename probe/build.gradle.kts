plugins {
    java
    id("com.gradleup.shadow") version "9.3.2"
    id("io.github.intisy.github-gradle") version "1.8.2.1"
}

group = "io.github.slimefun5.harness.probe"
description = "Runs the harness's registry checks inside the server, after Slimefun and its addons have registered everything."

// The Slimefun-addon conventions: Java 8, the spigot-api baseline, core's API, and a jar that never bundles core.
apply(from = "https://raw.githubusercontent.com/Slimefun5/gradle/main/slimefun-addon.gradle")
