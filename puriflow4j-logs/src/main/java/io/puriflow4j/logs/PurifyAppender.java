package io.puriflow4j.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.api.Sanitizer.Result;

import io.puriflow4j.core.report.Reporter;

public class PurifyAppender extends AppenderBase<ILoggingEvent> {
    private final Appender<ILoggingEvent> delegate;
    private volatile Sanitizer sanitizer;
    private final Reporter reporter;

    public PurifyAppender(Appender<ILoggingEvent> delegate, Reporter reporter) {
        this.delegate = delegate;
        this.reporter = reporter;
    }

    /**
     * Starter injects the Sanitizer built from YAML.
     */
    public void setSanitizer(Sanitizer sanitizer) { this.sanitizer = sanitizer; }
    public Appender<ILoggingEvent> getDelegate() { return delegate; }

    @Override
    public void start() {
        if (!isStarted()) {
            if (getContext() == null && delegate.getContext() != null) setContext(delegate.getContext());
            super.start();
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (sanitizer == null) {
            delegate.doAppend(event);
            return;
        }
        Result res = sanitizer.applyDetailed(event.getFormattedMessage(), event.getLoggerName());
        if (!res.findings().isEmpty()) reporter.report(res.findings());
        if (res.sanitized().equals(event.getFormattedMessage())) {
            delegate.doAppend(event);
        } else {
            delegate.doAppend(new SanitizedLoggingEvent(event, res.sanitized()));
        }
    }

    public static boolean isPurify(Appender<?> app) { return app instanceof PurifyAppender; }
}
