package io.github.slimefun5.harness;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;

import io.github.slimefun5.harness.simulation.HarnessContext;

/** Collects every log record at WARNING or above from the server and every plugin. */
final class WarningLog extends Handler {

    private static final SimpleFormatter FORMATTER = new SimpleFormatter();
    private static final Pattern LOG4J_ENTRY = Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}\\] \\[[^\\]]+/[A-Z]+\\]: ");
    private static final Pattern LOG4J_PROBLEM = Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}\\] \\[[^\\]]+/(WARN|ERROR|FATAL)\\]: ");

    private final List<String> records = Collections.synchronizedList(new ArrayList<String>());

    /** Collects warnings from the root logger, and writes every record at INFO and above to {@code fullLog}. */
    static WarningLog attachToRoot(File fullLog) throws IOException {
        WarningLog log = new WarningLog();
        log.setLevel(Level.WARNING);
        Logger root = Logger.getLogger("");
        root.addHandler(log);
        FileHandler everything = new FileHandler(fullLog.getAbsolutePath());
        everything.setEncoding(StandardCharsets.UTF_8.name());
        everything.setFormatter(new SimpleFormatter());
        everything.setLevel(Level.INFO);
        root.addHandler(everything);
        return log;
    }

    /** Records a failure the harness itself caught, so it is reported like any warning. */
    void harnessFailure(String what, Throwable failure) {
        records.add("SEVERE [harness] " + what + System.lineSeparator() + stackTrace(failure));
    }

    /**
     * Adds every WARN and ERROR entry, with its stack trace, from a Log4j log file.
     *
     * @implNote Paper and vanilla log through Log4j rather than {@code java.util.logging}; their entries
     *           only reach this collector through the file the bundled configuration writes.
     */
    void importLog4jFile(File file) throws IOException {
        if (!file.isFile()) {
            return;
        }
        StringBuilder entry = null;
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            boolean startsEntry = LOG4J_ENTRY.matcher(line).find();
            if (startsEntry || entry == null) {
                if (entry != null) {
                    records.add(entry.toString());
                    entry = null;
                }
                if (startsEntry && LOG4J_PROBLEM.matcher(line).find()) {
                    entry = new StringBuilder(line);
                }
            } else {
                entry.append(System.lineSeparator()).append(line);
            }
        }
        if (entry != null) {
            records.add(entry.toString());
        }
    }

    List<String> records() {
        synchronized (records) {
            return new ArrayList<>(records);
        }
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        String message = record.getLevel() + " [" + record.getLoggerName() + "] " + FORMATTER.formatMessage(record);
        if (!"Harness".equals(record.getLoggerName())) {
            message += HarnessContext.suffix();
        }
        if (record.getThrown() != null) {
            message += System.lineSeparator() + stackTrace(record.getThrown());
        }
        records.add(message);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}

    private static String stackTrace(Throwable failure) {
        StringWriter out = new StringWriter();
        failure.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
