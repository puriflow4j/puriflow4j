/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.spring.config;

import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.report.Reporter;
import io.puriflow4j.logs.core.categorize.ExceptionClassifier;
import io.puriflow4j.logs.core.categorize.HeuristicExceptionClassifier;
import io.puriflow4j.logs.core.shorten.EmbeddedStacktraceShortener;
import io.puriflow4j.logs.core.shorten.ExceptionShortener;
import io.puriflow4j.logs.log4j2.PuriflowLog4j2Installer;
import io.puriflow4j.spring.PuriflowProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "puriflow4j.logs", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "org.apache.logging.log4j.core.LoggerContext")
public class PuriflowLog4j2AutoConfiguration {

    @Bean
    @ConditionalOnBean({Sanitizer.class, Reporter.class})
    public Object puriflowLog4j2Init(
            PuriflowProperties props, Sanitizer sanitizer, Reporter reporter, ExceptionClassifier classifier) {

        var ctx = (LoggerContext) LogManager.getContext(false);
        if (ctx == null) return new Object();

        var e = props.getLogs().getErrors();
        var shortener = new ExceptionShortener(sanitizer, e.isShorten(), e.getMaxDepth(), e.getHidePackages());
        var embeddedShortener = new EmbeddedStacktraceShortener(sanitizer, e.getMaxDepth(), e.getHidePackages());

        var installer = new PuriflowLog4j2Installer(
                reporter,
                sanitizer,
                shortener,
                embeddedShortener,
                classifier,
                props.getMode());

        installer.install(); // perform async+rewrite wrapping

        return new Object();
    }

    @Bean
    @ConditionalOnProperty(prefix = "puriflow4j.logs.errors", name = "categorize", havingValue = "true")
    public ExceptionClassifier exceptionClassifierEnabled() {
        return new HeuristicExceptionClassifier();
    }

    @Bean
    @ConditionalOnMissingBean(ExceptionClassifier.class)
    public ExceptionClassifier exceptionClassifierNoop() {
        return ExceptionClassifier.noop();
    }
}