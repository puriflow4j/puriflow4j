/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.spring.config.logs;

import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.logs.core.categorize.ExceptionClassifier;
import io.puriflow4j.logs.core.shorten.EmbeddedStacktraceShortener;
import io.puriflow4j.logs.core.shorten.ExceptionShortener;
import io.puriflow4j.logs.jul.PuriflowJULInstaller;
import io.puriflow4j.spring.PuriflowProperties;
import java.io.UnsupportedEncodingException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "puriflow4j.logs", name = "enabled", havingValue = "true")
@ConditionalOnMissingClass({
    "ch.qos.logback.classic.LoggerContext", // exclude Logback
    "org.apache.logging.log4j.core.LoggerContext" // exclude Log4j2 Core
})
public class PuriflowJULAutoConfiguration {

    @Bean
    @ConditionalOnBean(Sanitizer.class)
    public Object puriflowJulInit(
            PuriflowProperties props,
            @Qualifier("logSanitizer") Sanitizer sanitizer,
            @Qualifier("logExceptionClassifier") ExceptionClassifier classifier)
            throws UnsupportedEncodingException {
        var e = props.getLogs().getErrors();
        var shortener = new ExceptionShortener(sanitizer, e.isShorten(), e.getMaxDepth(), e.getHidePackages());
        var embedded = new EmbeddedStacktraceShortener(sanitizer, e.getMaxDepth(), e.getHidePackages());
        new PuriflowJULInstaller(
                        sanitizer,
                        shortener,
                        embedded,
                        classifier,
                        props.getLogs().getMode())
                .install();
        return new Object();
    }
}
