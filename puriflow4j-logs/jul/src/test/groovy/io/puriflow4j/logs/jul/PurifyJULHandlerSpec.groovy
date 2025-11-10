package io.puriflow4j.logs.jul

import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.Mode
import io.puriflow4j.logs.core.categorize.ExceptionClassifier
import io.puriflow4j.logs.core.model.ThrowableView
import io.puriflow4j.logs.core.shorten.EmbeddedStacktraceShortener
import io.puriflow4j.logs.core.shorten.ExceptionShortener
import spock.lang.Specification

import java.util.logging.*

/**
 * Minimal tests for PurifyJULHandler focusing on wrapper behavior (not masking logic).
 *
 * We verify:
 *  - STRICT mode: any change -> message becomes "[REDACTED_LOG]" and Throwable is dropped.
 *  - No-change fast path: original record is delegated unchanged.
 *  - When placeholders {0} exist and there's no change, handler delegates the raw pattern ("Hi {0}")
 *    because JUL expects the Formatter to apply MessageFormat (handler doesn't pre-format on fast path).
 */
class PurifyJULHandlerSpec extends Specification {

    /** Capturing delegate that stores the last published LogRecord. */
    static class CapturingHandler extends Handler {
        volatile LogRecord last
        CapturingHandler() { setFormatter(new SimpleFormatter()) }
        @Override void publish(LogRecord record) { last = record }
        @Override void flush() { /* no-op */ }
        @Override void close() throws SecurityException { /* no-op */ }
    }

    private static ExceptionClassifier dummyClassifier() {
        return new ExceptionClassifier() {
            @Override
            ExceptionClassifier.CategoryResult classify(ThrowableView view) {
                return ExceptionClassifier.CategoryResult.NONE
            }
        }
    }

    private static PurifyJULHandler newHandler(Handler delegate, Mode mode) {
        def sanitizer = new Sanitizer(List.of(), Action.NONE)
        def shortener = new ExceptionShortener(sanitizer, /*shorten=*/ false, /*maxDepth=*/ 3, Collections.emptyList())
        def embedded  = new EmbeddedStacktraceShortener(sanitizer, /*maxDepth=*/ 3, Collections.emptyList())
        return new PurifyJULHandler(delegate, sanitizer, shortener, embedded, dummyClassifier(), mode)
    }

    def "STRICT: when a Throwable is present, record is redacted and Throwable is dropped"() {
        given:
        def delegate = new CapturingHandler()
        def handler  = newHandler(delegate, Mode.STRICT)

        and:
        def r = new LogRecord(Level.INFO, "anything")
        r.loggerName = "test.logger"
        r.thrown = new RuntimeException("boom")

        when:
        handler.publish(r)

        then:
        delegate.last != null
        delegate.last.message == "[REDACTED_LOG]"
        delegate.last.thrown == null
        and:
        r.message == "anything"
        r.thrown instanceof RuntimeException
    }

    def "fast path: when nothing changes, original record is delegated as-is"() {
        given:
        def delegate = new CapturingHandler()
        def handler  = newHandler(delegate, Mode.MASK)

        and:
        def r = new LogRecord(Level.INFO, "hello")
        r.loggerName = "test.logger"

        when:
        handler.publish(r)

        then:
        delegate.last != null
        delegate.last.message == "hello"
        delegate.last.thrown == null
    }

    def "fast path keeps raw MessageFormat pattern when no change (formatter applies it later)"() {
        given:
        def delegate = new CapturingHandler()
        def handler  = newHandler(delegate, Mode.MASK)

        and: "a record with a placeholder but no reasons to sanitize"
        def r = new LogRecord(Level.INFO, "Hi {0}")
        r.loggerName = "test.logger"
        r.parameters = ["Bob"] as Object[]

        when:
        handler.publish(r)

        then: "handler delegates the original record (still 'Hi {0}')"
        delegate.last != null
        delegate.last.message == "Hi {0}"
        delegate.last.thrown == null
    }
}