/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.logback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.Appender
import io.puriflow4j.core.api.Detector
import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.DetectionResult
import io.puriflow4j.core.api.model.Mode
import io.puriflow4j.core.report.Reporter
import io.puriflow4j.logs.core.categorize.ExceptionClassifier
import io.puriflow4j.logs.core.model.ThrowableView
import io.puriflow4j.logs.core.shorten.ExceptionShortener
import spock.lang.Specification

import java.sql.SQLException
import java.util.regex.Pattern

/**
 * Behavior tests for PurifyAppender.
 * - Uses a real Sanitizer (tiny inlined detector) and a real ExceptionShortener.
 * - Appender delegate is a Spock mock; we assert what goes through.
 *
 * IMPORTANT:
 * - Do NOT stub classifier in setup(); declare it explicitly in tests that use a throwable,
 *   otherwise `0 * _` will treat that call as unexpected.
 * - Avoid asserting loggerName to reduce brittleness (Logback may keep it null in unit tests).
 */
class PurifyAppenderSpec extends Specification {

    Appender<ILoggingEvent> delegate = Mock()
    Reporter reporter = Mock()
    ExceptionClassifier classifier = Mock()

    def setup() {
        // PurifyAppender.start() calls delegate.getContext(); ignore it in interactions counting
        _ * delegate.getContext() >> null
        // Do NOT stub classifier here. Each test will declare expectations when throwable is present.
    }

    // --- helpers -------------------------------------------------------------

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
        // Hide common frameworks so compact output is stable between JVMs
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

    // --- tests ---------------------------------------------------------------

    def "sanitizes message and forwards sanitized event"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, false) // no shortening
        def app = new PurifyAppender(delegate, reporter, sanitizer, shortener, null, classifier, Mode.MASK)
        app.start()

        when:
        app.doAppend(evt("demo.Logger", "secret=123"))

        then:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "secret=[MASKED]" &&
                    ev.getThrowableProxy() == null &&
                    ev.getMDCPropertyMap().isEmpty()
        })
        0 * _
    }

    def "zero-overhead: when nothing changes the original CONTENT is forwarded"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, false)
        def app = new PurifyAppender(delegate, reporter, sanitizer, shortener, null, classifier, Mode.MASK)
        app.start()

        when:
        def original = evt("demo.Logger", "no secrets here")
        app.doAppend(original)

        then:
        // Check content only; MDC map instance may differ even if logically equal.
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.getFormattedMessage() == "no secrets here" &&
                    ev.getThrowableProxy() == null
        })
        0 * _
    }

    def "renders exception via shortener and drops raw throwable; classifier label appears"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, true) // compact
        def app = new PurifyAppender(delegate, reporter, sanitizer, shortener, null, classifier, Mode.MASK)
        app.start()

        when:
        def ex = new SQLException("password=topsecret")
        app.doAppend(evt("demo.Logger", "boom", [:], ex))

        then:
        // Classifier is called exactly once when a throwable is present.
        1 * classifier.classify(_ as ThrowableView) >> new ExceptionClassifier.CategoryResult("DB")

        and:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            // First line is the original message "boom", exception is on the next line.
            def lines = ev.formattedMessage.split("\\R", -1) as List
            assert lines.size() >= 2

            def excLine = lines[1] // compact shortener puts summary here
            assert excLine.contains("[DB]")                 // label from classifier
            assert excLine.toLowerCase().contains("sqlexception")
            assert excLine.contains("[MASKED]")             // message masked by sanitizer inside shortener
            assert ev.getThrowableProxy() == null           // raw throwable suppressed
            true
        })
        0 * _
    }

    def "STRICT mode: any change redacts whole output and removes throwable"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, false)
        def app = new PurifyAppender(delegate, reporter, sanitizer, shortener, null, classifier, Mode.STRICT)
        app.start()

        when:
        app.doAppend(evt("demo.Logger", "password=abc", [:], new RuntimeException("x")))

        then:
        // In STRICT mode we still classify (best-effort) but the output is fully redacted.
        1 * classifier.classify(_ as ThrowableView) >> null

        and:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "[REDACTED_LOG]" &&
                    ev.getThrowableProxy() == null
        })
        0 * _
    }

    def "MDC is sanitized while message stays intact"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, false)
        def app = new PurifyAppender(delegate, reporter, sanitizer, shortener, null, classifier, Mode.MASK)
        app.start()

        when:
        app.doAppend(evt("demo.Logger", "msg", [token: "token123"]))

        then:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "msg" &&
                    ev.getMDCPropertyMap().get("token") == "[MASKED]"
        })
        0 * _
    }

    def "no changes at all → original CONTENT (message, mdc, no throwable)"() {
        given:
        def sanitizer = mkSanitizer()
        def shortener = mkShortener(sanitizer, false)
        def app = new PurifyAppender(delegate, reporter, sanitizer, shortener, null, classifier, Mode.MASK)
        app.start()

        when:
        def original = evt("demo.Logger", "plain", [:], null)
        app.doAppend(original)

        then:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.getFormattedMessage() == "plain" &&
                    ev.getThrowableProxy() == null &&
                    ev.getMDCPropertyMap().isEmpty()
        })
        0 * _
    }
}
