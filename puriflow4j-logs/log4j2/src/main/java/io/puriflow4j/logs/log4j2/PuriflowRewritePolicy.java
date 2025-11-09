/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.log4j2;

import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.api.model.Mode;
import io.puriflow4j.core.report.Reporter;
import io.puriflow4j.logs.core.categorize.ExceptionClassifier;
import io.puriflow4j.logs.core.model.ThrowableView;
import io.puriflow4j.logs.core.sanitize.MdcSanitizer;
import io.puriflow4j.logs.core.sanitize.MessageSanitizer;
import io.puriflow4j.logs.core.shorten.EmbeddedStacktraceShortener;
import io.puriflow4j.logs.core.shorten.ExceptionShortener;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Log4j2 RewritePolicy that performs the same hygiene steps as our Logback appender:
 *  - sanitize formatted message
 *  - sanitize context data (MDC / ThreadContext)
 *  - shorten/format exception and append it to the message (and drop raw Throwable)
 *  - STRICT mode (if any change -> redact the log)
 *  - optional exception classification label (e.g., [DB], [HTTP])
 */
public final class PuriflowRewritePolicy implements RewritePolicy {

    private final Reporter reporter;
    private final MessageSanitizer msgSan;
    private final MdcSanitizer mdcSan;
    private final ExceptionShortener shortener;
    private final EmbeddedStacktraceShortener embeddedShortener;
    private final ExceptionClassifier classifier;
    private final Mode mode;

    public PuriflowRewritePolicy(
            Reporter reporter,
            Sanitizer sanitizer,
            ExceptionShortener shortener,
            EmbeddedStacktraceShortener embeddedShortener,
            ExceptionClassifier classifier,
            Mode mode
    ) {
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        this.msgSan = new MessageSanitizer(Objects.requireNonNull(sanitizer, "sanitizer"));
        this.mdcSan = new MdcSanitizer(sanitizer);
        this.shortener = Objects.requireNonNull(shortener, "shortener");
        this.embeddedShortener = embeddedShortener;
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @Override
    public LogEvent rewrite(LogEvent source) {
        final String logger = source.getLoggerName();

        // 1) sanitize formatted message
        final String originalMsg = source.getMessage() != null ? source.getMessage().getFormattedMessage() : null;
        String maskedMsg = msgSan.sanitize(originalMsg, logger);

        // 2) sanitize MDC / context data
        Map<String, String> originalMdc = source.getContextData() != null
                ? source.getContextData().toMap()
                : Map.of();
        Map<String, String> maskedMdc = mdcSan.sanitize(originalMdc, logger);

        // 3) error/shortening options
        final boolean shortenOn = shortener.isShortenEnabled();

        // 4) shorten embedded stacks inside the message (only when enabled)
        if (shortenOn && embeddedShortener != null && maskedMsg != null) {
            maskedMsg = embeddedShortener.shorten(maskedMsg, logger);
        }

        // 5) ALWAYS render exception via our shortener if Throwable exists.
        //    Also classify and pass optional label to the shortener.
        String renderedExc = null;
        if (source.getThrown() != null) {
            final ThrowableView tv = ThrowableViewAdapter.toView(source.getThrown());

            String categoryLabel = null;
            try {
                var res = classifier.classify(tv);
                if (res != null && res.hasLabel()) categoryLabel = res.label();
            } catch (Throwable ignore) {
                // best-effort: we never break logging on classification errors
            }

            renderedExc = shortener.format(tv, logger, categoryLabel);
        }

        // 6) detect changes
        final boolean messageChanged = !Objects.equals(originalMsg, maskedMsg);
        final boolean mdcChanged = !Objects.equals(originalMdc, maskedMdc);
        final boolean hasRenderedExc = (renderedExc != null);
        final boolean anyChange = messageChanged || mdcChanged || hasRenderedExc;

        // 7) STRICT mode: redact everything if anything changed
        if (mode == Mode.STRICT && anyChange) {
            return build(source, "[REDACTED_LOG]", maskedMdc, /*drop throwable*/ null);
        }

        // 8) Compose final message. If we rendered exception, append it and DROP the raw Throwable
        String outMsg = maskedMsg;
        Throwable outThrown = source.getThrown();

        if (hasRenderedExc) {
            outMsg = (outMsg == null || outMsg.isEmpty()) ? renderedExc : (outMsg + "\n" + renderedExc);
            outThrown = null; // prevent Log4j2 from printing the raw (unmasked) stack trace
        }

        // 9) zero-overhead path if literally nothing changed
        if (!anyChange) return source;

        // 10) build a sanitized copy
        return build(source, outMsg, maskedMdc, outThrown);
    }

    private static LogEvent build(LogEvent src, String newMessage, Map<String, String> newMdc, Throwable newThrown) {
        return Log4jLogEvent.newBuilder()
                .setLoggerName(src.getLoggerName())
                .setLoggerFqcn(src.getLoggerFqcn())
                .setLevel(src.getLevel())
                .setMarker(src.getMarker())
                .setContextData(ContextDataFactory.createContextData(newMdc))
                .setContextStack(src.getContextStack())
                .setTimeMillis(src.getTimeMillis())
                .setSource(src.getSource())
                .setThreadName(src.getThreadName())
                .setThrown(newThrown)
                .setMessage(new SimpleMessage(newMessage))
                .build();
    }
}