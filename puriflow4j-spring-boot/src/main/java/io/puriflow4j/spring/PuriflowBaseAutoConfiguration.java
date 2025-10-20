/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.spring;

import io.micrometer.core.instrument.MeterRegistry;
import io.puriflow4j.core.api.*;
import io.puriflow4j.core.preset.DetectorRegistry;
import io.puriflow4j.core.preset.KVPatternConfig;
import io.puriflow4j.core.report.Reporter;
import java.util.ArrayList;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds Sanitizer from YAML and provides a Reporter bean (Micrometer or no-op).
 * This config is backend-agnostic and does not import any logging framework APIs.
 */
@Configuration
@EnableConfigurationProperties(PuriflowProperties.class)
public class PuriflowBaseAutoConfiguration {

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnMissingBean
    public MicrometerReporter puriflowMicrometerReporter(MeterRegistry registry) {
        return new MicrometerReporter(registry, 200);
    }

    @Bean
    @ConditionalOnMissingBean(Reporter.class)
    public Reporter reporter(org.springframework.beans.factory.ObjectProvider<MicrometerReporter> mic) {
        Reporter r = mic.getIfAvailable();
        return (r != null) ? r : findings -> {}; // safe no-op fallback
    }

    @Bean
    @ConditionalOnProperty(prefix = "puriflow4j", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Sanitizer sanitizer(PuriflowProperties props) {
        var registry = new DetectorRegistry();
        var types = props.getDetectors().isEmpty()
                ? new ArrayList<>(DetectorRegistry.defaultTypes())
                : new ArrayList<>(props.getDetectors());

        var kvCfg = KVPatternConfig.of(
                props.getLogs().getKeyAllowlist(), props.getLogs().getKeyBlocklist());
        var detectors = registry.build(types, kvCfg);
        return new Sanitizer(detectors, Modes.actionFor(props.getMode()));
    }
}
