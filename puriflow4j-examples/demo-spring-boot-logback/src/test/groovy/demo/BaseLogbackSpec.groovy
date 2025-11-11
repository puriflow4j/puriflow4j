package demo

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import spock.lang.Specification

/**
 * Shared helpers for Logback integration specs.
 * Keeps only the minimal utilities used by all specs.
 */
abstract class BaseLogbackSpec extends Specification {

    protected ListAppender<ILoggingEvent> appender

    // ---------- helpers ----------

    protected static Logger rootLogger() {
        (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
    }

    /**
     * Find a ListAppender holding final, masked messages.
     * We support:
     *  - direct "MEM" ListAppender attached to ROOT
     *  - Puriflow wrapper "PURIFY_WRAPPER_MEM" that delegates to a ListAppender
     */
    @SuppressWarnings("GroovyAssignabilityCheck")
    protected static ListAppender<ILoggingEvent> findMemListAppender() {
        def root = rootLogger()

        // 1) direct "MEM"?
        Appender<?> mem = root.getAppender("MEM")
        if (mem instanceof ListAppender) return (ListAppender<ILoggingEvent>) mem

        // 2) wrapped "PURIFY_WRAPPER_MEM"?
        Appender<?> wrapped = root.getAppender("PURIFY_WRAPPER_MEM")
        if (wrapped != null) {
            // Try Groovy property first
            def delegate = null
            try {
                delegate = wrapped.hasProperty("delegate") ? wrapped.getProperty("delegate") : null
            } catch (ignored) { /* fall through */ }

            // Try a public getter if available
            if (delegate == null && wrapped.metaClass.respondsTo(wrapped, "getDelegate")) {
                delegate = wrapped.getDelegate()
            }

            // Try private field via reflection
            if (!(delegate instanceof ListAppender)) {
                try {
                    def f = wrapped.getClass().getDeclaredField("delegate")
                    f.accessible = true
                    delegate = f.get(wrapped)
                } catch (Throwable ignored) { /* fall through */ }
            }

            assert delegate instanceof ListAppender : "PURIFY_WRAPPER_MEM exists but delegate is not a ListAppender"
            return (ListAppender<ILoggingEvent>) delegate
        }

        throw new IllegalStateException(
                "MEM appender not found. Ensure logback-test.xml has <appender name=\"MEM\" .../> and it is attached to ROOT.\n" +
                        "If Puriflow wraps it, wrapper name must be PURIFY_WRAPPER_MEM.")
    }

    // ---------- lifecycle ----------

    def setup() {
        // Force resolution of our XML (handy debug line)
        println "logback-test.xml = ${getClass().getResource('/logback-test.xml')}"
        appender = findMemListAppender()
        appender.list.clear()
    }

    def cleanup() {
        appender?.list?.clear()
    }
}