package demo

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.test.appender.ListAppender
import spock.lang.Specification

abstract class BaseLog2j2Spec extends Specification {
    protected ListAppender mem

    // ---------- helpers ----------

    // Read current Log4j2 configuration
    protected static Configuration cfg() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false)
        assert ctx != null : "Log4j2 LoggerContext is null"
        return ctx.getConfiguration()
    }

    // Obtain our MEM test appender (exists even after Puriflow wraps logger refs)
    protected static ListAppender memAppender() {
        def app = cfg().getAppenders().get("MEM")
        assert app instanceof ListAppender : "Appender 'MEM' not found or not a ListAppender. Check log4j2-test.xml and test dependency ':tests'."
        return (ListAppender) app
    }

    protected static List<String> allLines() {
        return memAppender().events.collect { LogEvent e -> e.message.formattedMessage }
    }

    protected static String joined() { allLines().join("\n") }
    protected static String lastLine() { def l = allLines(); l.isEmpty() ? "" : l.last() }

    private String url(String path) { "http://localhost:$port$path" }

    // ---------- lifecycle ----------

    def setup() {
        println "log4j2-test.xml = ${getClass().getResource('/log4j2-test.xml')}"
        mem = memAppender()
        mem.clear() // start from clean buffer
    }

    def cleanup() {
        mem?.clear()
    }
}
