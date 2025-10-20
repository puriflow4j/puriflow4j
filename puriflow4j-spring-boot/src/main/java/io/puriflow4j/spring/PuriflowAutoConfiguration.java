/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.spring;

import io.micrometer.core.instrument.MeterRegistry;
import io.puriflow4j.core.api.Modes;
import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.api.models.Action;
import io.puriflow4j.core.preset.DetectorRegistry;
import io.puriflow4j.core.preset.KVPatternConfig;
import io.puriflow4j.core.report.Reporter;
import io.puriflow4j.logs.logback.LogbackIntegration;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-config that:
 *  - builds Sanitizer from YAML
 *  - detects Logback backend and attaches a reset-resistant listener
 *  - does not require any changes in user's logback.xml
 *  - does not pull any logging backend transitively (compileOnly deps)
 */
@Slf4j
@Configuration
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

        // 1) Build detector set from YAML (or defaults)
        var registry = new DetectorRegistry();
        var types = props.getDetectors().isEmpty()
                ? new ArrayList<>(DetectorRegistry.defaultTypes())
                : new ArrayList<>(props.getDetectors());

        var kvCfg = KVPatternConfig.of(
                props.getLogs().getKeyAllowlist(), props.getLogs().getKeyBlocklist());
        var detectors = registry.build(types, kvCfg);

        // 2) Mode → Action + Sanitizer
        Action action = Modes.actionFor(props.getMode());
        var sanitizer = new Sanitizer(detectors, action);

        // 3) Detect backend: Logback → attach listener; otherwise safe no-op

        boolean isLogback = LogbackIntegration.activateIfLogback(
                reporter,
                sanitizer,
                props.getLogs().getOnlyLoggers(),
                props.getLogs().getIgnoreLoggers());
        if (isLogback) {
            return new Object();
        }

        log.warn(
                "Puriflow4j: logging backend is unknown, masking is not activated. Bring puriflow4j adapter for your backend.");

        return new Object();
    }
}
