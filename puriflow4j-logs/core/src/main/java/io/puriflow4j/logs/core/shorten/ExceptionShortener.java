/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.core.shorten;

import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.logs.core.model.StackFrameView;
import io.puriflow4j.logs.core.model.ThrowableView;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Renders exception text either in compact (shorten=true) or full mode (shorten=false).
 * IMPORTANT: We sanitize ONLY exception messages (t.message / cause.message),
 * NOT the frame lines, to avoid false positives (e.g., class names matching JWT regex).
 * Category label (e.g., "DB") is optional and provided by the caller.
 */
public final class ExceptionShortener {

    private final Sanitizer sanitizer;
    private final boolean shorten;
    private final int maxDepth; // how many application frames to print in compact mode
    private final Set<String> hidePackages;

    public ExceptionShortener(Sanitizer sanitizer, boolean shorten, int maxDepth, List<String> hidePkgs) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
        this.shorten = shorten;
        this.maxDepth = Math.max(1, maxDepth);
        this.hidePackages = (hidePkgs == null ? Set.of() : Set.copyOf(hidePkgs));
    }

    /** Backward-compatible signature (no category label). */
    public String format(ThrowableView t, String loggerName) {
        return format(t, loggerName, null);
    }

    /**
     * Render exception with an optional category label (e.g., "DB").
     * We sanitize only messages, not frame lines.
     */
    public String format(ThrowableView t, String loggerName, String categoryLabel) {
        if (t == null) return null;
        return shorten
                ? renderCompact(t, loggerName, normalized(categoryLabel))
                : renderFull(t, loggerName, normalized(categoryLabel));
    }

    public boolean isShortenEnabled() {
        return shorten;
    }

    // ---------------- rendering ----------------

    /** Compact: optional category + first line + filtered frames (maxDepth) + omitted counter + single cause line. */
    private String renderCompact(ThrowableView t, String loggerName, String category) {
        String origMsg = nullToEmpty(t.message());
        String maskedMsg = origMsg.isEmpty() ? "" : sanitizer.apply(origMsg, loggerName);
        boolean msgChanged = !Objects.equals(origMsg, maskedMsg);

        StringBuilder sb = new StringBuilder(256);
        if (category != null) sb.append('[').append(category).append("] ");
        if (msgChanged) sb.append("[Masked] ");

        sb.append(simple(t.className()));
        if (!maskedMsg.isEmpty()) {
            sb.append(": ").append(maskedMsg);
        }

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
            // DO NOT sanitize frame lines to preserve readability and avoid false positives
            sb.append('\n').append(" \u2192 ").append(f.pretty());
            printed++;
        }
        if (omitted > 0) {
            sb.append('\n').append(" (").append(omitted).append(" framework frames omitted)");
        }

        // Single-level cause line (also sanitize only the message)
        if (t.cause() != null) {
            String cOrig = nullToEmpty(t.cause().message());
            String cMask = cOrig.isEmpty() ? "" : sanitizer.apply(cOrig, loggerName);
            boolean cChanged = !Objects.equals(cOrig, cMask);

            sb.append('\n').append(" Caused by: ");
            if (cChanged) sb.append("[Masked] ");
            sb.append(simple(t.cause().className()));
            if (!cMask.isEmpty()) sb.append(": ").append(cMask);
        }
        return sb.toString();
    }

    /** Full: optional category + full multi-line stack (no filtering, full cause chain). */
    private String renderFull(ThrowableView t, String loggerName, String category) {
        String origMsg = nullToEmpty(t.message());
        String maskedMsg = origMsg.isEmpty() ? "" : sanitizer.apply(origMsg, loggerName);
        boolean msgChanged = !Objects.equals(origMsg, maskedMsg);

        StringBuilder sb = new StringBuilder(256);
        if (category != null) sb.append('[').append(category).append("] ");
        if (msgChanged) sb.append("[Masked] ");

        sb.append(simple(t.className()));
        if (!maskedMsg.isEmpty()) {
            sb.append(": ").append(maskedMsg);
        }

        // DO NOT sanitize frame lines
        for (StackFrameView f : t.frames()) {
            sb.append('\n').append("\tat ").append(f.pretty());
        }

        // Full recursive cause chain (sanitize only messages)
        if (t.cause() != null) {
            appendCauseFull(sb, t.cause(), loggerName);
        }
        return sb.toString();
    }

    private void appendCauseFull(StringBuilder sb, ThrowableView cause, String loggerName) {
        String origMsg = nullToEmpty(cause.message());
        String maskedMsg = origMsg.isEmpty() ? "" : sanitizer.apply(origMsg, loggerName);
        boolean msgChanged = !Objects.equals(origMsg, maskedMsg);

        sb.append('\n').append("Caused by: ");
        if (msgChanged) sb.append("[Masked] ");
        sb.append(simple(cause.className()));
        if (!maskedMsg.isEmpty()) sb.append(": ").append(maskedMsg);

        // frames intact
        for (StackFrameView f : cause.frames()) {
            sb.append('\n').append("\tat ").append(f.pretty());
        }
        if (cause.cause() != null) {
            appendCauseFull(sb, cause.cause(), loggerName);
        }
    }

    // ---------------- helpers ----------------

    private boolean isHidden(String className) {
        if (className == null) return false;
        for (String p : hidePackages) if (className.startsWith(p)) return true;
        return false;
    }

    private static String simple(String fqcn) {
        if (fqcn == null) return "Exception";
        int i = fqcn.lastIndexOf('.');
        return i >= 0 ? fqcn.substring(i + 1) : fqcn;
    }

    private static String nullToEmpty(String s) {
        return (s == null) ? "" : s;
    }

    private static String normalized(String label) {
        if (label == null) return null;
        String t = label.trim();
        return t.isEmpty() ? null : t;
    }
}
