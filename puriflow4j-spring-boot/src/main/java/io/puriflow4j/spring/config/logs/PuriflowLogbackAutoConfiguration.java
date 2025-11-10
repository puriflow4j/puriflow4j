/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.spring.config.logs;

import ch.qos.logback.classic.LoggerContext;
import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.logs.core.categorize.ExceptionClassifier;
import io.puriflow4j.logs.core.shorten.EmbeddedStacktraceShortener;
import io.puriflow4j.logs.core.shorten.ExceptionShortener;
import io.puriflow4j.logs.logback.PurifyLoggerContextListener;
import io.puriflow4j.spring.PuriflowProperties;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "puriflow4j.logs", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
public class PuriflowLogbackAutoConfiguration {

    @Bean
    @ConditionalOnBean({Sanitizer.class})
    public Object puriflowLogbackInit(PuriflowProperties props, Sanitizer sanitizer, ExceptionClassifier classifier) {
        var lf = LoggerFactory.getILoggerFactory();
        if (!(lf instanceof LoggerContext ctx)) return new Object();

        var e = props.getLogs().getErrors();
        var shortener = new ExceptionShortener(sanitizer, e.isShorten(), e.getMaxDepth(), e.getHidePackages());

        var embeddedShortener = new EmbeddedStacktraceShortener(sanitizer, e.getMaxDepth(), e.getHidePackages());

        var listener = new PurifyLoggerContextListener(
                sanitizer,
                shortener,
                embeddedShortener,
                classifier,
                props.getMode(),
                props.getLogs().getOnlyLoggers(),
                props.getLogs().getIgnoreLoggers());

        boolean already = ctx.getCopyOfListenerList().stream()
                .anyMatch(l -> l.getClass().getName().equals(PurifyLoggerContextListener.class.getName()));
        if (!already) {
            ctx.addListener(listener);
            listener.onStart(ctx); // initial wrapping now
        }
        return new Object();
    }
}
