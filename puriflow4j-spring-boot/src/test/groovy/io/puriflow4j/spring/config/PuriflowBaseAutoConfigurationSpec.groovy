package io.puriflow4j.spring.config

import io.puriflow4j.logs.core.categorize.ExceptionClassifier
import io.puriflow4j.logs.core.categorize.HeuristicExceptionClassifier
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

class PuriflowBaseAutoConfigurationSpec extends Specification {

    //  Load ONLY the base auto-config; no manual beans with same names.
    private ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PuriflowBaseAutoConfiguration))
            .withUserConfiguration(TestSupport)

    @Configuration
    static class TestSupport { }

    def "does not create beans when puriflow4j is disabled"() {
        expect:
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PuriflowBaseAutoConfiguration))
                .withUserConfiguration(TestSupport)
                .withPropertyValues("puriflow4j.enabled=false")
                .run { ctx ->
                    assert !ctx.containsBean("sanitizer")
                    assert !ctx.containsBean("exceptionClassifierEnabled")
                    assert !ctx.containsBean("exceptionClassifierNoop")
                }
    }

    def "creates Sanitizer when enabled (with defaults)"() {
        expect:
        runner.withPropertyValues("puriflow4j.enabled=true")
                .run { ctx ->
                    assert ctx.containsBean("sanitizer")
                }
    }

    def "creates heuristic ExceptionClassifier when categorize=true"() {
        expect:
        runner.withPropertyValues(
                "puriflow4j.enabled=true",
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
                "puriflow4j.enabled=true",
                "puriflow4j.logs.errors.categorize=false"
        )
                .run { ctx ->
                    def cls = ctx.getBean(ExceptionClassifier)
                    assert !(cls instanceof HeuristicExceptionClassifier)
                }
    }
}