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
import io.puriflow4j.core.api.model.Mode;
import io.puriflow4j.core.report.Reporter;
import io.puriflow4j.logs.core.EmbeddedStacktraceShortener;
import io.puriflow4j.logs.core.ExceptionShortener;
import io.puriflow4j.logs.core.MdcSanitizer;
import io.puriflow4j.logs.core.MessageSanitizer;
import io.puriflow4j.logs.core.model.ThrowableView;
import java.util.Map;
import java.util.Objects;

@SuppressFBWarnings
public final class PurifyAppender extends AppenderBase<ILoggingEvent> {
    private final Appender<ILoggingEvent> delegate;
    private final Reporter reporter;
    private final MessageSanitizer msgSan;
    private final MdcSanitizer mdcSan;
    private final ExceptionShortener shortener;              // for ThrowableProxy
    private final EmbeddedStacktraceShortener embeddedShortener; // for in-message stacks
    private final Mode mode;

    public PurifyAppender(
            Appender<ILoggingEvent> delegate,
            Reporter reporter,
            Sanitizer sanitizer,
            ExceptionShortener shortener,
            EmbeddedStacktraceShortener embeddedShortener,
            Mode mode) {
        this.delegate = Objects.requireNonNull(delegate);
        this.reporter = Objects.requireNonNull(reporter);
        this.msgSan = new MessageSanitizer(Objects.requireNonNull(sanitizer));
        this.mdcSan = new MdcSanitizer(sanitizer);
        this.shortener = Objects.requireNonNull(shortener);
        this.embeddedShortener = embeddedShortener;
        this.mode = Objects.requireNonNull(mode);
    }

    @Override
    public void start() {
        if (getContext() == null && delegate.getContext() != null) setContext(delegate.getContext());
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        String logger = event.getLoggerName();

        // message + mdc
        String originalMsg = event.getFormattedMessage();
        String maskedMsg = msgSan.sanitize(originalMsg, logger);


        // NEW: shorten embedded stack traces in the message text (Tomcat-style "with root cause")
        if (shortener.isShortenEnabled()) {
            maskedMsg = embeddedShortener.shorten(maskedMsg, logger);
        }

        Map<String, String> maskedMdc = mdcSan.sanitize(event.getMDCPropertyMap(), logger);

        // throwable → short text
        String shortExc = null;
        if (event.getThrowableProxy() != null && shortener.isShortenEnabled()) {
            ThrowableView tv = ThrowableViewAdapter.toView(event.getThrowableProxy());
            shortExc = shortener.format(tv, logger);
        }

        boolean anyChange = !Objects.equals(originalMsg, maskedMsg) || shortExc != null;

        String outMsg = maskedMsg;
        if (shortExc != null) outMsg = (outMsg == null || outMsg.isEmpty()) ? shortExc : (outMsg + "\n" + shortExc);

        if (mode == Mode.STRICT && anyChange) {
            outMsg = "[REDACTED_LOG]";
            shortExc = null; // не печатаем ничего из стека
        }

        if (!anyChange && maskedMdc == event.getMDCPropertyMap()) {
            delegate.doAppend(event); // zero overhead path
        } else {
            delegate.doAppend(new SanitizedLoggingEvent(event, outMsg, maskedMdc, /*throwableProxy*/ null));
        }
    }

    static boolean isPurify(Appender<?> app) {
        return app instanceof PurifyAppender;
    }
}
