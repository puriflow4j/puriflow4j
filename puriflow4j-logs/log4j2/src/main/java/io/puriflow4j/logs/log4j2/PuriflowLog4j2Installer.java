/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.log4j2;

import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.api.model.Mode;
import io.puriflow4j.logs.core.categorize.ExceptionClassifier;
import io.puriflow4j.logs.core.shorten.EmbeddedStacktraceShortener;
import io.puriflow4j.logs.core.shorten.ExceptionShortener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.rewrite.RewriteAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

/**
 * Log4j2 wiring that keeps user's async/sync choice intact:
 *
 * For ANY appender "A" (sync or async), we add:
 *   LoggerConfig -> Rewrite("PURIFY_WRAPPER_A") -> A
 *
 * - We do NOT introduce extra AsyncAppender.
 * - If user already uses AsyncAppender or AsyncLoggers, the async behavior remains.
 * - Idempotent (skips already wrapped).
 */
public final class PuriflowLog4j2Installer {

    private final Sanitizer sanitizer;
    private final ExceptionShortener shortener;
    private final EmbeddedStacktraceShortener embeddedShortener;
    private final ExceptionClassifier classifier;
    private final Mode mode;

    public PuriflowLog4j2Installer(
            Sanitizer sanitizer,
            ExceptionShortener shortener,
            EmbeddedStacktraceShortener embeddedShortener,
            ExceptionClassifier classifier,
            Mode mode) {
        this.sanitizer = Objects.requireNonNull(sanitizer);
        this.shortener = Objects.requireNonNull(shortener);
        this.embeddedShortener = embeddedShortener;
        this.classifier = Objects.requireNonNull(classifier);
        this.mode = Objects.requireNonNull(mode);
    }

    /**
     * Public API kept as before. Now it preserves user's async/sync setup.
     */
    public void install() {
        LoggerContext ctx = LoggerContext.getContext(false);
        if (ctx == null) return;
        installPreservingAsync(ctx);
    }

    // --- Internal implementation that preserves async/sync ---
    public void installPreservingAsync(LoggerContext ctx) {
        final Configuration cfg = ctx.getConfiguration();

        // Snapshot to avoid concurrent modification while adding new appenders
        final Map<String, Appender> appenders = new LinkedHashMap<>(cfg.getAppenders());

        for (Map.Entry<String, Appender> e : appenders.entrySet()) {
            final String origName = e.getKey();
            final Appender original = e.getValue();
            if (origName == null || original == null) continue;

            // Skip already-processed
            if (origName.startsWith("PURIFY_WRAPPER_")) continue;

            // Build policy (message + MDC + exception)
            final PuriflowRewritePolicy policy =
                    new PuriflowRewritePolicy(sanitizer, shortener, embeddedShortener, classifier, mode);

            // Create RewriteAppender that targets the original appender by name.
            final String rewriteName = "PURIFY_WRAPPER_" + origName;
            final AppenderRef[] rewriteRefs = new AppenderRef[] {AppenderRef.createAppenderRef(origName, null, null)};

            final RewriteAppender rewrite = RewriteAppender.createAppender(
                    /* name             */ rewriteName,
                    /* ignoreExceptions */ "true",
                    /* appenderRefs     */ rewriteRefs,
                    /* config           */ cfg,
                    /* rewritePolicy    */ policy,
                    /* filter           */ null);

            rewrite.start();
            cfg.addAppender(rewrite);

            // Replace original appender with our rewrite in all loggers (incl. root).
            // This yields:
            //   LoggerConfig -> Rewrite(PURIFY_WRAPPER_orig) -> orig
            // If 'orig' is AsyncAppender, async stays downstream (behaviour preserved).
            replaceAppenderInAllLoggers(cfg, origName, rewrite);
        }

        // Publish changes immediately so the very first log is sanitized
        ctx.updateLoggers();
    }

    private static void replaceAppenderInAllLoggers(Configuration cfg, String originalName, Appender replacement) {
        for (LoggerConfig lc : cfg.getLoggers().values()) {
            if (lc.getAppenders().containsKey(originalName)) {
                lc.removeAppender(originalName);
                lc.addAppender(replacement, null, null);
            }
        }
        final LoggerConfig rootCfg = cfg.getRootLogger();
        if (rootCfg.getAppenders().containsKey(originalName)) {
            rootCfg.removeAppender(originalName);
            rootCfg.addAppender(replacement, null, null);
        }
    }
}
