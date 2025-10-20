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
 * No heuristics here: every message is scanned. Designed to be attached programmatically
 * on top of user's configuration without requiring changes in logback.xml.
 */
public final class PurifyAppender extends AppenderBase<ILoggingEvent> {

    private final Appender<ILoggingEvent> delegate;
    private volatile Sanitizer sanitizer;
    private final Reporter reporter;

    /**
     * Intentionally stores framework-managed objects (delegate appender and reporter) by reference.
     * We never expose them via accessors; lifecycle is owned by the logging framework / DI container.
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Delegate appender and reporter are framework-managed; "
                    + "no getters expose them and copying is impossible/meaningless.")
    public PurifyAppender(Appender<ILoggingEvent> delegate, Reporter reporter) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
    }

    public void setSanitizer(Sanitizer sanitizer) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
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
        if (sanitizer == null) {
            // Fallback: nothing to sanitize, just pass through
            delegate.doAppend(event);
            return;
        }
        String original = event.getFormattedMessage();
        Result res = sanitizer.applyDetailed(original, event.getLoggerName());
        if (!res.findings().isEmpty()) {
            reporter.report(res.findings());
        }

        if (res.sanitized().equals(original)) {
            delegate.doAppend(event);
        } else {
            delegate.doAppend(new SanitizedLoggingEvent(event, res.sanitized()));
        }
    }

    /** Returns true if the given appender is already a Purify wrapper to avoid double-wrapping. */
    public static boolean isPurify(Appender<?> app) {
        return app instanceof PurifyAppender;
    }
}
