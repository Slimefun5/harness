package io.github.slimefun5.harness;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Accepted warnings: one regular expression per line, matched against a record's first line; blank lines
 * and lines starting with {@code #} are ignored.
 */
final class Allowlist {

    private final List<Pattern> patterns;

    private Allowlist(List<Pattern> patterns) {
        this.patterns = patterns;
    }

    static Allowlist read(File file) throws IOException {
        List<Pattern> patterns = new ArrayList<>();
        if (file != null && file.isFile()) {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    patterns.add(Pattern.compile(trimmed));
                }
            }
        }
        return new Allowlist(patterns);
    }

    boolean accepts(String record) {
        String firstLine = record.split("\\R", 2)[0];
        for (Pattern pattern : patterns) {
            if (pattern.matcher(firstLine).find()) {
                return true;
            }
        }
        return false;
    }
}
