/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.jul;

import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.api.model.Mode;
import io.puriflow4j.logs.core.categorize.ExceptionClassifier;
import io.puriflow4j.logs.core.shorten.EmbeddedStacktraceShortener;
import io.puriflow4j.logs.core.shorten.ExceptionShortener;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.logging.*;

/**
 * JUL installer: wraps all handlers on all known loggers (incl. root).
 * Idempotent: skips handlers already wrapped with PurifyJULHandler.
 * JUL has no async controls here — we simply preserve user's handlers as-is.
 */
public final class PuriflowJULInstaller {

    private final Sanitizer sanitizer;
    private final ExceptionShortener shortener;
    private final EmbeddedStacktraceShortener embeddedShortener;
    private final ExceptionClassifier classifier;
    private final Mode mode;

    public PuriflowJULInstaller(
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

    /** Public API: install now. Safe to call multiple times. */
    public void install() throws UnsupportedEncodingException {
        LogManager lm = LogManager.getLogManager();
        // root logger in JUL has empty name ""
        Logger root = Logger.getLogger("");
        Set<Logger> all = new LinkedHashSet<>();
        all.add(root);

        // Enumerate known loggers from LogManager (may be incomplete if created later)
        Enumeration<String> names = lm.getLoggerNames();
        while (names.hasMoreElements()) {
            String n = names.nextElement();
            Logger l = lm.getLogger(n);
            if (l != null) all.add(l);
        }

        for (Logger l : all) {
            wrapHandlers(l);
        }
    }

    private void wrapHandlers(Logger logger) throws UnsupportedEncodingException {
        Handler[] hs = logger.getHandlers();
        for (Handler h : hs) {
            if (h instanceof PurifyJULHandler) continue; // already wrapped
            // detach original
            logger.removeHandler(h);
            // attach purify wrapper that delegates to original
            PurifyJULHandler ph = new PurifyJULHandler(h, sanitizer, shortener, embeddedShortener, classifier, mode);
            logger.addHandler(ph);
        }
    }
}
