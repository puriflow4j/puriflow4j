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
 * Comprehensive behavior tests for PurifyAppender.
 *
 * What we verify:
 *  - DRY_RUN: original is always forwarded; a single WARN is emitted when something is detected.
 *  - MASK: message/MDC sanitization, exception rendering, throwable dropping, zero-overhead passthrough.
 *  - STRICT: redaction rules per current implementation (see notes below).
 *  - Recursion guards: puriflow.* namespace and internal marker.
 *
 * IMPORTANT about STRICT semantics (as implemented in the class under test):
 *  - If ONLY MDC changes (message didn't change and no rendered exception), STRICT keeps the original message,
 *    but still drops the throwable and forwards sanitized MDC.
 *  - If the message changes for any reason (masking/shortening/rendered exception), STRICT emits "[REDACTED_LOG]"
 *    and drops the throwable.
 */
class PurifyAppenderSpec extends Specification {

    Appender<ILoggingEvent> delegate = Mock()
    ExceptionClassifier classifier = Mock()

    def setup() {
        // AppenderBase.start() touches delegate.getContext(); make it non-counting.
        _ * delegate.getContext() >> null
    }

    // ------------------------ helpers ---------------------------------------

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
        // Keep output deterministic across different JDKs by hiding common frameworks.
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

    // ============================= DRY_RUN ===================================

    def "DRY_RUN: forwards original unchanged and emits one WARN when message contains secrets"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, false)
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.DRY_RUN)
        app.start()

        when:
        def original = evt("demo.Logger", "password=abc")
        app.doAppend(original)

        then: "original goes through as-is"
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "password=abc" &&
                    ev.loggerName == "demo.Logger"
        })

        and: "one synthetic WARN via the SAME delegate"
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.loggerName == "puriflow.logs.dryrun" &&
                    ev.level == Level.WARN &&
                    ev.formattedMessage.contains("DRY-RUN") &&
                    ev.formattedMessage.contains("logger='demo.Logger'") &&
                    ev.getMDCPropertyMap() != null
        })
        0 * _
    }

    def "DRY_RUN: warning is emitted even when MDC is empty (regression)"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, false)
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.DRY_RUN)
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

    def "DRY_RUN: throwable-only detection produces a WARN; original throwable is preserved"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, false)
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.DRY_RUN)
        app.start()

        when:
        def ex = new SQLException("password=boom")
        app.doAppend(evt("demo.Logger", "no secrets", [:], ex))

        then:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "no secrets" &&
                    ev.getThrowableProxy() instanceof ThrowableProxy
        })
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.loggerName == "puriflow.logs.dryrun" &&
                    ev.level == Level.WARN &&
                    ev.formattedMessage.contains("DRY-RUN") &&
                    ev.formattedMessage.contains("logger='demo.Logger'")
        })
        0 * _
    }

    def "DRY_RUN: no findings -> only original is forwarded, no WARN"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, false)
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.DRY_RUN)
        app.start()

        when:
        app.doAppend(evt("demo.Logger", "benign", [traceId: "x"]))

        then:
        1 * delegate.doAppend({ ILoggingEvent ev -> ev.formattedMessage == "benign" })
        0 * _
    }

    // ================================ MASK ===================================

    def "MASK: message is sanitized; MDC empty; throwable absent"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, false)
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.MASK)
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

    def "MASK: MDC is sanitized while message stays intact"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, false)
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.MASK)
        app.start()

        when:
        app.doAppend(evt("demo.Logger", "plain", [password: "abc"]))

        then:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "plain" &&
                    ev.getMDCPropertyMap().get("password") == "[MASKED]"
        })
        0 * _
    }

    def "MASK: exception is rendered into message and raw throwable is dropped; classifier label may appear"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, true) // compact
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.MASK)
        app.start()

        when:
        def ex = new SQLException("password=topsecret")
        app.doAppend(evt("demo.Logger", "boom", [:], ex))

        then:
        1 * classifier.classify(_ as ThrowableView) >> new ExceptionClassifier.CategoryResult("DB")

        and:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            def parts = ev.formattedMessage.split("\\R", -1)
            assert parts.length >= 2
            def excLine = parts[1]
            excLine.contains("[DB]") &&
                    excLine.toLowerCase().contains("sqlexception") &&
                    excLine.contains("[MASKED]") &&          // sanitized inside shortener
                    ev.getThrowableProxy() == null            // dropped
        })
        0 * _
    }

    def "MASK: zero-overhead path when nothing changes (original content forwarded)"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, false)
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.MASK)
        app.start()

        when:
        def original = evt("demo.Logger", "no secrets here")
        app.doAppend(original)

        then:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "no secrets here" &&
                    ev.getThrowableProxy() == null
        })
        0 * _
    }

    // ================================ STRICT =================================

    def "STRICT: benign message and MDC -> original is forwarded untouched"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, false)
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.STRICT)
        app.start()

        when:
        app.doAppend(evt("demo.Logger", "hello", [:], null))

        then:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "hello" &&
                    ev.getThrowableProxy() == null
        })
        0 * _
    }

    def "STRICT: secrets in message cause full redaction and throwable is dropped"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, false)
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.STRICT)
        app.start()

        when:
        app.doAppend(evt("demo.Logger", "token123", [:], new RuntimeException("x")))

        then:
        1 * classifier.classify(_ as ThrowableView) >> null

        and:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "[REDACTED_LOG]" &&
                    ev.getThrowableProxy() == null
        })
        0 * _
    }

    def "STRICT: ONLY-MDC change keeps original message text but drops throwable and forwards sanitized MDC (matches current code)"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, false)
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.STRICT)
        app.start()

        when:
        app.doAppend(evt("demo.Logger", "plain", [password: "abc"], null))

        then:
        1 * delegate.doAppend({ ILoggingEvent ev ->
            // Current implementation: message unchanged, throwable null, MDC sanitized.
            ev.formattedMessage == "plain" &&
                    ev.getThrowableProxy() == null &&
                    ev.getMDCPropertyMap().get("password") == "[MASKED]"
        })
        0 * _
    }

    def "STRICT: rendering an exception (even if message clean) counts as change -> full redaction"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, true)
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.STRICT)
        app.start()

        when:
        app.doAppend(evt("demo.Logger", "benign", [:], new SQLException("password=p")))

        then:
        1 * classifier.classify(_ as ThrowableView) >> null
        1 * delegate.doAppend({ ILoggingEvent ev ->
            ev.formattedMessage == "[REDACTED_LOG]" &&
                    ev.getThrowableProxy() == null
        })
        0 * _
    }

    // ============================== recursion ================================

    def "puriflow.* logs are not processed (forwarded as-is)"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, false)
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.DRY_RUN)
        app.start()

        when:
        def internal = evt("puriflow.logs.dryrun", "internal line")
        app.doAppend(internal)

        then:
        1 * delegate.doAppend({ ILoggingEvent ev -> ev.formattedMessage == "internal line" })
        0 * _
    }

    def "events tagged with internal marker are forwarded once and not re-processed"() {
        given:
        def san = mkSanitizer()
        def sh  = mkShortener(san, false)
        def app = new PurifyAppender(delegate, san, sh, null, classifier, Mode.DRY_RUN)
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