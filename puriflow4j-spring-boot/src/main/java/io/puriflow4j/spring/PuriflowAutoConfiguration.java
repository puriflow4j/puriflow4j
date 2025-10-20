/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.spring;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.micrometer.core.instrument.MeterRegistry;
import io.puriflow4j.core.api.*;
import io.puriflow4j.core.api.models.*;
import io.puriflow4j.core.detect.Detector;
import io.puriflow4j.core.preset.DetectorRegistry;
import io.puriflow4j.core.preset.KVPatternConfig;
import io.puriflow4j.core.report.Reporter;
import io.puriflow4j.logs.PurifyAppender;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Auto-configures log sanitization from YAML. */
@AutoConfiguration
@EnableConfigurationProperties(PuriflowProperties.class)
public class PuriflowAutoConfiguration {

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnMissingBean
    public MicrometerReporter puriflowMicrometerReporter(MeterRegistry registry) {
        return new MicrometerReporter(registry, 200);
    }

    @Bean
    @ConditionalOnMissingBean(Reporter.class)
    @ConditionalOnBean(MicrometerReporter.class)
    public Reporter reporter(MicrometerReporter micrometerReporter) {
        return micrometerReporter;
    }

    @Bean
    @ConditionalOnProperty(prefix = "puriflow4j", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Object puriflowInit(PuriflowProperties props, Reporter reporter) {
        if (!props.getLogs().isEnabled()) return new Object();

        // build detectors from enum list (or defaults)
        var registry = new DetectorRegistry();
        var types = (props.getDetectors() == null || props.getDetectors().isEmpty())
                ? new ArrayList<>(DetectorRegistry.defaultTypes())
                : new ArrayList<>(props.getDetectors());

        var kvCfg = KVPatternConfig.of(
                props.getLogs().getKeyAllowlist(), props.getLogs().getKeyBlocklist());
        var detectors = registry.build(types, kvCfg);

        // mode → action
        Action action = Modes.actionFor(props.getMode());

        // wrap root logger appenders (with only/ignore filters)
        ILoggerFactory lf = LoggerFactory.getILoggerFactory();
        if (lf instanceof LoggerContext ctx) {
            wrapLogger(ctx.getLogger(Logger.ROOT_LOGGER_NAME), detectors, action, reporter, props);
        }
        return new Object();
    }

    private void wrapLogger(
            Logger logger, List<Detector> detectors, Action action, Reporter reporter, PuriflowProperties props) {

        var only = props.getLogs().getOnlyLoggers().stream()
                .map(s -> s == null ? "" : s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        var ignore = props.getLogs().getIgnoreLoggers().stream()
                .map(s -> s == null ? "" : s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        // snapshot current appenders
        var toWrap = new ArrayList<Appender<ILoggingEvent>>();
        for (Iterator<Appender<ILoggingEvent>> e = logger.iteratorForAppenders(); e.hasNext(); ) {
            toWrap.add(e.next());
        }

        for (Appender<ILoggingEvent> app : toWrap) {
            if (PurifyAppender.isPurify(app)) continue;

            String lname = logger.getName() == null ? "" : logger.getName().toLowerCase(Locale.ROOT);
            if (!only.isEmpty() && !only.contains(lname)) continue;
            if (!ignore.isEmpty() && ignore.contains(lname)) continue;

            logger.detachAppender(app);

            var sanitizer = new Sanitizer(detectors, action);
            var wrapper = new PurifyAppender(app, reporter);
            wrapper.setContext(logger.getLoggerContext());
            wrapper.setName("PURIFY_WRAPPER_" + app.getName());
            wrapper.setSanitizer(sanitizer);
            wrapper.start();

            logger.addAppender(wrapper);
        }
    }
}
