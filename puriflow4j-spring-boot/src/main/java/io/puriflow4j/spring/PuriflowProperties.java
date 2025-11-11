/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.spring;

import io.puriflow4j.core.api.model.DetectorType;
import io.puriflow4j.core.api.model.Mode;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "puriflow4j")
public class PuriflowProperties {

    private Logs logs = new Logs();

    public void setLogs(Logs logs) {
        this.logs = (logs == null) ? new Logs() : logs;
    }

    // ---- nested: logs ----
    public static final class Logs {
        @Setter
        @Getter
        private boolean enabled = false;

        @Setter
        @Getter
        private Mode mode = Mode.DRY_RUN;

        private List<DetectorType> detectors = new ArrayList<>();
        private List<String> onlyLoggers = new ArrayList<>();
        private List<String> ignoreLoggers = new ArrayList<>();
        private List<String> keyAllowlist = new ArrayList<>();
        private List<String> keyBlocklist = new ArrayList<>();

        @Getter
        private Errors errors = new Errors();

        public List<DetectorType> getDetectors() {
            return Collections.unmodifiableList(detectors);
        }

        public void setDetectors(List<DetectorType> detectors) {
            this.detectors = new ArrayList<>(Objects.requireNonNullElse(detectors, List.of()));
        }

        public List<String> getOnlyLoggers() {
            return Collections.unmodifiableList(onlyLoggers);
        }

        public void setOnlyLoggers(List<String> v) {
            this.onlyLoggers = new ArrayList<>(Objects.requireNonNullElse(v, List.of()));
        }

        public List<String> getIgnoreLoggers() {
            return Collections.unmodifiableList(ignoreLoggers);
        }

        public void setIgnoreLoggers(List<String> v) {
            this.ignoreLoggers = new ArrayList<>(Objects.requireNonNullElse(v, List.of()));
        }

        public List<String> getKeyAllowlist() {
            return Collections.unmodifiableList(keyAllowlist);
        }

        public void setKeyAllowlist(List<String> v) {
            this.keyAllowlist = new ArrayList<>(Objects.requireNonNullElse(v, List.of()));
        }

        public List<String> getKeyBlocklist() {
            return Collections.unmodifiableList(keyBlocklist);
        }

        public void setKeyBlocklist(List<String> v) {
            this.keyBlocklist = new ArrayList<>(Objects.requireNonNullElse(v, List.of()));
        }

        public void setErrors(Errors e) {
            this.errors = (e == null) ? new Errors() : e;
        }
    }

    // ---- nested: logs.errors ----
    public static final class Errors {
        @Setter
        @Getter
        private boolean shorten = false;

        @Setter
        @Getter
        private Integer maxDepth; // null = not specified => ignore

        private List<String> hidePackages = new ArrayList<>();

        @Setter
        @Getter
        private boolean categorize = false;

        public List<String> getHidePackages() {
            return Collections.unmodifiableList(hidePackages);
        }

        public void setHidePackages(List<String> v) {
            this.hidePackages = new ArrayList<>(Objects.requireNonNullElse(v, List.of()));
        }
    }
}
