package io.puriflow4j.spring;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.puriflow4j.logs.PurifyAppender;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Iterator;

/**
 * Automatically wraps all appenders with a PurifyAppender,
 * to mask the formattedMessage regardless of logback-spring.xml.
 */
@AutoConfiguration
@EnableConfigurationProperties(PuriflowProperties.class)
public class PuriflowAutoConfiguration {

    @Bean
    public Object puriflowLogSanitizerInit(PuriflowProperties props) {
        if (!props.isEnabled()) return new Object();

        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext ctx)) return new Object();

        if (props.isRootOnly()) {
            wrapAppenders(ctx.getLogger(Logger.ROOT_LOGGER_NAME));
        } else {
            // wrap all known loggers
            for (Logger l : ctx.getLoggerList()) wrapAppenders(l);
        }
        return new Object();
    }

    private void wrapAppenders(Logger logger) {
        Iterator<Appender<ILoggingEvent>> e = logger.iteratorForAppenders();
        new java.util.ArrayList<Appender<ILoggingEvent>>() {{
            while (e.hasNext()) add(e.next());
        }}.forEach(app -> {
            if (PurifyAppender.isPurify(app)) return; // already wrapped
            // We remove the old one and put on our wrapper
            logger.detachAppender(app);
            PurifyAppender wrapper = new PurifyAppender(app);
            wrapper.setName("PURIFY_WRAPPER_" + app.getName());
            if (logger.getLoggerContext() != null) wrapper.setContext(logger.getLoggerContext());
            wrapper.start();
            logger.addAppender(wrapper);
        });
    }
}
