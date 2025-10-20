/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.spring;

import io.puriflow4j.core.api.models.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Global configuration bound from application.yml. */
@ConfigurationProperties(prefix = "puriflow4j")
public class PuriflowProperties {
    private Mode mode = Mode.MASK;
    private List<DetectorType> detectors = new ArrayList<>();
    private Logs logs = new Logs();

    public static class Logs {
        private boolean enabled = true;
        private List<String> onlyLoggers = new ArrayList<>();
        private List<String> ignoreLoggers = new ArrayList<>();
        private List<String> keyAllowlist = List.of("traceId", "requestId", "correlationId");
        private List<String> keyBlocklist = List.of("password", "secret", "apiKey", "token", "authorization");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getOnlyLoggers() {
            return onlyLoggers;
        }

        public void setOnlyLoggers(List<String> onlyLoggers) {
            this.onlyLoggers = onlyLoggers;
        }

        public List<String> getIgnoreLoggers() {
            return ignoreLoggers;
        }

        public void setIgnoreLoggers(List<String> ignoreLoggers) {
            this.ignoreLoggers = ignoreLoggers;
        }

        public List<String> getKeyAllowlist() {
            return keyAllowlist;
        }

        public void setKeyAllowlist(List<String> keyAllowlist) {
            this.keyAllowlist = keyAllowlist;
        }

        public List<String> getKeyBlocklist() {
            return keyBlocklist;
        }

        public void setKeyBlocklist(List<String> keyBlocklist) {
            this.keyBlocklist = keyBlocklist;
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public List<DetectorType> getDetectors() {
        return detectors;
    }

    public void setDetectors(List<DetectorType> detectors) {
        this.detectors = detectors;
    }

    public Logs getLogs() {
        return logs;
    }

    public void setLogs(Logs logs) {
        this.logs = logs;
    }
}
