package io.puriflow4j.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.preset.BuiltInDetectors;

import java.util.Objects;

/**
 * Wrapper around any Appender<ILoggingEvent>.
 * Sanitizes only the formattedMessage, then delegates the event to the original appender.
 */
public class PurifyAppender extends AppenderBase<ILoggingEvent> {

    private final Appender<ILoggingEvent> delegate;
    private volatile Sanitizer sanitizer;

    public PurifyAppender(Appender<ILoggingEvent> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void start() {
        if (!isStarted()) {
            this.sanitizer = new Sanitizer(BuiltInDetectors.minimal());
            // Inherit context from delegate (if any)
            if (getContext() == null && delegate.getContext() != null) {
                setContext(delegate.getContext());
            }
            super.start();
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        // Sanitize the already formatted string
        String formatted = event.getFormattedMessage();
        String sanitized = sanitizer.apply(formatted);
        if (Objects.equals(sanitized, formatted) || sanitized.equals(formatted)) {
            delegate.doAppend(event); // no changes
        } else {
            delegate.doAppend(new SanitizedLoggingEvent(event, sanitized));
        }
    }

    public Appender<ILoggingEvent> getDelegate() { return delegate; }

    /**
     * Indicates that the appender has already been wrapped (so as not to wrap it again).
     * */
    public static boolean isPurify(Appender<?> app) {
        return app instanceof PurifyAppender;
    }
}
