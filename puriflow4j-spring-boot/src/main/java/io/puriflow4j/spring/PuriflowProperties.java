/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.spring;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.puriflow4j.core.api.model.DetectorType;
import io.puriflow4j.core.api.model.Mode;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "puriflow4j")
public class PuriflowProperties {

    @Setter
    @Getter
    private boolean enabled = true;

    @Setter
    @Getter
    private Mode mode = Mode.MASK;

    private List<DetectorType> detectors = new ArrayList<>();

    private Logs logs = new Logs();

    public List<DetectorType> getDetectors() {
        return Collections.unmodifiableList(detectors);
    }

    public void setDetectors(List<DetectorType> detectors) {
        this.detectors = new ArrayList<>(Objects.requireNonNullElse(detectors, List.of()));
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Mutable nested bean owned by Spring configuration")
    public Logs getLogs() {
        return logs;
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Assignment of nested config is intentional for Spring binding")
    public void setLogs(Logs logs) {
        this.logs = (logs == null) ? new Logs() : logs;
    }

    // ---- nested: logs ----
    @SuppressFBWarnings
    public static final class Logs {
        @Setter
        @Getter
        private boolean enabled = true;

        private List<String> onlyLoggers = new ArrayList<>();
        private List<String> ignoreLoggers = new ArrayList<>();
        private List<String> keyAllowlist = new ArrayList<>();
        private List<String> keyBlocklist = new ArrayList<>();

        private Errors errors = new Errors();

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

        public Errors getErrors() {
            return errors;
        }

        public void setErrors(Errors e) {
            this.errors = (e == null) ? new Errors() : e;
        }
    }

    // ---- nested: logs.errors ----
    public static final class Errors {
        @Setter
        @Getter
        private boolean shorten = true;

        @Setter
        @Getter
        private int maxDepth = 25; // number of app frames to show

        private List<String> hidePackages =
                new ArrayList<>(List.of("org.springframework", "com.fasterxml.jackson", "java.util.concurrent"));

        @Setter
        @Getter
        private boolean categorize = true;

        public List<String> getHidePackages() {
            return Collections.unmodifiableList(hidePackages);
        }

        public void setHidePackages(List<String> v) {
            this.hidePackages = new ArrayList<>(Objects.requireNonNullElse(v, List.of()));
        }
    }
}
