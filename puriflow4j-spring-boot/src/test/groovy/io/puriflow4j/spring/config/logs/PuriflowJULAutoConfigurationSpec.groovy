package io.puriflow4j.spring.config.logs

import io.puriflow4j.spring.config.PuriflowBaseAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

/**
 * Minimal tests for PuriflowJULAutoConfiguration.
 *
 * What we verify:
 *  1) When puriflow logs are enabled AND Logback/Log4j2 are absent (simulated via FilteredClassLoader),
 *     the bean 'puriflowJulInit' is created by auto-configuration.
 *  2) When puriflow logs are disabled, the bean is not created.
 *  3) When no Sanitizer bean exists (i.e., base auto-config is not applied), the bean is not created
 *     due to @ConditionalOnBean(Sanitizer.class).
 *
 * We intentionally avoid asserting any runtime masking behavior here.
 */
class PuriflowJULAutoConfigurationSpec extends Specification {

    /**
     * Happy path: base auto-config provides Sanitizer & ExceptionClassifier,
     * Logback/Log4j2 are hidden, logs are enabled => the JUL bean is created.
     */
    def "creates puriflowJulInit when logs enabled and Logback/Log4j2 are missing"() {
        given:
        def runner = new ApplicationContextRunner()
        // Hide competing logging systems to satisfy @ConditionalOnMissingClass
                .withClassLoader(new FilteredClassLoader(
                        "ch.qos.logback.classic",
                        "org.apache.logging.log4j.core"
                ))
        // Use Java Util Logging in Spring Boot explicitly (optional but deterministic)
                .withPropertyValues(
                        "logging.system=org.springframework.boot.logging.java.JavaLoggingSystem",
                        "puriflow4j.enabled=true",
                        "puriflow4j.logs.enabled=true"
                )
        // Base auto-config supplies Sanitizer/ExceptionClassifier/Properties
                .withConfiguration(AutoConfigurations.of(
                        PuriflowBaseAutoConfiguration,
                        PuriflowJULAutoConfiguration
                ))

        expect:
        runner.run { ctx ->
            assert ctx.containsBean("puriflowJulInit")
        }
    }

    /**
     * Negative: property disabled => bean must not be present.
     */
    def "does not create puriflowJulInit when logs disabled"() {
        given:
        def runner = new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(
                        "ch.qos.logback.classic",
                        "org.apache.logging.log4j.core"
                ))
                .withPropertyValues(
                        "logging.system=org.springframework.boot.logging.java.JavaLoggingSystem",
                        "puriflow4j.enabled=true",
                        "puriflow4j.logs.enabled=false"
                )
                .withConfiguration(AutoConfigurations.of(
                        PuriflowBaseAutoConfiguration,
                        PuriflowJULAutoConfiguration
                ))

        expect:
        runner.run { ctx ->
            assert !ctx.containsBean("puriflowJulInit")
        }
    }

    /**
     * Negative: no Sanitizer bean in context (we do not include the base auto-config) =>
     * @ConditionalOnBean(Sanitizer) prevents bean creation.
     */
    def "does not create puriflowJulInit when no Sanitizer bean is present"() {
        given:
        def runner = new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(
                        "ch.qos.logback.classic",
                        "org.apache.logging.log4j.core"
                ))
                .withPropertyValues(
                        "logging.system=org.springframework.boot.logging.java.JavaLoggingSystem",
                        "puriflow4j.enabled=true",
                        "puriflow4j.logs.enabled=true"
                )
        // Intentionally only JUL auto-config; NO base auto-config -> no Sanitizer bean
                .withConfiguration(AutoConfigurations.of(
                        PuriflowJULAutoConfiguration
                ))

        expect:
        runner.run { ctx ->
            assert !ctx.containsBean("puriflowJulInit")
        }
    }
}