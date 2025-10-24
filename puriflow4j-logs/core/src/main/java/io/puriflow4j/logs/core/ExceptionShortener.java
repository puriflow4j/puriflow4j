/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.core;

import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.logs.core.model.StackFrameView;
import io.puriflow4j.logs.core.model.ThrowableView;
import java.util.List;
import java.util.Set;

public final class ExceptionShortener {
    private final Sanitizer sanitizer;
    private final boolean shorten;
    private final int maxDepth; // how many app frames to print
    private final Set<String> hidePackages;
    private final boolean categorize;

    public ExceptionShortener(
            Sanitizer sanitizer, boolean shorten, int maxDepth, List<String> hidePkgs, boolean categorize) {
        this.sanitizer = sanitizer;
        this.shorten = shorten;
        this.maxDepth = Math.max(1, maxDepth);
        this.hidePackages = (hidePkgs == null ? Set.of() : Set.copyOf(hidePkgs));
        this.categorize = categorize;
    }

    public boolean isShortenEnabled() {
        return shorten;
    }

    /** Returns a single multi-line string with masked/categorized, shortened stacktrace. */
    public String format(ThrowableView t, String loggerName) {
        if (t == null) return null;
        if (!shorten) return full(t, loggerName); // fall back to full string (masked messages)

        String cat = categorize ? category(t.className()) : null;
        String msg = t.message();
        if (msg != null && !msg.isEmpty()) msg = sanitizer.apply(msg, loggerName);

        StringBuilder sb = new StringBuilder(256);
        if (cat != null) sb.append('[').append(cat).append(']');
        if (msg != null && !msg.isEmpty()) sb.append("[Masked] ");
        sb.append(simple(t.className())).append(": ").append(msg == null ? "" : msg);

        int printed = 0, omitted = 0;
        for (StackFrameView f : t.frames()) {
            if (isHidden(f.className())) {
                omitted++;
                continue;
            }
            if (printed >= maxDepth) {
                omitted++;
                continue;
            }
            sb.append('\n').append(" \u2192 ").append(f.pretty());
            printed++;
        }
        if (omitted > 0) sb.append('\n').append(" (").append(omitted).append(" framework frames omitted)");

        if (t.cause() != null) {
            String cmsg = t.cause().message();
            if (cmsg != null && !cmsg.isEmpty()) cmsg = sanitizer.apply(cmsg, loggerName);
            sb.append('\n')
                    .append(" Caused by: ")
                    .append(simple(t.cause().className()))
                    .append(": ")
                    .append(cmsg == null ? "" : cmsg);
        }
        return sb.toString();
    }

    private boolean isHidden(String className) {
        if (className == null) return false;
        for (String p : hidePackages) if (className.startsWith(p)) return true;
        return false;
    }

    private static String simple(String fqcn) {
        int i = (fqcn == null ? -1 : fqcn.lastIndexOf('.'));
        return i >= 0 ? fqcn.substring(i + 1) : (fqcn == null ? "Exception" : fqcn);
    }

    private String full(ThrowableView t, String loggerName) {
        // Fallback full text (masked messages), still portable
        String msg = t.message();
        if (msg != null && !msg.isEmpty()) msg = sanitizer.apply(msg, loggerName);
        StringBuilder sb = new StringBuilder(256)
                .append(simple(t.className()))
                .append(": ")
                .append(msg == null ? "" : msg);
        for (StackFrameView f : t.frames()) sb.append('\n').append(" at ").append(f.pretty());
        if (t.cause() != null) {
            String cmsg = t.cause().message();
            if (cmsg != null && !cmsg.isEmpty()) cmsg = sanitizer.apply(cmsg, loggerName);
            sb.append('\n')
                    .append(" Caused by: ")
                    .append(simple(t.cause().className()))
                    .append(": ")
                    .append(cmsg == null ? "" : cmsg);
        }
        return sb.toString();
    }

    private static String category(String fqcn) {
        if (fqcn == null) return null;
        String s = fqcn.toLowerCase();
        if (s.contains("sql") || s.contains("jdbc") || s.contains("datasource")) return "DB";
        if (s.contains("json") || s.contains("jackson")) return "JSON";
        if (s.contains("timeout") || s.contains("timedout")) return "Timeout";
        if (s.contains("security") || s.contains("forbidden") || s.contains("unauthor")) return "Security";
        if (s.contains("io.") || s.contains("socket") || s.contains("channel")) return "IO";
        return null;
    }
}
