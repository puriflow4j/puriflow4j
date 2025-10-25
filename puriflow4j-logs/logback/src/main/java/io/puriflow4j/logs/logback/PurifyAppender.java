/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.api.model.Mode;
import io.puriflow4j.core.report.Reporter;
import io.puriflow4j.logs.core.categorize.ExceptionClassifier;
import io.puriflow4j.logs.core.model.ThrowableView;
import io.puriflow4j.logs.core.sanitize.MdcSanitizer;
import io.puriflow4j.logs.core.sanitize.MessageSanitizer;
import io.puriflow4j.logs.core.shorten.EmbeddedStacktraceShortener;
import io.puriflow4j.logs.core.shorten.ExceptionShortener;
import java.util.Map;
import java.util.Objects;

/**
 * Wraps a real Logback appender and sanitizes:
 *  - formatted message (always),
 *  - MDC (always),
 *  - exception text (always, full or compact depending on shortener settings).
 * Optionally classifies exceptions (e.g., [DB], [HTTP]) and includes label in the first line.
 */
public final class PurifyAppender extends AppenderBase<ILoggingEvent> {
    private final Appender<ILoggingEvent> delegate;
    private final Reporter reporter;
    private final MessageSanitizer msgSan;
    private final MdcSanitizer mdcSan;
    private final ExceptionShortener shortener; // for ThrowableProxy
    private final EmbeddedStacktraceShortener embeddedShortener; // for in-message stacks
    private final ExceptionClassifier classifier; // exception classifier
    private final Mode mode;

    public PurifyAppender(
            Appender<ILoggingEvent> delegate,
            Reporter reporter,
            Sanitizer sanitizer,
            ExceptionShortener shortener,
            EmbeddedStacktraceShortener embeddedShortener,
            ExceptionClassifier classifier, // NEW
            Mode mode) {

        this.delegate = Objects.requireNonNull(delegate);
        this.reporter = Objects.requireNonNull(reporter);
        this.msgSan = new MessageSanitizer(Objects.requireNonNull(sanitizer));
        this.mdcSan = new MdcSanitizer(sanitizer);
        this.shortener = Objects.requireNonNull(shortener);
        this.embeddedShortener = embeddedShortener;
        this.classifier = Objects.requireNonNull(classifier); // NEW
        this.mode = Objects.requireNonNull(mode);
    }

    @Override
    public void start() {
        if (getContext() == null && delegate.getContext() != null) {
            setContext(delegate.getContext());
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        final String logger = event.getLoggerName();

        // 1) Always sanitize the formatted message text
        final String originalMsg = event.getFormattedMessage();
        String maskedMsg = msgSan.sanitize(originalMsg, logger);

        // 2) Always sanitize MDC
        final Map<String, String> maskedMdc = mdcSan.sanitize(event.getMDCPropertyMap(), logger);

        // 3) Error options
        final boolean shortenOn = shortener.isShortenEnabled();

        // 4) Shorten embedded stacks in message only when shortening is enabled
        if (shortenOn && embeddedShortener != null) {
            maskedMsg = embeddedShortener.shorten(maskedMsg, logger);
        }

        // 5) ALWAYS render exception via our renderer if throwable exists.
        //    Here we also classify exception and pass label into shortener.
        String renderedExc = null;
        if (event.getThrowableProxy() != null) {
            final ThrowableView tv = ThrowableViewAdapter.toView(event.getThrowableProxy());

            // NEW: classify first line label (e.g. [DB], [HTTP]) — shortener сам решит, печатать её или игнорировать
            String categoryLabel = null;
            try {
                var res = classifier.classify(tv);
                if (res != null && res.hasLabel()) categoryLabel = res.label();
            } catch (Throwable ignore) {
                // classification is best-effort; never break logging
            }

            // Call the shortener with optional category label (overload below)
            renderedExc = shortener.format(tv, logger, categoryLabel);
        }

        // 6) Did anything change?
        final boolean messageChanged = !Objects.equals(originalMsg, maskedMsg);
        final boolean mdcChanged = (maskedMdc != event.getMDCPropertyMap());
        final boolean hasRenderedExc = (renderedExc != null);
        final boolean anyChange = messageChanged || mdcChanged || hasRenderedExc;

        // 7) STRICT mode: redact everything if anything changed
        if (mode == Mode.STRICT && anyChange) {
            delegate.doAppend(new SanitizedLoggingEvent(event, "[REDACTED_LOG]", maskedMdc, /*throwable*/ null));
            return;
        }

        // 8) Compose final message. If we rendered exception, append it and DROP original throwable
        String outMsg = maskedMsg;
        IThrowableProxy toForwardThrowable = event.getThrowableProxy();

        if (hasRenderedExc) {
            outMsg = (outMsg == null || outMsg.isEmpty()) ? renderedExc : (outMsg + "\n" + renderedExc);
            toForwardThrowable = null; // prevent Logback from printing the raw (unmasked) stack trace
        }

        // 9) Zero-overhead path if literally nothing changed
        if (!anyChange) {
            delegate.doAppend(event);
            return;
        }

        // 10) Forward sanitized event
        delegate.doAppend(new SanitizedLoggingEvent(event, outMsg, maskedMdc, toForwardThrowable));
    }

    static boolean isPurify(Appender<?> app) {
        return app instanceof PurifyAppender;
    }
}
