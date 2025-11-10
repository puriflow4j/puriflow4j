package io.puriflow4j.logs.log4j2

import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.Mode
import io.puriflow4j.logs.core.categorize.ExceptionClassifier
import io.puriflow4j.logs.core.model.ThrowableView
import io.puriflow4j.logs.core.shorten.EmbeddedStacktraceShortener
import io.puriflow4j.logs.core.shorten.ExceptionShortener
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AsyncAppender
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.appender.rewrite.RewriteAppender
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration
import org.apache.logging.log4j.core.config.builder.impl.DefaultConfigurationBuilder
import spock.lang.Specification

/**
 * Structural tests for PuriflowLog4j2Installer:
 * - wraps sync appenders with RewriteAppender and retargets logger mappings
 * - preserves user async chains (AsyncAppender or Async Loggers)
 * - idempotent installation
 * - replaces mappings in named loggers too
 *
 * IMPORTANT: we assert using LoggerConfig.appenders (runtime mapping),
 * not appenderRefs (immutable builder-time list).
 */
class PuriflowLog4j2InstallerSpec extends Specification {

    // ---------- helpers ----------

    private static LoggerContext newCtxWithConfig(BuiltConfiguration conf) {
        def ctx = new LoggerContext("test-" + UUID.randomUUID())
        ctx.start(conf)
        return ctx
    }

    private static ConfigurationBuilder<BuiltConfiguration> newBuilder(String name = "test") {
        def b = new DefaultConfigurationBuilder<BuiltConfiguration>()
        b.setStatusLevel(Level.ERROR)
        b.setConfigurationName(name)
        return b
    }

    private static void addPatternLayout(AppenderComponentBuilder app, ConfigurationBuilder b) {
        app.add(b.newLayout("PatternLayout").addAttribute("pattern", "%msg%n"))
    }

    /** Runtime mapping on the ROOT logger (what Log4j actually uses for routing). */
    private static Set<String> rootAppenders(Configuration cfg) {
        return cfg.rootLogger.appenders.keySet() as Set<String>
    }

    /** Runtime mapping on a named logger. */
    private static Set<String> loggerAppenders(Configuration cfg, String name) {
        LoggerConfig lc = (LoggerConfig) cfg.loggers[name]
        return (lc?.appenders?.keySet() ?: []) as Set<String>
    }

    /** Build installer with a real Sanitizer and a no-op classifier. */
    private static PuriflowLog4j2Installer newInstaller() {
        def sanitizer = new Sanitizer(List.of(), Action.NONE)
        def shortener = new ExceptionShortener(sanitizer, /*shorten*/ false, /*maxDepth*/ 3, /*hidePkgs*/ List.of())
        def embedded  = new EmbeddedStacktraceShortener(sanitizer, 3, List.of())

        def classifier = new ExceptionClassifier() {
            @Override
            ExceptionClassifier.CategoryResult classify(ThrowableView view) {
                return ExceptionClassifier.CategoryResult.NONE
            }
        }

        return new PuriflowLog4j2Installer(sanitizer, shortener, embedded, classifier, Mode.MASK)
    }

    // ---------- tests ----------

    def "wraps sync appender with Rewrite and replaces references in root"() {
        given: "root -> CON (Console)"
        def b = newBuilder("sync")
        def con = b.newAppender("CON", "Console")
        addPatternLayout(con, b)
        b.add(con)

        def root = b.newRootLogger(Level.INFO)
        root.add(b.newAppenderRef("CON"))
        b.add(root)

        def ctx = newCtxWithConfig(b.build())
        def cfg = ctx.configuration

        and: "preconditions (runtime mapping contains CON)"
        assert cfg.appenders["CON"] instanceof ConsoleAppender
        assert rootAppenders(cfg) == ["CON"] as Set

        when: "install preserving async/sync"
        newInstaller().installPreservingAsync(ctx)

        then: "CON remains present; root now routes via PURIFY_WRAPPER_CON"
        cfg.appenders["CON"] instanceof ConsoleAppender
        cfg.appenders["PURIFY_WRAPPER_CON"] instanceof RewriteAppender
        rootAppenders(cfg) == ["PURIFY_WRAPPER_CON"] as Set

        and: "RewriteAppender targets the original CON"
        def rw = (RewriteAppender) cfg.appenders["PURIFY_WRAPPER_CON"]
        (rw.appenderRefs*.ref as Set) == ["CON"] as Set
        rw.rewritePolicy instanceof PuriflowRewritePolicy

        cleanup:
        ctx?.stop()
    }

