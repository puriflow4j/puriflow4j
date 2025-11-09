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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.appender.rewrite.RewriteAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

/**
 * High-performance wiring for Log4j2:
 *   NON-async appender "A" becomes:  Async("PURIFY_ASYNC_A") -> Rewrite("PURIFY_WRAPPER_A") -> A
 *   Existing AsyncAppender is left intact (safe default).
 *
 * Notes:
 *  - Uses RewriteAppender.createAppender(...) as requested (no AppenderSet).
 *  - Sanitization runs off the caller thread (on Async worker).
 *  - Idempotent (skips already wrapped).
 */
public final class PuriflowLog4j2Installer {

    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final boolean DEFAULT_BLOCKING = true;
    private static final long DEFAULT_SHUTDOWN_TIMEOUT_MILLIS = 5000L;
    private static final boolean DEFAULT_INCLUDE_LOCATION = false;

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

    public void install() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration cfg = ctx.getConfiguration();

        final Map<String, Appender> appenders = new LinkedHashMap<>(cfg.getAppenders());

        for (Map.Entry<String, Appender> e : appenders.entrySet()) {
            final String origName = e.getKey();
            final Appender original = e.getValue();
            if (origName == null || original == null) continue;

            // Skip already-processed
            if (origName.startsWith("PURIFY_WRAPPER_") || origName.startsWith("PURIFY_ASYNC_")) continue;

            // If it's already async — leave as-is (optionally, later wrap its children)
            if (original instanceof AsyncAppender) continue;

            // 1) Policy for message/MDC/exception (STRICT, classification, shortening)
            final PuriflowRewritePolicy policy =
                    new PuriflowRewritePolicy(sanitizer, shortener, embeddedShortener, classifier, mode);

            // 2) Create RewriteAppender that targets the original appender by name.
            //    Signature: createAppender(name, ignoreExceptions("true"/"false"), AppenderRef[], policy, config)
            final String rewriteName = "PURIFY_WRAPPER_" + origName;
            final AppenderRef[] rewriteRefs = new AppenderRef[] {AppenderRef.createAppenderRef(origName, null, null)};
            final RewriteAppender rewrite = RewriteAppender.createAppender(
                    /* name              */ rewriteName,
                    /* ignoreExceptions  */ "true",
                    /* appenderRefs      */ rewriteRefs,
                    /* config            */ cfg,
                    /* rewritePolicy     */ policy,
                    /* filter            */ null);
            rewrite.start();
            cfg.addAppender(rewrite);

            // 3) Create AsyncAppender that targets the rewrite wrapper.
            final String asyncName = "PURIFY_ASYNC_" + origName;
            final AppenderRef[] asyncRefs = new AppenderRef[] {AppenderRef.createAppenderRef(rewriteName, null, null)};

            final AsyncAppender async = AsyncAppender.newBuilder()
                    .setName(asyncName)
                    .setConfiguration(cfg)
                    .setAppenderRefs(asyncRefs)
                    .setBlocking(DEFAULT_BLOCKING)
                    .setBufferSize(DEFAULT_BUFFER_SIZE)
                    .setShutdownTimeout(DEFAULT_SHUTDOWN_TIMEOUT_MILLIS)
                    .setIncludeLocation(DEFAULT_INCLUDE_LOCATION)
                    .build();
            async.start();
            cfg.addAppender(async);

            // 4) Replace original appender with our async wrapper in all loggers (incl. root)
            replaceAppenderInAllLoggers(cfg, origName, async);
        }

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
