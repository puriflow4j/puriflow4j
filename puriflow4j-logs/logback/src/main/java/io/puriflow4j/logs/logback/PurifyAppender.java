/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.api.model.Finding;
import io.puriflow4j.core.api.model.Mode;
import io.puriflow4j.logs.core.categorize.ExceptionClassifier;
import io.puriflow4j.logs.core.model.ThrowableView;
import io.puriflow4j.logs.core.sanitize.MdcSanitizer;
import io.puriflow4j.logs.core.sanitize.MessageSanitizer;
import io.puriflow4j.logs.core.shorten.EmbeddedStacktraceShortener;
import io.puriflow4j.logs.core.shorten.ExceptionShortener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * # PurifyAppender (Logback) — simple, predictable behavior
 *
 * Wraps a real Logback {@link Appender} and makes logging safe.
 *
 * ## Modes
 * - **DRY_RUN**: never rewrites the original log. Instead, builds a cheap "probe"
 *   string (formatted message + exception headers + MDC), runs detectors, and if any
 *   sensitive content is found emits **one extra WARN** via the same delegate appender
 *   with logger name {@code puriflow.logs.dryrun}. Then it ALWAYS forwards the original
 *   event untouched. This makes DRY_RUN visible to users without changing their logs.
 *
 * - **MASK**: sanitizes message and MDC, optionally shortens embedded stack blocks,
 *   renders and sanitizes Throwable into text (and drops the raw Throwable).
 *   Forwards the sanitized clone. If literally nothing changed — forwards original.
 *
 * - **STRICT**: if ANY change would be required, forwards a redacted message
 *   {@code [REDACTED_LOG]} and drops Throwable. Otherwise forwards original.
 *
 * ## Recursion safety
 * - Events from {@code puriflow.*} logger namespace (including
 *   {@code puriflow.logs.dryrun}) are **not processed** and are passed straight to
 *   the delegate. This prevents feedback loops when DRY_RUN emits its WARN.
 * - We also tag internal WARNs with a marker and skip processing when it’s present.
 *
 * ## Why it “just works”
 * We do NOT use SLF4J calls inside the appender. We directly call
 * {@code delegate.doAppend(...)} for both the extra DRY_RUN WARN and the forward
 * of the (sanitized or original) event. That guarantees the warning is printed by
 * the same console/file appender the user already has, without extra XML or levels.
 */
public final class PurifyAppender extends AppenderBase<ILoggingEvent> {

    /** Name of the logger used for DRY_RUN warnings. */
    private static final String DRYRUN_LOGGER = "puriflow.logs.dryrun";

    /** Internal marker to tag our own synthetic DRY_RUN warning events. */
    private static final Marker P4J_INTERNAL = MarkerFactory.getMarker("P4J_INTERNAL");

    private final Appender<ILoggingEvent> delegate;
    private final MessageSanitizer msgSan;
    private final MdcSanitizer mdcSan;
    private final ExceptionShortener shortener;
    private final EmbeddedStacktraceShortener embeddedShortener; // may be null
    private final ExceptionClassifier classifier;
    private final Mode mode;

