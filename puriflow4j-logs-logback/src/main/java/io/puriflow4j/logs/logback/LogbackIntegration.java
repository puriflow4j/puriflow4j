/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.logback;

import ch.qos.logback.classic.LoggerContext;
import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.report.Reporter;
import java.util.List;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * Single public entrypoint to activate masking for Logback.
 * It detects LoggerContext, registers a reset-resistant listener,
 * and performs initial wrapping immediately.
 *
 * IMPORTANT:
 *  - Method signature uses only core types (Reporter, Sanitizer, List),
 *    so callers (e.g., Spring starter) do not need Logback API at compile time.
 *  - This class MUST live in the logback-adapter module where Logback is available at compile time.
 */
public final class LogbackIntegration {
    private LogbackIntegration() {}

    public static boolean activateIfLogback(
            Reporter reporter, Sanitizer sanitizer, List<String> onlyLoggers, List<String> ignoreLoggers) {
        ILoggerFactory lf = LoggerFactory.getILoggerFactory();
        if (!(lf instanceof LoggerContext ctx)) {
            // Not a Logback backend — exit
            return false;
        }

        var listener = new PurifyLoggerContextListener(reporter, sanitizer, onlyLoggers, ignoreLoggers);

        boolean already = ctx.getCopyOfListenerList().stream()
                .anyMatch(l -> l.getClass().getName().equals(PurifyLoggerContextListener.class.getName()));
        if (!already) {
            ctx.addListener(listener);
            listener.onStart(ctx); // initial wrapping now
        }

        return true;
    }
}
