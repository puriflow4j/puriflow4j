/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.jul;

import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.api.model.Mode;
import io.puriflow4j.logs.core.categorize.ExceptionClassifier;
import io.puriflow4j.logs.core.model.ThrowableView;
import io.puriflow4j.logs.core.sanitize.MdcSanitizer;
import io.puriflow4j.logs.core.sanitize.MessageSanitizer;
import io.puriflow4j.logs.core.shorten.EmbeddedStacktraceShortener;
import io.puriflow4j.logs.core.shorten.ExceptionShortener;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * JUL handler that wraps a real Handler and sanitizes:
 *  - formatted message (always),
 *  - MDC-like data (best-effort: reads Map param or SLF4J MDC if available),
 *  - Throwable (always, rendered into message; raw throwable is dropped).
 *
 * IMPORTANT: JUL has no native MDC. We try in order:
 *  1) if LogRecord.getParameters()[i] is a Map<?,?>, treat it as MDC-like data,
 *  2) if org.slf4j.MDC exists, read its context map (optional dependency).
 */
public final class PurifyJULHandler extends Handler {

    private final Handler delegate;
    private final MessageSanitizer msgSan;
    private final MdcSanitizer mdcSan;
    private final ExceptionShortener shortener;
    private final EmbeddedStacktraceShortener embeddedShortener;
    private final ExceptionClassifier classifier;
    private final Mode mode;

    public PurifyJULHandler(
            Handler delegate,
            Sanitizer sanitizer,
            ExceptionShortener shortener,
            EmbeddedStacktraceShortener embeddedShortener,
            ExceptionClassifier classifier,
            Mode mode)
            throws UnsupportedEncodingException {

        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.msgSan = new MessageSanitizer(Objects.requireNonNull(sanitizer, "sanitizer"));
        this.mdcSan = new MdcSanitizer(sanitizer);
        this.shortener = Objects.requireNonNull(shortener, "shortener");
        this.embeddedShortener = embeddedShortener;
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.mode = Objects.requireNonNull(mode, "mode");

        // mirror delegate's config
        setFormatter(delegate.getFormatter());
        setFilter(delegate.getFilter());
        setLevel(delegate.getLevel());
        setEncoding(getEncoding());
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) return;

        final String logger = record.getLoggerName();

        // 1) message
        final String originalMsg = MessageFormatter.formatJul(record);
        String maskedMsg = msgSan.sanitize(originalMsg, logger);

        // 2) "MDC": try parameters Map first, then SLF4J MDC (if present)
        Map<String, String> originalMdc = readMdc(record);
        Map<String, String> maskedMdc = mdcSan.sanitize(originalMdc, logger);

        // 3) shorten embedded stacks inside message when enabled
        if (shortener.isShortenEnabled() && embeddedShortener != null && maskedMsg != null) {
            maskedMsg = embeddedShortener.shorten(maskedMsg, logger);
        }

        // 4) render throwable (and drop raw)
        String renderedExc = null;
        if (record.getThrown() != null) {
            ThrowableView tv = ThrowableViewAdapter.toView(record.getThrown());
            String label = null;
            try {
                var res = classifier.classify(tv);
                if (res != null && res.hasLabel()) label = res.label();
            } catch (Throwable ignore) {
                /* best-effort */
            }
            renderedExc = shortener.format(tv, logger, label);
        }

        final boolean msgChanged = !Objects.equals(originalMsg, maskedMsg);
        final boolean mdcChanged = !Objects.equals(originalMdc, maskedMdc);
        final boolean hasRendered = (renderedExc != null);
        final boolean anyChange = msgChanged || mdcChanged || hasRendered;

        String outMsg = maskedMsg;
        if (hasRendered) {
            outMsg = (outMsg == null || outMsg.isEmpty()) ? renderedExc : outMsg + "\n" + renderedExc;
        }

        // STRICT -> redact everything
        if (mode == Mode.STRICT && anyChange) {
            delegate.publish(copy(record, "[REDACTED_LOG]", /*drop*/ null));
            return;
        }

        // fast-path
        if (!anyChange) {
            delegate.publish(record);
            return;
        }

        // write sanitized copy (drop raw throwable)
        delegate.publish(copy(record, outMsg, /*drop*/ null));
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void close() throws SecurityException {
        delegate.close();
    }

    /** Builds a sanitized copy: replaces message and removes Throwable. */
    private static LogRecord copy(LogRecord src, String newMsg, Throwable newThrown) {
        LogRecord r = new LogRecord(src.getLevel(), newMsg);
        r.setLoggerName(src.getLoggerName());
        r.setMillis(src.getMillis());
        r.setSequenceNumber(src.getSequenceNumber());
        r.setSourceClassName(src.getSourceClassName());
        r.setSourceMethodName(src.getSourceMethodName());
        r.setThreadID(src.getThreadID());
        r.setResourceBundle(src.getResourceBundle());
        r.setResourceBundleName(src.getResourceBundleName());
        r.setParameters(src.getParameters()); // unchanged; message was sanitized
        r.setThrown(newThrown); // drop raw throwable
        return r;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> readMdc(LogRecord r) {
        // 1) parameters Map
        Object[] params = r.getParameters();
        if (params != null) {
            for (Object p : params) {
                if (p instanceof Map<?, ?> m) {
                    Map<String, String> out = new LinkedHashMap<>();
                    m.forEach((k, v) -> {
                        if (k != null && v != null) out.put(String.valueOf(k), String.valueOf(v));
                    });
                    return out;
                }
            }
        }
        // 2) optional SLF4J MDC
        try {
            Class<?> mdc = Class.forName("org.slf4j.MDC");
            var get = mdc.getMethod("getCopyOfContextMap");
            Object res = get.invoke(null);
            if (res instanceof Map<?, ?> m) {
                Map<String, String> out = new LinkedHashMap<>();
                m.forEach((k, v) -> {
                    if (k != null && v != null) out.put(String.valueOf(k), String.valueOf(v));
                });
                return out;
            }
        } catch (Throwable ignore) {
            /* absent */
        }
        return Collections.emptyMap();
    }

    /** Small helper that renders JUL message using java.text formatting rules. */
    private static final class MessageFormatter {
        static String formatJul(LogRecord r) {
            String msg = r.getMessage();
            Object[] params = r.getParameters();
            if (params == null || params.length == 0) return msg;

            try {
                return java.text.MessageFormat.format(msg, params);
            } catch (IllegalArgumentException e) {
                // fallback to simple concatenation if pattern is broken
                return msg + " " + Arrays.toString(params);
            }
        }
    }
}
