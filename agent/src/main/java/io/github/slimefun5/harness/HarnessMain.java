package io.github.slimefun5.harness;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

/**
 * Runs inside one version's JVM: bootstraps that version's game data, installs the harness server, loads and
 * enables the plugins, runs the start-up ticks and then Slimefun's own checks, and writes a report next to the
 * given path.
 */
public final class HarnessMain {

    /** Long enough for the delayed start-up tasks plugins schedule after enable (Slimefun's audits run at 20 to 100 ticks). */
    private static final int STARTUP_TICKS = 200;

    private static final String SLIMEFUN_PLUGIN = "Slimefun";
    private static final String CHECK_ENTRY_CLASS = "io.github.thebusybiscuit.slimefun5.core.checks.HarnessEntry";

    private HarnessMain() {}

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        ServerOverrides.PrimaryThread.mainThread = Thread.currentThread();
        arguments.report.getParentFile().mkdirs();
        WarningLog log = WarningLog.attachToRoot(new File(arguments.report.getParentFile(), "harness.log"));
        long start = System.nanoTime();
        String failure = null;
        try {
            ServerLayout layout = ServerLayout.detect();
            GameBootstrap.run(layout);
            HarnessServer harness = HarnessServer.install(layout, arguments.version, Logger.getLogger("Minecraft"));
            GameBootstrap.loadVanillaData(layout, harness.console);
            PluginBoot.loadAndEnable(harness.server.getPluginManager(), arguments.plugins, new File("plugins"), log);
            VirtualClock clock = new VirtualClock(harness.scheduler);
            clock.advance(STARTUP_TICKS, log);
            IntConsumer advanceTicks = ticks -> clock.advance(ticks, log);
            runCoreChecks(harness.server, arguments.stage, advanceTicks, log);
        } catch (Throwable fatal) {
            Throwable cause = rootCause(fatal);
            log.harnessFailure("the harness stopped", cause);
            failure = cause.toString();
        }
        log.importLog4jFile(new File("logs/latest.log"));
        writeReport(arguments.report, log.records(), Allowlist.read(arguments.allowlist), failure,
            (System.nanoTime() - start) / 1_000_000);
        System.exit(0);
    }

    /**
     * Runs Slimefun's own checks, which place its machines and click its menus with the players of core's
     * sandbox; a core jar without them is a harness failure, not a pass.
     */
    private static void runCoreChecks(Server server, String stage, IntConsumer advanceTicks, WarningLog log) {
        String context = "core checks";
        HarnessContext.set(context);
        Consumer<String> activity = what -> HarnessContext.set(what == null ? context : context + ": " + what);
        try {
            Plugin slimefun = server.getPluginManager().getPlugin(SLIMEFUN_PLUGIN);
            if (slimefun == null) {
                throw new IllegalStateException("The " + SLIMEFUN_PLUGIN + " plugin is not loaded, so its checks cannot run");
            }
            Class<?> entry = slimefun.getClass().getClassLoader().loadClass(CHECK_ENTRY_CLASS);
            entry.getMethod("run", String.class, IntConsumer.class, Consumer.class).invoke(null, stage, advanceTicks, activity);
        } catch (Throwable failure) {
            log.harnessFailure("core checks stopped", rootCause(failure));
        } finally {
            HarnessContext.clear();
        }
    }

    /** Reflection wraps every failure it relays; the report wants what actually went wrong. */
    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof java.lang.reflect.InvocationTargetException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** Writes the failing records to {@code warnings.txt}, the accepted ones to {@code accepted.txt}, and the verdict. */
    private static void writeReport(File report, List<String> records, Allowlist allowlist, String failure, long millis)
            throws IOException {
        List<String> failing = new ArrayList<>();
        List<String> accepted = new ArrayList<>();
        for (String record : records) {
            (allowlist.accepts(record) ? accepted : failing).add(record);
        }
        writeRecords(new File(report.getParentFile(), "warnings.txt"), failing);
        writeRecords(new File(report.getParentFile(), "accepted.txt"), accepted);
        Properties properties = new Properties();
        properties.setProperty("status", failure == null && failing.isEmpty() ? "PASS" : "FAIL");
        properties.setProperty("summary", (failure != null ? "stopped: " + failure + ", " : "")
            + failing.size() + " warning(s), " + accepted.size() + " accepted, " + millis + " ms");
        try (OutputStream out = new FileOutputStream(report)) {
            properties.store(out, null);
        }
    }

    /** Streams the records: an addon failing on every click logs enough of them to exhaust the heap if joined first. */
    private static void writeRecords(File file, List<String> records) throws IOException {
        String separator = System.lineSeparator() + System.lineSeparator();
        try (Writer out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            for (int index = 0; index < records.size(); index++) {
                if (index > 0) {
                    out.write(separator);
                }
                out.write(records.get(index));
            }
        }
    }

    private static final class Arguments {
        String version;
        File report;
        File allowlist;
        String stage = "max";
        final List<File> plugins = new ArrayList<>();

        static Arguments parse(String[] args) {
            Arguments parsed = new Arguments();
            for (int i = 0; i + 1 < args.length; i += 2) {
                switch (args[i]) {
                    case "--version":
                        parsed.version = args[i + 1];
                        break;
                    case "--report":
                        parsed.report = new File(args[i + 1]);
                        break;
                    case "--allowlist":
                        parsed.allowlist = new File(args[i + 1]);
                        break;
                    case "--stage":
                        parsed.stage = args[i + 1];
                        break;
                    case "--plugins":
                        for (String path : args[i + 1].split(File.pathSeparator)) {
                            parsed.plugins.add(new File(path));
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown argument " + args[i]);
                }
            }
            return parsed;
        }
    }
}
