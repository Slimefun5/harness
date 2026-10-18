import java.util.Properties

plugins {
    `java-base`
}

evaluationDependsOn(":agent")

val serverRoot =file(property("harness.serverRoot").toString())
val pluginJars = property("harness.plugins").toString().split(',').map { file(it.trim()) }
val versions = (findProperty("versions") ?: property("harness.versions")).toString().split(',').map { it.trim() }
val checkStage = (findProperty("stage") ?: "max").toString()
val agentJar = project(":agent").tasks.named("shadowJar")

/** The JDK each version is run on: the oldest that version's real servers run on, so a newer-JDK-only call fails here too. */
fun jdkFor(version: String): Int {
    val parts = version.split('.').map { it.toInt() }
    return when {
        parts[0] >= 26 -> 25
        parts[1] > 20 || (parts[1] == 20 && parts.getOrElse(2) { 0 } >= 5) -> 21
        parts[1] >= 17 -> 17
        else -> 8
    }
}

/** The server jar plus, from 1.18, its unbundled libraries. */
fun serverClasspath(version: String): List<File> {
    val root = File(serverRoot, version)
    val patched = File(root, "cache/patched_$version.jar")
    val paper = File(root, "versions/$version/paper-$version.jar")
    val serverJar = listOf(paper, patched).firstOrNull { it.isFile }
        ?: throw GradleException("No server jar for $version under $root")
    val libraries = File(root, "libraries").walkTopDown().filter { it.isFile && it.name.endsWith(".jar") }.sorted().toList()
    return listOf(serverJar) + libraries
}

fun versionParts(version: String) = version.split('.').map { it.toInt() }

fun compareVersions(left: String, right: String): Int {
    val a = versionParts(left)
    val b = versionParts(right)
    for (i in 0 until maxOf(a.size, b.size)) {
        val difference = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (difference != 0) {
            return difference
        }
    }
    return 0
}

/** The newest `loaders/<first version>` folder that is not newer than this version, if any. */
fun loaderFor(version: String): File? = file("loaders").listFiles { candidate -> candidate.isDirectory }
    ?.filter { compareVersions(it.name, version) <= 0 }
    ?.maxWithOrNull { a, b -> compareVersions(a.name, b.name) }

fun reportFile(version: String) = layout.buildDirectory.file("harness/$version/report.properties").get().asFile

val versionTasks = versions.map { version ->
    val loaderSource = loaderFor(version)
    val loaderClasses = layout.buildDirectory.dir("harness-loaders/$version")
    val compileLoader = loaderSource?.let { source ->
        tasks.register<JavaCompile>("compileLoader_$version") {
            description = "Compiles the vanilla datapack loader against the real $version server."
            this.source = fileTree(source)
            classpath = files(serverClasspath(version))
            destinationDirectory.set(loaderClasses)
            javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(jdkFor(version))) })
            options.encoding = "UTF-8"
        }
    }
    tasks.register<JavaExec>("verify_$version") {
        group = "verification"
        description = "Loads the plugins against Minecraft $version without starting a server."
        dependsOn(agentJar)
        compileLoader?.let { dependsOn(it) }
        val workDir = layout.buildDirectory.dir("harness/$version").get().asFile
        javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(jdkFor(version))) })
        mainClass.set("io.github.slimefun5.harness.HarnessMain")
        workingDir = workDir
        isIgnoreExitValue = true
        doFirst {
            workDir.deleteRecursively()
            workDir.mkdirs()
            val agent = agentJar.get().outputs.files.singleFile
            val loader = if (compileLoader != null) files(loaderClasses) else files()
            classpath = files(agent) + loader + files(serverClasspath(version))
            jvmArgs("-javaagent:${agent.absolutePath}", "-Xmx2g")
            args("--version", version, "--report", reportFile(version).absolutePath,
                "--allowlist", file("allowlist.txt").absolutePath, "--stage", checkStage,
                "--plugins", pluginJars.joinToString(File.pathSeparator) { it.absolutePath })
        }
    }
}

tasks.register("verifyAllVersions") {
    group = "verification"
    description = "Verifies every configured version and fails if any reports a problem."
    dependsOn(versionTasks)
    doLast {
        val failed = versions.filter { version ->
            val report = Properties()
            val file = reportFile(version)
            if (file.isFile) {
                file.inputStream().use { report.load(it) }
            }
            val status = report.getProperty("status", "NO REPORT")
            logger.lifecycle("$version: $status (${report.getProperty("summary", "the harness did not finish")})")
            status != "PASS"
        }
        if (failed.isNotEmpty()) {
            throw GradleException("Harness failed on ${failed.joinToString()}; details in build/harness/<version>/")
        }
    }
}
