/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.spring.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.puriflow4j.core.api.Sanitizer;
import io.puriflow4j.core.api.model.Action;
import io.puriflow4j.core.preset.DetectorRegistry;
import io.puriflow4j.core.preset.KVPatternConfig;
import io.puriflow4j.core.report.Reporter;
import io.puriflow4j.spring.MicrometerReporter;
import io.puriflow4j.spring.PuriflowEndpoint;
import io.puriflow4j.spring.PuriflowProperties;
import java.util.ArrayList;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(PuriflowProperties.class)
@ConditionalOnProperty(prefix = "puriflow4j", name = "enabled", havingValue = "true")
public class PuriflowBaseAutoConfiguration {

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnMissingBean(MicrometerReporter.class)
    public MicrometerReporter micrometerReporter(MeterRegistry registry) {
        return new MicrometerReporter(registry, 200);
    }

    @Bean
    @ConditionalOnMissingBean(Reporter.class)
    public Reporter reporter(MicrometerReporter micrometerReporter) {
        return micrometerReporter;
    }

    @Bean
    public Sanitizer sanitizer(PuriflowProperties props) {
        var registry = new DetectorRegistry();
        var types = props.getDetectors().isEmpty()
                ? new ArrayList<>(DetectorRegistry.defaultTypes())
                : new ArrayList<>(props.getDetectors());
        var kvCfg = KVPatternConfig.of(
                props.getLogs().getKeyAllowlist(), props.getLogs().getKeyBlocklist());
        var detectors = registry.build(types, kvCfg);
        Action action =
                switch (props.getMode()) {
                    case DRY_RUN -> Action.WARN;
                    case MASK, STRICT -> Action.MASK;
                };
        return new io.puriflow4j.core.api.Sanitizer(detectors, action);
    }

    @Bean
    @ConditionalOnBean(MicrometerReporter.class)
    @ConditionalOnAvailableEndpoint(endpoint = PuriflowEndpoint.class)
    public PuriflowEndpoint puriflowEndpoint(MicrometerReporter reporter) {
        return new PuriflowEndpoint(reporter);
    }
}