    /**
     * @param delegate          the real appender to which we forward output
     * @param sanitizer         composite sanitizer (detectors + replacement)
     * @param shortener         error renderer/shortener (knows "shorten" on/off)
     * @param embeddedShortener optional in-message stack shortener (may be null)
     * @param classifier        best-effort exception classifier
     * @param mode              DRY_RUN / MASK / STRICT
     */
    public PurifyAppender(
            Appender<ILoggingEvent> delegate,
            Sanitizer sanitizer,
            ExceptionShortener shortener,
            EmbeddedStacktraceShortener embeddedShortener,
            ExceptionClassifier classifier,
            Mode mode) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.msgSan = new MessageSanitizer(Objects.requireNonNull(sanitizer, "sanitizer"));
        this.mdcSan = new MdcSanitizer(sanitizer);
        this.shortener = Objects.requireNonNull(shortener, "shortener");
        this.embeddedShortener = embeddedShortener;
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @Override
    public void start() {
        // Mirror delegate context so both live in the same Logback context.
        if (getContext() == null && delegate.getContext() != null) {
            setContext(delegate.getContext());
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        final String loggerName = event.getLoggerName();

        // 0) Skip processing for Puriflow-owned channels or internal marker.
        //    We still delegate them (so they appear), but do not re-process to avoid loops.
        if (isInternalLogger(loggerName) || hasInternalMarker(event)) {
            delegate.doAppend(event);
            return;
        }

        // ----------------------------- DRY_RUN -----------------------------
        if (mode == Mode.DRY_RUN) {

            // Always pass through the original event unchanged.
            delegate.doAppend(event);

            // Build a cheap probe = message + throwable headers + MDC as "k=v".
            final String probe = buildProbeString(
                    event.getFormattedMessage(),
                    renderThrowableChainHeaders(event.getThrowableProxy()),
                    serializeMdc(event.getMDCPropertyMap()));

            var res = msgSan.applyDetailed(probe, loggerName);
            if (!res.findings().isEmpty()) {
                var types =
                        res.findings().stream().map(Finding::type).distinct().toList();

                // Emit ONE extra WARN (synthetic event) via the SAME delegate appender.
                // Using DRYRUN_LOGGER ensures users can route/mute it if they want,
                // AND it avoids interference with application loggers.

                delegate.doAppend(buildDryRunWarnEvent(
                        DRYRUN_LOGGER,
                        loggerName, // original source logger (for message)
                        res.findings().size(),
                        types,
                        event.getMDCPropertyMap()));
            }

            return;
        }

        // ----------------------- MASK / STRICT path -----------------------
        final String originalMsg = event.getFormattedMessage();
        String maskedMsg = msgSan.sanitize(originalMsg, loggerName);

        final Map<String, String> maskedMdc = mdcSan.sanitize(event.getMDCPropertyMap(), loggerName);

        final boolean shortenOn = shortener.isShortenEnabled();
        if (shortenOn && embeddedShortener != null && maskedMsg != null) {
            maskedMsg = embeddedShortener.shorten(maskedMsg, loggerName);
        }

        String renderedExc = null;
        if (event.getThrowableProxy() != null) {
            final ThrowableView tv = ThrowableViewAdapter.toView(event.getThrowableProxy());
            String label = null;
            try {
                var r = classifier.classify(tv);
                if (r != null && r.hasLabel()) label = r.label();
            } catch (Throwable ignore) {
                // best-effort: never break logging
            }
            renderedExc = shortener.format(tv, loggerName, label);
        }

        final boolean hasRendered = (renderedExc != null);

        // Compose final message; if we rendered the exception, append it and DROP raw Throwable
        String outMsg = maskedMsg;
        IThrowableProxy toForwardThrowable = event.getThrowableProxy();
        if (hasRendered) {
            outMsg = (outMsg == null || outMsg.isEmpty()) ? renderedExc : (outMsg + "\n" + renderedExc);
            toForwardThrowable = null; // prevent raw, unmasked stack printing by Logback
        }

        final boolean messageChanged = !Objects.equals(originalMsg, outMsg);
        final boolean mdcChanged = !Objects.equals(event.getMDCPropertyMap(), maskedMdc);
        final boolean anyChange = messageChanged || mdcChanged || hasRendered;

        // STRICT: redact everything if anything would change
        if (mode == Mode.STRICT && anyChange) {
            delegate.doAppend(new SanitizedLoggingEvent(
                    event, messageChanged ? "[REDACTED_LOG]" : originalMsg, maskedMdc, /* drop Throwable */ null));
            return;
        }

        // Zero-overhead path
        if (!anyChange) {
            delegate.doAppend(event);
            return;
        }

        // Forward sanitized clone
        delegate.doAppend(new SanitizedLoggingEvent(event, outMsg, maskedMdc, toForwardThrowable));
    }

    // ========================== helpers ==========================

    /** Treat any logger under "puriflow." namespace as internal; skip processing. */
    private static boolean isInternalLogger(String name) {
        return name != null && name.startsWith("puriflow.");
    }

    /** Skip processing if our internal marker is present (synthetic events). */
    private static boolean hasInternalMarker(ILoggingEvent e) {
        Marker m = e.getMarker();
        return m != null && (m.contains(P4J_INTERNAL) || P4J_INTERNAL.equals(m));
    }

    /**
     * Builds the synthetic WARN event for DRY_RUN.
     * We attach {@link #P4J_INTERNAL} so the wrapper won’t try to re-process it.
     *
     * @param dryRunLogger   logger name to attribute the warning to (puriflow.logs.dryrun)
     * @param sourceLogger   original logger that produced the sensitive content (for message text)
     * @param count          number of detections
     * @param types          distinct detector types found
     * @param mdc            MDC to copy (helps correlate by traceId, etc.)
     */
    private static LoggingEvent buildDryRunWarnEvent(
            String dryRunLogger, String sourceLogger, int count, List<String> types, Map<String, String> mdc) {
        final String msg = String.format(
                "[puriflow4j DRY-RUN]: detected %d sensitive fragment(s) in logger='%s' types=%s. "
                        + "Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize.",
                count, sourceLogger, types);

        LoggingEvent ev = new LoggingEvent();
        ev.setLoggerName(dryRunLogger);
        ev.setLevel(Level.WARN);
        ev.setMessage(msg);
        ev.setTimeStamp(System.currentTimeMillis());
        ev.addMarker(P4J_INTERNAL); // avoid re-processing by this wrapper

        if (mdc != null && !mdc.isEmpty()) {
            ev.setMDCPropertyMap(Map.copyOf(mdc));
        } else { // warning log disappeared without it
            ev.setMDCPropertyMap(new HashMap<>());
        }

        return ev;
    }

    /** Cheap probe: message + exception headers + MDC line. */
    private static String buildProbeString(String msg, String excHeaders, String mdc) {
        StringBuilder sb = new StringBuilder();
        if (msg != null && !msg.isEmpty()) sb.append(msg);
        if (excHeaders != null && !excHeaders.isEmpty()) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(excHeaders);
        }
        if (mdc != null && !mdc.isEmpty()) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(mdc);
        }
        return sb.toString();
    }

    /** Renders only "ClassName: message" lines for the throwable chain (no frames). */
    private static String renderThrowableChainHeaders(IThrowableProxy p) {
        if (p == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        while (p != null) {
            if (!first) sb.append("\nCaused by: ");
            first = false;
            sb.append(p.getClassName());
            String m = p.getMessage();
            if (m != null && !m.isEmpty()) sb.append(": ").append(m);
            p = p.getCause();
        }
        return sb.toString();
    }

    /** Serializes MDC as `k=v` pairs separated by a single space. */
    private static String serializeMdc(Map<String, String> mdc) {
        if (mdc == null || mdc.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var e : mdc.entrySet()) {
            if (!first) sb.append(' ');
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /** Public helper: tells whether an appender is already a Purify wrapper. */
    public static boolean isPurify(Appender<?> app) {
        return app instanceof PurifyAppender;
    }
}
