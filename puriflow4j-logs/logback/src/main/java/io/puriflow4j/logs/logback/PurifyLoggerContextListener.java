/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.logback;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.core.Appender;
import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.api.model.Mode;
import io.puriflow4j.core.report.Reporter;
import io.puriflow4j.logs.core.EmbeddedStacktraceShortener;
import io.puriflow4j.logs.core.ExceptionShortener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Attaches PurifyAppender wrappers to ALL loggers/appender chains,
 * and repeats wrapping on LoggerContext.reset().
 */
public final class PurifyLoggerContextListener implements LoggerContextListener {

    private final Reporter reporter;
    private final Sanitizer sanitizer;
    private final ExceptionShortener shortener;
    private final EmbeddedStacktraceShortener embeddedShortener;
    private final Mode mode;
    private final List<String> only; // lowercase names; empty => all
    private final List<String> ignore; // lowercase names

    public PurifyLoggerContextListener(
            Reporter reporter,
            Sanitizer sanitizer,
            ExceptionShortener shortener,
            EmbeddedStacktraceShortener embeddedShortener,
            Mode mode,
            List<String> onlyLoggers,
            List<String> ignoreLoggers) {
        this.reporter = Objects.requireNonNull(reporter);
        this.sanitizer = Objects.requireNonNull(sanitizer);
        this.shortener = Objects.requireNonNull(shortener);
        this.embeddedShortener = Objects.requireNonNull(embeddedShortener);
        this.mode = Objects.requireNonNull(mode);
        this.only = toLower(onlyLoggers);
        this.ignore = toLower(ignoreLoggers);
    }

    private static List<String> toLower(List<String> in) {
        var out = new ArrayList<String>();
        if (in != null) for (String s : in) if (s != null) out.add(s.toLowerCase(Locale.ROOT));
        return out;
    }

    @Override
    public boolean isResetResistant() {
        return true;
    }

    @Override
    public void onStart(LoggerContext context) {
        wrapAll(context);
    }

    @Override
    public void onReset(LoggerContext context) {
        wrapAll(context);
    }

    @Override
    public void onStop(LoggerContext context) {
        /* no-op */
    }

    @Override
    public void onLevelChange(Logger logger, ch.qos.logback.classic.Level level) {
        /* no-op */
    }

    private void wrapAll(LoggerContext ctx) {
        List<Logger> loggers = new ArrayList<>(ctx.getLoggerList());
        Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        if (!loggers.contains(root)) loggers.add(root);

        for (Logger logger : loggers) {
            String lname = (logger.getName() == null ? "" : logger.getName().toLowerCase(Locale.ROOT));
            if (!only.isEmpty() && !only.contains(lname)) continue;
            if (!ignore.isEmpty() && ignore.contains(lname)) continue;

            List<Appender<ILoggingEvent>> current = new ArrayList<>();
            for (var it = logger.iteratorForAppenders(); it.hasNext(); ) current.add(it.next());

            for (Appender<ILoggingEvent> app : current) {
                if (PurifyAppender.isPurify(app)) continue;

                if (app instanceof AsyncAppender async) {
                    wrapAsyncChildren(logger, async);
                } else {
                    wrapAtLogger(logger, app);
                }
            }
        }
    }

    private void wrapAtLogger(Logger logger, Appender<ILoggingEvent> app) {
        logger.detachAppender(app);

        var wrapper = new PurifyAppender(app, reporter, sanitizer, shortener, embeddedShortener, mode);
        wrapper.setContext(logger.getLoggerContext());
        wrapper.setName("PURIFY_WRAPPER_" + app.getName());
        wrapper.start();

        logger.addAppender(wrapper);
    }

    private void wrapAsyncChildren(Logger logger, AsyncAppender async) {
        List<Appender<ILoggingEvent>> children = new ArrayList<>();
        for (var it = async.iteratorForAppenders(); it.hasNext(); ) children.add(it.next());

        for (Appender<ILoggingEvent> child : children) {
            if (PurifyAppender.isPurify(child)) continue;
            async.detachAppender(child.getName());

            var wrapper = new PurifyAppender(child, reporter, sanitizer, shortener, embeddedShortener, mode);
            wrapper.setContext(logger.getLoggerContext());
            wrapper.setName("PURIFY_WRAPPER_" + child.getName());
            wrapper.start();

            async.addAppender(wrapper);
        }
    }
}
