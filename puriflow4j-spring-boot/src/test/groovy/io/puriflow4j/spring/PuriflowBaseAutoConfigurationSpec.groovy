package io.puriflow4j.spring

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.report.Reporter
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

class PuriflowBaseAutoConfigurationSpec extends Specification {

    // Reusable runner preloaded with our auto-config
    private ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PuriflowBaseAutoConfiguration))
            .withPropertyValues(
                    "puriflow4j.enabled=true" // gate for the auto-config
            )
            .withUserConfiguration(TestMeters)

    @Configuration
    static class TestMeters {
        @Bean MeterRegistry meterRegistry() {
            // Real Micrometer registry to satisfy @ConditionalOnClass & injection
            return new SimpleMeterRegistry()
        }
    }

    def "creates MicrometerReporter, Reporter (same instance) and Sanitizer when enabled and MeterRegistry present"() {
        expect:
        runner.run { ctx ->
            // MicrometerReporter bean exists (by name is fine here)
            assert ctx.containsBean("micrometerReporter")
            assert ctx.getBean("micrometerReporter") instanceof MicrometerReporter

            // Reporter exists (by type) and is the same instance as MicrometerReporter
            def reporterBeans = ctx.getBeansOfType(Reporter)
            assert reporterBeans.size() == 1
            def reporter = reporterBeans.values().first()
            def micrometer = ctx.getBean(MicrometerReporter)
            assert reporter.is(micrometer)

            // Sanitizer exists
            assert ctx.getBean(Sanitizer) != null
        }
    }

    def "respects user-provided MicrometerReporter (not overridden)"() {
        given:
        def custom = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PuriflowBaseAutoConfiguration))
                .withPropertyValues("puriflow4j.enabled=true")
                .withUserConfiguration(TestMeters, UserMicrometer)

        expect:
        custom.run { ctx ->
            def fromCtx = ctx.getBean(MicrometerReporter)
            def user = ctx.getBean("userMicrometer", MicrometerReporter)
            assert fromCtx.is(user)       // auto-config did not create its own
            assert ctx.getBean(Reporter).is(user) // Reporter wired to the same instance
        }
    }

    @Configuration
    static class UserMicrometer {
        @Bean("userMicrometer")
        MicrometerReporter userMicrometer(MeterRegistry registry) {
            // user-supplied bean should win due to @ConditionalOnMissingBean
            return new MicrometerReporter(registry, 123)
        }
    }

    def "does not create beans when puriflow4j is disabled"() {
        expect:
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PuriflowBaseAutoConfiguration))
                .withPropertyValues("puriflow4j.enabled=false")
                .withUserConfiguration(TestMeters)
                .run { ctx ->
                    assert !ctx.containsBean("micrometerReporter")
                    assert !ctx.containsBean("reporter")
                    assert !ctx.containsBean("sanitizer")
                    assert !ctx.containsBean("puriflowEndpoint")
                }
    }

    def "creates PuriflowEndpoint when actuator endpoint is available and MicrometerReporter exists"() {
        given:
        // Expose web endpoints so @ConditionalOnAvailableEndpoint is satisfied
        def withActuator = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PuriflowBaseAutoConfiguration))
                .withPropertyValues(
                        "puriflow4j.enabled=true",
                        "management.endpoints.web.exposure.include=*" // make endpoint available
                )
                .withUserConfiguration(TestMeters)

        expect:
        withActuator.run { ctx ->
            assert ctx.containsBean("puriflowEndpoint")
            assert ctx.getBean(PuriflowEndpoint) != null
        }
    }
}
