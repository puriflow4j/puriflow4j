package io.puriflow4j.logs.log4j2

import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.Mode
import io.puriflow4j.logs.core.categorize.ExceptionClassifier
import io.puriflow4j.logs.core.model.ThrowableView
import io.puriflow4j.logs.core.sanitize.MdcSanitizer
import io.puriflow4j.logs.core.sanitize.MessageSanitizer
import io.puriflow4j.logs.core.shorten.EmbeddedStacktraceShortener
import io.puriflow4j.logs.core.shorten.ExceptionShortener
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.impl.ContextDataFactory
import org.apache.logging.log4j.core.impl.Log4jLogEvent
import org.apache.logging.log4j.message.SimpleMessage
import spock.lang.Specification

/**
 * Unit tests for PuriflowRewritePolicy.
 *
 * We don't verify concrete masking placeholders here (no detectors connected).
 * Instead we assert structural behavior:
 *  - identity when nothing changes,
 *  - exception rendering is appended to message and original Throwable is dropped,
 *  - STRICT mode produces [REDACTED_LOG],
 *  - MDC is preserved (or carried over) in rewritten events.
 */
class PuriflowRewritePolicySpec extends Specification {

    // --- tiny no-op classifier so we avoid mocks ---
    static final class NoopClassifier implements ExceptionClassifier {
        @Override
        CategoryResult classify(ThrowableView view) { return CategoryResult.NONE }
    }

    private static PuriflowRewritePolicy newPolicy(Mode mode) {
        def sanitizer = new Sanitizer(List.of(), Action.NONE) // no detectors -> no changes to strings
        def shortener = new ExceptionShortener(sanitizer, /*shorten*/ false, /*maxDepth*/ 3, /*hidePkgs*/ List.of())
        def embedded  = new EmbeddedStacktraceShortener(sanitizer, 3, List.of())
        def classifier = new NoopClassifier()
        return new PuriflowRewritePolicy(sanitizer, shortener, embedded, classifier, mode)
    }

    private static LogEvent evt(String logger = "demo.Foo",
                                String msg = "hello",
                                Map<String,String> mdc = Map.of(),
                                Throwable th = null) {
        return Log4jLogEvent.newBuilder()
                .setLoggerName(logger)
                .setLoggerFqcn("demo.Foo")
                .setLevel(Level.INFO)
                .setContextData(ContextDataFactory.createContextData(mdc))
                .setMessage(new SimpleMessage(msg))
                .setThrown(th)
                .setThreadName("t")
                .setTimeMillis(System.currentTimeMillis())
                .build()
    }

    def "no-op path: when message, MDC and throwable don't change, the same LogEvent instance is returned"() {
        given:
        def policy = newPolicy(Mode.MASK)
        def source = evt("demo.Foo", "plain message", Map.of("traceId","abc"), /*th*/ null)

        when:
        def out = policy.rewrite(source)

        then: "identity preserved (policy short-circuits)"
        out.is(source)
        out.getMessage().formattedMessage == "plain message"
        out.getThrown() == null
        out.getContextData().toMap() == [traceId:"abc"]
    }

    def "when throwable is present, policy appends rendered exception and drops the original Throwable"() {
        given:
        def policy = newPolicy(Mode.MASK)
        def ex = new RuntimeException("boom")
        def source = evt("demo.Foo", "before", Map.of("k","v"), ex)

        when:
        def out = policy.rewrite(source)

        then: "a new event is produced"
        !out.is(source)

        and: "original Throwable is dropped to avoid raw stack traces"
        out.getThrown() == null

        and: "message contains original text + newline + rendered exception"
        def txt = out.getMessage().formattedMessage
        txt.startsWith("before\n")
        txt.contains("RuntimeException")
        txt.contains("boom")

        and: "MDC is preserved"
        out.getContextData().toMap() == [k:"v"]
    }

    def "STRICT mode: any change leads to full redaction and dropping Throwable"() {
        given:
        def policy = newPolicy(Mode.STRICT)
        def ex = new IllegalStateException("oops")
        def source = evt("demo.Bar", "anything", Map.of("u","1"), ex)

        when:
        def out = policy.rewrite(source)

        then: "Throwable is removed"
        out.getThrown() == null

        and: "payload is redacted"
        out.getMessage().formattedMessage == "[REDACTED_LOG]"

        and: "MDC is still carried to the new event (sanitized copy)"
        out.getContextData().toMap() == [u:"1"]
    }

    def "if only exception exists and original message is empty, output is just rendered exception"() {
        given:
        def policy = newPolicy(Mode.MASK)
        def ex = new RuntimeException("E")
        def source = Log4jLogEvent.newBuilder()
                .setLoggerName("demo.Baz")
                .setLoggerFqcn("demo.Baz")
                .setLevel(Level.ERROR)
                .setContextData(ContextDataFactory.createContextData(Map.of()))
                .setMessage(new SimpleMessage("")) // empty payload
                .setThrown(ex)
                .build()

        when:
        def out = policy.rewrite(source)

        then: "Throwable removed, message replaced by rendered exception"
        out.getThrown() == null
        def txt = out.getMessage().formattedMessage
        txt.contains("RuntimeException")
        !txt.startsWith("\n") // no leading newline when original message is empty
    }
}
