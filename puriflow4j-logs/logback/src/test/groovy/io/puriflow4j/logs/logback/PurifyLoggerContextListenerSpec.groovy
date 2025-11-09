package io.puriflow4j.logs.logback

import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.Appender
import io.puriflow4j.core.api.Detector
import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.DetectionResult
import io.puriflow4j.core.api.model.Mode
import io.puriflow4j.logs.core.categorize.ExceptionClassifier
import io.puriflow4j.logs.core.shorten.EmbeddedStacktraceShortener
import io.puriflow4j.logs.core.shorten.ExceptionShortener
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.util.regex.Pattern

class PurifyLoggerContextListenerSpec extends Specification {

    // -------- helpers (instance, no statics)

    private Sanitizer mkSanitizer() {
        Detector det = new Detector() {
            private final Pattern P = Pattern.compile("(?i)\\b(secret)\\s*[:=]\\s*([^\\s,;]+)")
            @Override
            DetectionResult detect(String s) {
                if (s == null || s.isEmpty()) return DetectionResult.empty()
                def m = P.matcher(s)
                def spans = []
                while (m.find()) spans.add(new DetectionResult.Span(m.start(2), m.end(2), "kv", "[MASKED]"))
                return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans))
            }
        }
        new Sanitizer(List.of(det), Action.MASK)
    }

    private ExceptionShortener mkShortener(Sanitizer san) {
        new ExceptionShortener(san, /*shorten*/ true, 5, List.of("java.", "org.slf4j"))
    }

    private EmbeddedStacktraceShortener mkEmbedded(Sanitizer san) {
        // твой продакшен-ctor ожидает (Sanitizer, int, List<String>)
        new EmbeddedStacktraceShortener(san, 5, List.of("java.", "org.slf4j"))
    }

    private ExceptionClassifier mkClassifier() {
        Mock(ExceptionClassifier) { 0 * _ }
    }

    private Appender<ILoggingEvent> namedAppender(String name) {
        Appender<ILoggingEvent> app = Mock(Appender)
        _ * app.getName() >> name
        _ * app.start() >> { }
        _ * app.stop() >> { }
        app
    }

    private AsyncAppender asyncWithChild(LoggerContext ctx, String asyncName, Appender<ILoggingEvent> child) {
        def async = new AsyncAppender()
        async.setContext(ctx) // чтобы не ругался на отсутствие контекста
        async.setName(asyncName)
        async.addAppender(child)
        async.start()         // запускаем, чтобы он принимал события
        async
    }

    private static List<Appender<ILoggingEvent>> appendersOf(Logger logger) {
        def list = new ArrayList<Appender<ILoggingEvent>>()
        def it = logger.iteratorForAppenders()
        while (it.hasNext()) list.add(it.next())
        list
    }

    private static LoggingEvent evt(String logger, String msg) {
        def e = new LoggingEvent()
        e.loggerName = logger
        e.message = msg
        e
    }

    // -------- tests

//    def "wrapAll: wraps direct appenders and async children once, idempotent on reset()"() {
//        given: "fresh context with root + named logger"
//        def ctx = new LoggerContext()
//        def root = ctx.getLogger(Logger.ROOT_LOGGER_NAME)
//        def appA = namedAppender("A")
//        root.addAppender(appA)
//
//        def named = ctx.getLogger("service.HTTP")
//        def child = namedAppender("child")
//        def async = asyncWithChild(ctx, "ASYNC", child)
//        named.addAppender(async)
//
//        and: "listener for all loggers"
//        def san = mkSanitizer()
//        def listener = new PurifyLoggerContextListener(
//                mkReporter(),
//                san,
//                mkShortener(san),
//                mkEmbedded(san),
//                mkClassifier(),
//                Mode.MASK,
//                /*only*/ List.of(),
//                /*ignore*/ List.of()
//        )
//
//        when: "start wraps"
//        listener.onStart(ctx)
//
//        then: "root's A replaced by Purify wrapper (visible immediately)"
//        def rootApps = appendersOf(root)
//        rootApps.size() == 1
//        PurifyAppender.isPurify(rootApps[0])
//        rootApps[0].name == "PURIFY_WRAPPER_A"
//
//        and: "async child effectively wrapped — it receives a SANITIZED event"
//        1 * child.doAppend({ ILoggingEvent ev -> ev.getFormattedMessage() == "secret=[MASKED]" })
//
//        when: "send an event through ASYNC after wrap"
//        async.doAppend(evt("service.HTTP", "secret=raw"))
//
//        then:
//        true  // нет доп. ожиданий в этом блоке
//
//        when: "reset again (idempotent) + another event"
//        listener.onReset(ctx)
//        async.doAppend(evt("service.HTTP", "secret=again"))
//
//        then: "still single call per event, and sanitized again"
//        1 * child.doAppend({ ILoggingEvent ev -> ev.getFormattedMessage() == "secret=[MASKED]" })
//    }

    def "only/ignore lists are honored (lowercased matching)"() {
        given:
        def ctx = new LoggerContext()
        def root = ctx.getLogger(Logger.ROOT_LOGGER_NAME)
        def lA = ctx.getLogger("service.DB")
        def lB = ctx.getLogger("service.http")

        root.addAppender(namedAppender("R"))
        lA.addAppender(namedAppender("A"))
        lB.addAppender(namedAppender("B"))

        and:
        def san = mkSanitizer()
        def listener = new PurifyLoggerContextListener(
                san,
                mkShortener(san),
                mkEmbedded(san),
                mkClassifier(),
                Mode.MASK,
                List.of("service.db"),   // only
                List.of("service.http")  // ignore
        )

        when:
        listener.onStart(ctx)

        then: "root untouched (не в only)"
        def rootApps = appendersOf(root)
        rootApps.size() == 1
        !PurifyAppender.isPurify(rootApps[0])

        and: "service.DB wrapped"
        def aApps = appendersOf(lA)
        aApps.size() == 1
        PurifyAppender.isPurify(aApps[0])
        aApps[0].name == "PURIFY_WRAPPER_A"

        and: "service.http ignored"
        def bApps = appendersOf(lB)
        bApps.size() == 1
        !PurifyAppender.isPurify(bApps[0])
    }

    def "existing PurifyAppender is not re-wrapped"() {
        given:
        def ctx = new LoggerContext()
        def logger = ctx.getLogger("demo")
        def base = namedAppender("BASE")

        def san = mkSanitizer()
        def wrapper = new PurifyAppender(base, san, mkShortener(san), mkEmbedded(san), mkClassifier(), Mode.MASK)
        wrapper.setContext(ctx)
        wrapper.setName("PURIFY_WRAPPER_BASE")
        wrapper.start()
        logger.addAppender(wrapper)

        and:
        def listener = new PurifyLoggerContextListener(
                san,
                mkShortener(san),
                mkEmbedded(san),
                mkClassifier(),
                Mode.MASK,
                List.of(), List.of()
        )

        when:
        listener.onStart(ctx)

        then: "still one wrapper"
        def apps = appendersOf(logger)
        apps.size() == 1
        PurifyAppender.isPurify(apps[0])
        apps[0].name == "PURIFY_WRAPPER_BASE"
    }

    def cleanup() {
        // reset global root context after each test
        def logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
        logger.setLevel(Level.INFO)
        logger.getLoggerContext().reset()
    }
}
