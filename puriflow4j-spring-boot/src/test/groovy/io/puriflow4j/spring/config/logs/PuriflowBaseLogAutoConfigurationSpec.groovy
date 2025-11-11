package io.puriflow4j.spring.config.logs

import io.puriflow4j.logs.core.categorize.ExceptionClassifier
import io.puriflow4j.logs.core.categorize.HeuristicExceptionClassifier
import io.puriflow4j.spring.config.logs.PuriflowBaseLogAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

class PuriflowBaseLogAutoConfigurationSpec extends Specification {

    //  Load ONLY the base auto-config; no manual beans with same names.
    private ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PuriflowBaseLogAutoConfiguration))
            .withUserConfiguration(TestSupport)

    @Configuration
    static class TestSupport { }

    def "does not create beans when puriflow4j.logs is disabled"() {
        expect:
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PuriflowBaseLogAutoConfiguration))
                .withUserConfiguration(TestSupport)
                .withPropertyValues("puriflow4j.logs.enabled=false")
                .run { ctx ->
                    assert !ctx.containsBean("logSanitizer")
                    assert !ctx.containsBean("logExceptionClassifier")
                }
    }

    def "creates Sanitizer when puriflow4j.logs enabled (with defaults)"() {
        expect:
        runner.withPropertyValues("puriflow4j.logs.enabled=true")
                .run { ctx ->
                    assert ctx.containsBean("logSanitizer")
                }
    }

    def "creates heuristic ExceptionClassifier when categorize=true"() {
        expect:
        runner.withPropertyValues(
                "puriflow4j.logs.enabled=true",
                "puriflow4j.logs.errors.categorize=true"
        )
                .run { ctx ->
                    def cls = ctx.getBean(ExceptionClassifier)
                    assert cls instanceof HeuristicExceptionClassifier
                }
    }

    def "creates noop ExceptionClassifier by default (categorize=false)"() {
        expect:
        runner.withPropertyValues(
                "puriflow4j.logs.enabled=true",
                "puriflow4j.logs.errors.categorize=false"
        )
                .run { ctx ->
                    def cls = ctx.getBean(ExceptionClassifier)
                    assert !(cls instanceof HeuristicExceptionClassifier)
                }
    }
}