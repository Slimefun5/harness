package io.github.slimefun5.harness;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Runs inside one version's JVM: bootstraps that version's game data, installs the harness server, loads and
 * enables the plugins, runs the start-up ticks, and writes a report next to the given path.
 */
public final class HarnessMain {

    /** Long enough for the delayed start-up tasks plugins schedule after enable (Slimefun's audits run at 20 to 100 ticks). */
    private static final int STARTUP_TICKS = 200;

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
            new VirtualClock(harness.scheduler).advance(STARTUP_TICKS, log);
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

    private static void writeRecords(File file, List<String> records) throws IOException {
        String separator = System.lineSeparator() + System.lineSeparator();
        Files.write(file.toPath(), String.join(separator, records).getBytes(StandardCharsets.UTF_8));
    }

    private static final class Arguments {
        String version;
        File report;
        File allowlist;
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
