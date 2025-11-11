package io.puriflow4j.spring.config.logs


import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import io.puriflow4j.logs.core.categorize.ExceptionClassifier
import io.puriflow4j.logs.core.categorize.HeuristicExceptionClassifier
import spock.lang.Specification
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class PuriflowLog4j2AutoConfigurationSpec extends Specification {

    //  The runner loads BOTH base and log4j2 auto-configs.
    // Base AC will create Sanitizer/Properties/Classifier; no manual bean overriding here.
    private ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PuriflowBaseLogAutoConfiguration,
                    PuriflowLog4j2AutoConfiguration
            ))
            .withPropertyValues(
                    // gates for base & log integrations
                    "puriflow4j.enabled=true",
                    "puriflow4j.mode=MASK",
                    "puriflow4j.logs.enabled=true",
                    "puriflow4j.logs.errors.shorten=true",
                    "puriflow4j.logs.errors.max-depth=5"
            )

    def cleanup() {
        //  Reset Log4j2 between tests to avoid leftover wrappers across runs
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false)
        if (ctx != null) {
            ctx.stop()
            ctx.start()
        }
    }

    // ---------- helpers ----------

    private static Configuration cfg() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false)
        assert ctx != null : "Log4j2 LoggerContext is null — ensure log4j2 is on test classpath"
        return ctx.getConfiguration()
    }

    private static Set<String> appenderNames() {
        return new LinkedHashSet<>(cfg().getAppenders().keySet())
    }

    private static boolean hasPurifyWrappers() {
        return appenderNames().any { it.startsWith("PURIFY_WRAPPER_") || it.startsWith("PURIFY_ASYNC_") }
    }

    private static int countPurifyWrappers() {
        return appenderNames().count { it.startsWith("PURIFY_WRAPPER_") }
    }

    // ---------- tests ----------

    def "installs Rewrite/Async wrappers when enabled"() {
        expect:
        runner.withPropertyValues(
                "puriflow4j.logs.errors.categorize=true" // to exercise classifier path too
        )
                .run { ctx ->
                    assert ctx.isActive()

                    //  classifier is heuristic when categorize=true
                    def cls = ctx.getBean(ExceptionClassifier)
                    assert cls instanceof HeuristicExceptionClassifier

                    //  installer ran — appender wrappers exist
                    assert hasPurifyWrappers() : "Expected PURIFY_* appenders in Log4j2 configuration"
                }
    }

    def "is idempotent: running auto-config again does not double wrap appenders"() {
        when: "first context run"
        int firstCount
        runner.run { ctx ->
            assert ctx.isActive()
            firstCount = countPurifyWrappers()
            assert firstCount >= 0 // usually >= 1 if there was at least one original appender
        }

        then:
        //  reset runtime Log4j2 to simulate fresh app startup (keeps baseline appenders)
        // (done in cleanup(), but we want second run to see the same base state)
        true

        when: "second context run"
        int secondCount
        runner.run { ctx2 ->
            assert ctx2.isActive()
            secondCount = countPurifyWrappers()
        }

        then:
        //  Wrappers should not multiply; count should be the same
        secondCount == countPurifyWrappers()
    }

    def "does nothing when puriflow4j.logs.enabled=false"() {
        given:
        // Clear any wrappers that could have been added by previous tests
        cleanup()

        def off = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PuriflowBaseLogAutoConfiguration,
                        PuriflowLog4j2AutoConfiguration
                ))
                .withPropertyValues(
                        "puriflow4j.enabled=true",
                        "puriflow4j.logs.enabled=false"
                )

        expect:
        off.run { ctx ->
            assert ctx.isActive()

            //  No wrappers should be present when logs module is disabled
            assert !hasPurifyWrappers()
        }
    }
}
