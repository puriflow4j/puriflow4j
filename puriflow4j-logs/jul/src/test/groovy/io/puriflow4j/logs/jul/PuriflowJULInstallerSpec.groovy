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
 * Minimal, focused tests for PuriflowJULInstaller.
 *
 * We DO NOT assert masking here. We only verify:
 *  - handlers on a concrete JUL Logger are wrapped with PurifyJULHandler;
 *  - install() is idempotent (no double wrapping on repeated calls).
 *
 * We isolate a dedicated logger with setUseParentHandlers(false) so we don't
 * mutate global JUL root configuration.
 */
class PuriflowJULInstallerSpec extends Specification {

    private Logger logger
    private Handler original

    def setup() {
        // Unique, isolated JUL logger
        logger = Logger.getLogger("puriflow4j.test." + UUID.randomUUID())
        logger.useParentHandlers = false

        // Original handler we can detect later
        original = new Handler() {
            @Override void publish(LogRecord record) { /* no-op */ }
            @Override void flush() { /* no-op */ }
            @Override void close() throws SecurityException { /* no-op */ }
        }

        // IMPORTANT for tests: Handler must have a non-null Formatter,
        // because Handler.setFormatter(...) rejects null.
        original.setFormatter(new SimpleFormatter())

        logger.addHandler(original)
    }

    def cleanup() {
        logger.handlers.each { h -> logger.removeHandler(h) }
    }

    private static ExceptionClassifier dummyClassifier() {
        return new ExceptionClassifier() {
            @Override
            ExceptionClassifier.CategoryResult classify(ThrowableView view) {
                return ExceptionClassifier.CategoryResult.NONE
            }
        }
    }

    private static PuriflowJULInstaller newInstaller() {
        def sanitizer = new Sanitizer(List.of(), Action.NONE)
        def shortener = new ExceptionShortener(sanitizer, false, 3, Collections.emptyList())
        def embedded  = new EmbeddedStacktraceShortener(sanitizer, 3, Collections.emptyList())
        def classifier = dummyClassifier()
        return new PuriflowJULInstaller(sanitizer, shortener, embedded, classifier, Mode.MASK)
    }

    def "wraps handlers on a logger with PurifyJULHandler"() {
        given:
        def installer = newInstaller()

        when:
        installer.install()

        then: "the logger has exactly one handler and it is our wrapper"
        def hs = logger.getHandlers()
        hs.length == 1
        hs[0] instanceof PurifyJULHandler
    }

    def "install() is idempotent - no double wrapping on repeated calls"() {
        given:
        def installer = newInstaller()

        when:
        installer.install()
        installer.install()

        then: "still exactly one PurifyJULHandler (no nested wrappers)"
        def hs = logger.getHandlers()
        hs.length == 1
        hs[0] instanceof PurifyJULHandler
    }
}