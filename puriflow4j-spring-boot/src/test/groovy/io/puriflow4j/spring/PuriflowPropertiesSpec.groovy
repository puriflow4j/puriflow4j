package io.puriflow4j.spring

import io.puriflow4j.core.api.model.DetectorType
import io.puriflow4j.core.api.model.Mode
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration


class PuriflowPropertiesSpec extends Specification {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
    // Brings in the binder infrastructure
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration))
    // Registers PuriflowProperties as a @ConfigurationProperties bean
            .withUserConfiguration(TestConfig)

    @EnableConfigurationProperties(PuriflowProperties)
    static class TestConfig { }

    def "binds properties from application.yaml correctly"() {
        given:
        def propertyValues = [
                "puriflow4j.logs.enabled=true",
                "puriflow4j.logs.mode=MASK",
                "puriflow4j.logs.detectors[0]=EMAIL",
                "puriflow4j.logs.only-loggers[0]=com.example",
                "puriflow4j.logs.key-allowlist[0]=user",
                "puriflow4j.logs.errors.shorten=true",
                "puriflow4j.logs.errors.max-depth=5",
                "puriflow4j.logs.errors.hide-packages[0]=com.acme"
        ]

        expect:
        runner.withPropertyValues(*propertyValues).run { ctx ->
            def props = ctx.getBean(PuriflowProperties)

            assert props.logs.enabled
            assert props.logs.mode == Mode.MASK
            assert props.logs.detectors == [DetectorType.EMAIL]
            assert props.logs.onlyLoggers == ["com.example"]
            assert props.logs.keyAllowlist == ["user"]

            assert props.logs.errors.shorten
            assert props.logs.errors.maxDepth == 5
            assert props.logs.errors.hidePackages == ["com.acme"]
        }
    }

    def "defaults are applied when no custom properties provided"() {
        expect:
        runner.run { ctx ->
            def props = ctx.getBean(PuriflowProperties)
            assert !props.logs.enabled
            assert props.logs.mode == Mode.DRY_RUN
            assert props.logs.detectors.isEmpty()
            assert props.logs.onlyLoggers.isEmpty()
            assert props.logs.errors.maxDepth == null
            assert props.logs.errors.hidePackages == []
        }
    }

    def "returned list properties are unmodifiable"() {
        when: "trying to modify detectors"
        runner.run { ctx ->
            def props = ctx.getBean(PuriflowProperties)
            props.getLogs().getDetectors().add(DetectorType.EMAIL) // should throw UnsupportedOperationException
        }

        then:
        thrown(UnsupportedOperationException)

        when: "trying to modify logs.errors.hidePackages"
        runner.run { ctx ->
            def props = ctx.getBean(PuriflowProperties)
            props.getLogs().getErrors().getHidePackages().add("foo") // should also throw
        }

        then:
        thrown(UnsupportedOperationException)
    }
}
