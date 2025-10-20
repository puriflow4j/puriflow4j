/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.api.Sanitizer.Result;
import io.puriflow4j.core.report.Reporter;
import java.util.Objects;

/**
 * Wraps a real Logback appender and sanitizes the formatted message before delegating.
 * Designed to sit on top of user's appender chain; compatible with any logback.xml.
 */
public final class PurifyAppender extends AppenderBase<ILoggingEvent> {

    private final Appender<ILoggingEvent> delegate;
    private volatile Sanitizer sanitizer;
    private final Reporter reporter;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Delegate appender is framework-managed; reference is not exposed.")
    public PurifyAppender(Appender<ILoggingEvent> delegate, Reporter reporter) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
    }

    public void setSanitizer(Sanitizer sanitizer) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    @Override
    public void start() {
        if (getContext() == null && delegate.getContext() != null) setContext(delegate.getContext());
        super.start();
    }

    @Override
    public void append(ILoggingEvent event) {
        Sanitizer s = this.sanitizer;
        if (s == null) {
            delegate.doAppend(event);
            return;
        }

        String original = event.getFormattedMessage();
        Result res = s.applyDetailed(original, event.getLoggerName());
        if (!res.findings().isEmpty()) reporter.report(res.findings());

        if (res.sanitized().equals(original)) {
            delegate.doAppend(event);
        } else {
            delegate.doAppend(new SanitizedLoggingEvent(event, res.sanitized()));
        }
    }

    public static boolean isPurify(Appender<?> app) {
        return app instanceof PurifyAppender;
    }
}
