/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.core;

import io.puriflow4j.core.api.Sanitizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Detects and shortens stack traces that are already embedded into the log message text.
 * It also masks sensitive substrings in the exception header lines.
 */
public final class EmbeddedStacktraceShortener {

    private final Sanitizer sanitizer;
    private final int maxDepth;
    private final Set<String> hidePackages;
    private final boolean categorize; // reserved if you want to add [DB]/[JSON] tags here too

    public EmbeddedStacktraceShortener(Sanitizer sanitizer, int maxDepth, List<String> hidePkgs, boolean categorize) {
        this.sanitizer = sanitizer;
        this.maxDepth = Math.max(1, maxDepth);
        this.hidePackages = Set.copyOf(hidePkgs == null ? List.of() : hidePkgs);
        this.categorize = categorize;
    }

    /** Returns a possibly shortened message. If no embedded stack trace is detected, returns original. */
    public String shorten(String message, String loggerName) {
        if (message == null || message.isEmpty()) return message;

        // Heuristic: lines that start with whitespace + "at " are stack frames
        String[] lines = message.split("\\R");
        int firstFrame = -1;
        for (int i = 0; i < lines.length; i++) {
            if (isFrameLine(lines[i])) {
                firstFrame = i;
                break;
            }
        }
        if (firstFrame < 0) return message; // no embedded stack

        List<String> out = new ArrayList<>(lines.length);
        // copy header lines before the first frame; also mask sensitive substrings there
        for (int i = 0; i < firstFrame; i++) {
            out.add(sanitizer.apply(lines[i], loggerName));
        }

        int printed = 0;
        int omitted = 0;

        for (int i = firstFrame; i < lines.length; i++) {
            String line = lines[i];
            if (isFrameLine(line)) {
                String fqcn = extractClassName(line);
                if (isHidden(fqcn)) {
                    omitted++;
                    continue;
                }
                if (printed >= maxDepth) {
                    omitted++;
                    continue;
                }
                out.add(line); // keep as-is (we already filtered by package/depth)
                printed++;
            } else if (isCausedBy(line)) {
                // Keep "Caused by: ..." but mask its message part
                out.add(sanitizer.apply(line, loggerName));
            } else {
                // other trailing lines (suppressed, ...); drop to keep it short
                omitted++;
            }
        }

        if (omitted > 0) {
            out.add(" (" + omitted + " framework frames omitted)");
        }

        return String.join(System.lineSeparator(), out);
    }

    private boolean isFrameLine(String line) {
        return line != null && line.stripLeading().startsWith("at ");
    }

    private boolean isCausedBy(String line) {
        return line != null && line.stripLeading().startsWith("Caused by:");
    }

    private boolean isHidden(String classOrFrame) {
        if (classOrFrame == null) return false;
        for (String p : hidePackages) {
            if (classOrFrame.startsWith(p)) return true;
        }
        return false;
    }

    private String extractClassName(String frameLine) {
        // Example: "    at com.foo.Bar.baz(Bar.java:10)"
        String s = frameLine.stripLeading();
        if (!s.startsWith("at ")) return "";
        int start = 3;
        int paren = s.indexOf('(', start);
        if (paren > start) {
            String sig = s.substring(start, paren);
            int lastDot = sig.lastIndexOf('.');
            return (lastDot > 0) ? sig.substring(0, lastDot) : sig;
        }
        return "";
    }
}
