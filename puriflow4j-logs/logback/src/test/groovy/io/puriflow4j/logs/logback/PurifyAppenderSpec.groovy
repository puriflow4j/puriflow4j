/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.Appender
import io.puriflow4j.core.api.Detector
import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.DetectionResult
import io.puriflow4j.core.api.model.Mode
import io.puriflow4j.logs.core.categorize.ExceptionClassifier
import io.puriflow4j.logs.core.model.ThrowableView
import io.puriflow4j.logs.core.shorten.ExceptionShortener
import org.slf4j.MarkerFactory
import spock.lang.Specification

import java.sql.SQLException
import java.util.regex.Pattern

/**
 * Additional PurifyAppender tests focused on:
 *  - DRY_RUN behavior (warning emission, pass-through original),
 *  - STRICT behavior (redact on any change; pass-through otherwise),
 *  - recursion guard for internal puriflow.* loggers and internal marker.
 *
 * Uses the same tiny inlined detector as in your existing spec and the same shortener.
 */
class PurifyAppenderDryRunAndStrictSpec extends Specification {

    Appender<ILoggingEvent> delegate = Mock()
    ExceptionClassifier classifier = Mock()

    def setup() {
        // PurifyAppender.start() touches delegate context; ignore it in interaction counts.
        _ * delegate.getContext() >> null
    }

    // ---------- helpers (same style as your spec) ---------------------------