    def "preserves user async: root -> ASY(-> CON) becomes root -> Rewrite(ASY)"() {
        given: "root -> ASY (Async) -> CON"
        def b = newBuilder("async")
        def con = b.newAppender("CON", "Console")
        addPatternLayout(con, b)
        b.add(con)

        def asy = b.newAppender("ASY", "Async")
        asy.addComponent(b.newAppenderRef("CON"))
        b.add(asy)

        def root = b.newRootLogger(Level.INFO)
        root.add(b.newAppenderRef("ASY"))
        b.add(root)

        def ctx = newCtxWithConfig(b.build())
        def cfg = ctx.configuration

        and: "preconditions"
        assert cfg.appenders["CON"] instanceof ConsoleAppender
        assert cfg.appenders["ASY"] instanceof AsyncAppender
        assert rootAppenders(cfg) == ["ASY"] as Set

        when:
        newInstaller().installPreservingAsync(ctx)

        then: "ASY remains; root now routes via PURIFY_WRAPPER_ASY (async preserved downstream)"
        cfg.appenders["ASY"] instanceof AsyncAppender
        cfg.appenders["PURIFY_WRAPPER_ASY"] instanceof RewriteAppender
        rootAppenders(cfg) == ["PURIFY_WRAPPER_ASY"] as Set

        and: "Rewrite targets ASY"
        def rw = (RewriteAppender) cfg.appenders["PURIFY_WRAPPER_ASY"]
        (rw.appenderRefs*.ref as Set) == ["ASY"] as Set
        rw.rewritePolicy instanceof PuriflowRewritePolicy

        cleanup:
        ctx?.stop()
    }

    def "idempotent: second install does not create extra wrappers"() {
        given:
        def b = newBuilder("idem")
        def con = b.newAppender("CON", "Console")
        addPatternLayout(con, b)
        b.add(con)

        def root = b.newRootLogger(Level.INFO)
        root.add(b.newAppenderRef("CON"))
        b.add(root)

        def ctx = newCtxWithConfig(b.build())
        def inst = newInstaller()

        when: "first install"
        inst.installPreservingAsync(ctx)
        def cfg1 = ctx.configuration
        def namesAfterFirst = cfg1.appenders.keySet() as Set
        def rootAfterFirst = rootAppenders(cfg1)

        and: "second install"
        inst.installPreservingAsync(ctx)
        def cfg2 = ctx.configuration
        def namesAfterSecond = cfg2.appenders.keySet() as Set
        def rootAfterSecond = rootAppenders(cfg2)

        then: "no duplicate wrappers; names stable"
        namesAfterSecond == namesAfterFirst
        namesAfterSecond.contains("PURIFY_WRAPPER_CON")
        !namesAfterSecond.any { it.startsWith("PURIFY_WRAPPER_PURIFY_WRAPPER_") }

        and: "root still points to PURIFY_WRAPPER_CON"
        rootAfterSecond == ["PURIFY_WRAPPER_CON"] as Set

        cleanup:
        ctx?.stop()
    }

    def "replaces references in named loggers as well"() {
        given:
        def b = newBuilder("named")
        def con = b.newAppender("C1", "Console")
        addPatternLayout(con, b)
        b.add(con)

        // root -> C1
        def root = b.newRootLogger(Level.INFO)
        root.add(b.newAppenderRef("C1"))
        b.add(root)

        // demo.Foo -> C1
        def foo = b.newLogger("demo.Foo", Level.INFO)
        foo.add(b.newAppenderRef("C1"))
        foo.addAttribute("additivity", false)
        b.add(foo)

        def ctx = newCtxWithConfig(b.build())
        def cfg = ctx.configuration

        and: "preconditions use runtime mappings"
        assert loggerAppenders(cfg, "demo.Foo") == ["C1"] as Set
        assert rootAppenders(cfg) == ["C1"] as Set

        when:
        newInstaller().installPreservingAsync(ctx)

        then: "both root and demo.Foo now map to PURIFY_WRAPPER_C1"
        rootAppenders(cfg) == ["PURIFY_WRAPPER_C1"] as Set
        loggerAppenders(cfg, "demo.Foo") == ["PURIFY_WRAPPER_C1"] as Set

        cleanup:
        ctx?.stop()
    }
}