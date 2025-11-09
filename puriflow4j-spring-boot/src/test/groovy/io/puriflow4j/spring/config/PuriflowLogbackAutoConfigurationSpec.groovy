package io.puriflow4j.spring.config

import ch.qos.logback.classic.LoggerContext
import io.puriflow4j.core.api.Detector
import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.DetectionResult
import io.puriflow4j.core.report.Reporter
import io.puriflow4j.logs.core.categorize.ExceptionClassifier
import io.puriflow4j.logs.core.categorize.HeuristicExceptionClassifier
import io.puriflow4j.logs.logback.PurifyLoggerContextListener
import io.puriflow4j.spring.PuriflowProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

class PuriflowLogbackAutoConfigurationSpec extends Specification {

    // --- tiny real Sanitizer so we don’t mock finals
    private static Sanitizer tinySanitizer() {
        Detector noop = new Detector() {
            @Override
            DetectionResult detect(String s) { DetectionResult.empty() }
        }
        new Sanitizer(List.of(noop), Action.MASK)
    }

    // --- no-op Reporter
    private static Reporter noopReporter() {
        ({ List ignored -> /* no-op */ } as Reporter)
    }

    // --- default props bean for the auto-config (binding not required in tests)
    private static PuriflowProperties defaultProps() {
        def p = new PuriflowProperties()
        p.getLogs().setEnabled(true)
        p.getLogs().getErrors().setShorten(true)
        p.getLogs().getErrors().setMaxDepth(5)
        return p
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PuriflowLogbackAutoConfiguration))
    // provide required collaborator beans explicitly
            .withBean(Sanitizer) { tinySanitizer() }
            .withBean(Reporter)   { noopReporter() }
            .withBean(PuriflowProperties) { defaultProps() }
    // properties for @ConditionalOnProperty in the auto-config
            .withPropertyValues(
                    "puriflow4j.logs.enabled=true",
                    "puriflow4j.mode=MASK",
                    "puriflow4j.logs.errors.shorten=true",
                    "puriflow4j.logs.errors.max-depth=5"
            )

    def cleanup() {
        // Reset Logback between tests to avoid listener leakage
        def ctx = (LoggerContext) LoggerFactory.getILoggerFactory()
        ctx.reset()
    }

    private static void removePurifyListeners(LoggerContext ctx) {
        def toRemove = ctx.copyOfListenerList.findAll { it.class.name == PurifyLoggerContextListener.name }
        toRemove.each { ctx.removeListener(it) }
    }

    def "registers PurifyLoggerContextListener and HeuristicExceptionClassifier when categorize=true"() {
        given:
        def local = runner.withPropertyValues("puriflow4j.logs.errors.categorize=true")

        expect:
        local.run { ctx ->
            assert ctx.isActive()

            // Heuristic classifier is selected
            def cls = ctx.getBean(ExceptionClassifier)
            assert cls instanceof HeuristicExceptionClassifier

            // Listener attached to Logback
            def lctx = (LoggerContext) LoggerFactory.getILoggerFactory()
            assert lctx.copyOfListenerList.stream()
                    .anyMatch { it.class.name == PurifyLoggerContextListener.name }
        }
    }

    def "registers PurifyLoggerContextListener and noop ExceptionClassifier by default"() {
        expect:
        runner.run { ctx ->
            assert ctx.isActive()

            // Default classifier path (categorize=false) -> noop
            def cls = ctx.getBean(ExceptionClassifier)
            assert !(cls instanceof HeuristicExceptionClassifier)

            // Listener present
            def lctx = (LoggerContext) LoggerFactory.getILoggerFactory()
            assert lctx.copyOfListenerList.stream()
                    .anyMatch { it.class.name == PurifyLoggerContextListener.name }
        }
    }

    def "listener is added only once (no double registration)"() {
        expect:
        runner.run { ctx ->
            assert ctx.isActive()
            def lctx = (LoggerContext) LoggerFactory.getILoggerFactory()

            def first = lctx.copyOfListenerList.findAll { it.class.name == PurifyLoggerContextListener.name }
            assert first.size() == 1
        }

        and: "fresh run also yields exactly one listener (auto-config guards by class name)"
        runner.run { ctx2 ->
            def lctx2 = (LoggerContext) LoggerFactory.getILoggerFactory()
            def second = lctx2.copyOfListenerList.findAll { it.class.name == PurifyLoggerContextListener.name }
            assert second.size() == 1
        }
    }

    def "does nothing when puriflow4j.logs.enabled=false"() {
        given:
        def preCtx = (LoggerContext) LoggerFactory.getILoggerFactory()
        removePurifyListeners(preCtx)
        preCtx.reset() // очистили состояние полностью

        // sanity-check: теперь точно нет нашего листенера
        assert preCtx.copyOfListenerList.stream()
                .noneMatch { it.class.name == PurifyLoggerContextListener.name }

        def off = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PuriflowLogbackAutoConfiguration))
                .withBean(Sanitizer) { tinySanitizer() }
                .withBean(Reporter)  { noopReporter() }
                .withBean(PuriflowProperties) {
                    def p = defaultProps()
                    p.getLogs().setEnabled(false)
                    p
                }
                .withPropertyValues("puriflow4j.logs.enabled=false")

        expect:
        off.run { ctx ->
            assert ctx.isActive()
            def lctx = (LoggerContext) LoggerFactory.getILoggerFactory()
            assert lctx.copyOfListenerList.stream()
                    .noneMatch { it.class.name == PurifyLoggerContextListener.name }
        }
    }
}