    private static Sanitizer mkSanitizer() {
        Detector det = new Detector() {
            // Mask KV secrets (secret=..., password=...) and bare token "token123"
            private final Pattern P_KV   = Pattern.compile("(?i)\\b(secret|password)\\s*[:=]\\s*([^\\s,;]+)")
            private final Pattern P_BARE = Pattern.compile("\\btoken123\\b")

            @Override
            DetectionResult detect(String s) {
                if (s == null || s.isEmpty()) return DetectionResult.empty()
                def spans = new ArrayList<DetectionResult.Span>()
                def m = P_KV.matcher(s)
                while (m.find()) spans.add(new DetectionResult.Span(m.start(2), m.end(2), "kv", "[MASKED]"))
                def b = P_BARE.matcher(s)
                while (b.find()) spans.add(new DetectionResult.Span(b.start(), b.end(), "bare", "[MASKED]"))
                return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans))
            }
        }
        new Sanitizer(List.of(det), Action.MASK)
    }

    private static ExceptionShortener mkShortener(Sanitizer san, boolean shorten) {
        // Hide common frameworks so compact output is stable across JDKs
        new ExceptionShortener(san, shorten, 5,
                List.of("org.springframework", "jakarta.servlet", "java.", "jdk.", "org.apache"))
    }

    private static ILoggingEvent evt(String logger, String msg, Map<String,String> mdc = [:], Throwable t = null) {
        def e = new LoggingEvent()
        e.loggerName = logger
        e.message = msg
        e.argumentArray = null
        e.mdcPropertyMap = mdc
        if (t != null) e.throwableProxy = new ThrowableProxy(t)
        e
    }

    // ========================= DRY_RUN tests ================================

    def "DRY_RUN: emits one WARN via the same delegate and forwards the original unchanged (message-only finding)"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, false)
        def app = new PurifyAppender(delegate, sanitizer, shortener, null, classifier, Mode.DRY_RUN)
        app.start()

        when:
        def original = evt("demo.Logger", "password=abc")
        app.doAppend(original)

        then: "first call forwards the original untouched"
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "password=abc" &&
                    ev.loggerName == "demo.Logger"
        })

        and: "second call emits a synthetic WARN with DRY_RUN details"
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.loggerName == "puriflow.logs.dryrun" &&
                    ev.level == Level.WARN &&
                    ev.formattedMessage.contains("DRY-RUN") &&
                    ev.formattedMessage.contains("logger='demo.Logger'") &&
                    ev.formattedMessage.contains("detected") &&
                    ev.getMDCPropertyMap() != null // not null even if empty
        })
    }

    def "DRY_RUN: warning is emitted even when MDC is empty (regression test)"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, false)
        def app = new PurifyAppender(delegate, sanitizer, shortener, null, classifier, Mode.DRY_RUN)
        app.start()

        when:
        app.doAppend(evt("demo.Logger", "secret=top", [:]))

        then:
        1 * delegate.doAppend(_ as ILoggingEvent) // original
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.loggerName == "puriflow.logs.dryrun" &&
                    ev.level == Level.WARN &&
                    ev.formattedMessage.contains("DRY-RUN")
        })
        0 * _
    }

    def "DRY_RUN: throwable-only detection still produces a WARN and preserves original throwable"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, false)
        def app = new PurifyAppender(delegate, sanitizer, shortener, null, classifier, Mode.DRY_RUN)
        app.start()

        when:
        def ex = new SQLException("password=boom")
        def original = evt("demo.Logger", "no secrets here", [:], ex)
        app.doAppend(original)

        then:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            // original event goes first, with its throwable untouched
            ev.formattedMessage == "no secrets here" &&
                    ev.getThrowableProxy() instanceof ThrowableProxy
        })

        and:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.loggerName == "puriflow.logs.dryrun" &&
                    ev.level == Level.WARN &&
                    ev.formattedMessage.contains("DRY-RUN") &&
                    ev.formattedMessage.contains("logger='demo.Logger'")
        })

        and:
        0 * _
    }

    // ========================= STRICT tests =================================

    def "STRICT: when nothing would change (no secrets), original content is forwarded untouched"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, false)
        def app = new PurifyAppender(delegate, sanitizer, shortener, null, classifier, Mode.STRICT)
        app.start()

        when:
        app.doAppend(evt("demo.Logger", "hello world"))

        then:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "hello world" &&
                    ev.getThrowableProxy() == null
        })
        0 * _
    }

    def "STRICT: secrets in message cause full redaction and throwable is dropped if present"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, false)
        def app = new PurifyAppender(delegate, sanitizer, shortener, null, classifier, Mode.STRICT)
        app.start()

        when:
        app.doAppend(evt("demo.Logger", "token123", [:], new RuntimeException("x")))

        then:
        1 * classifier.classify(_ as ThrowableView)

        and:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "[REDACTED_LOG]" &&
                    ev.getThrowableProxy() == null
        })
        0 * _
    }

    def "STRICT: secrets ONLY in MDC still cause full redaction"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, false)
        def app = new PurifyAppender(delegate, sanitizer, shortener, null, classifier, Mode.STRICT)
        app.start()

        when:
        app.doAppend(evt("demo.Logger", "plain", [password: "abc"]))

        then:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "[REDACTED_LOG]" &&
                    ev.getThrowableProxy() == null
        })
        0 * _
    }

    // ===================== Recursion guard tests ============================

    def "internal logs (puriflow.*) are not processed and are simply forwarded"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, false)
        def app = new PurifyAppender(delegate, sanitizer, shortener, null, classifier, Mode.DRY_RUN)
        app.start()

        when:
        // This imitates our own synthetic warning (but w/o marker); still under puriflow.* namespace
        def internal = evt("puriflow.logs.dryrun", "internal line")
        app.doAppend(internal)

        then:
        1 * delegate.doAppend({ ILoggingEvent ev -> ev.formattedMessage == "internal line" })
        0 * _
    }

    def "events tagged with internal marker are forwarded once and not re-processed"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, false)
        def app = new PurifyAppender(delegate, sanitizer, shortener, null, classifier, Mode.DRY_RUN)
        app.start()

        when:
        def ev = (LoggingEvent) evt("some.logger", "will be treated as internal")
        ev.addMarker(MarkerFactory.getMarker("P4J_INTERNAL"))
        app.doAppend(ev)

        then:
        1 * delegate.doAppend({ ILoggingEvent e -> e.formattedMessage == "will be treated as internal" })
        0 * _
    }
}