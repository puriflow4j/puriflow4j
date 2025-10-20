/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.spring;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.puriflow4j.core.api.models.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Maps application.yml -> puriflow4j.* */
@ConfigurationProperties(prefix = "puriflow4j")
public class PuriflowProperties {

    @Setter
    @Getter
    private boolean enabled = true;

    @Setter
    @Getter
    private Mode mode = Mode.MASK;

    // store internally as mutable, expose as unmodifiable
    private List<DetectorType> detectors = new ArrayList<>();

    // nested config bean
    private Logs logs = new Logs();

    /** Return an unmodifiable view to avoid exposing internal representation. */
    public List<DetectorType> getDetectors() {
        return Collections.unmodifiableList(detectors);
    }

    /** Defensive copy to avoid storing external mutable list. */
    public void setDetectors(List<DetectorType> detectors) {
        this.detectors = new ArrayList<>(Objects.requireNonNullElse(detectors, List.of()));
    }

    /**
     * Spring @ConfigurationProperties expects a mutable nested bean instance.
     * We expose it intentionally; consumers should not mutate it directly at runtime.
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Spring @ConfigurationProperties requires a mutable nested bean; "
                    + "the object is owned by the configuration container.")
    public Logs getLogs() {
        return logs;
    }

    /** Defensive copy assign (or you can simply assign if only framework sets it). */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Logs is a configuration holder managed by Spring; assignment is intentional.")
    public void setLogs(Logs logs) {
        this.logs = (logs == null) ? new Logs() : logs;
    }

    /** Nested configuration for logging settings. */
    public static final class Logs {
        @Setter
        @Getter
        private boolean enabled = true;

        private List<String> onlyLoggers = new ArrayList<>();
        private List<String> ignoreLoggers = new ArrayList<>();
        private List<String> keyAllowlist = new ArrayList<>();
        private List<String> keyBlocklist = new ArrayList<>();

        public List<String> getOnlyLoggers() {
            return Collections.unmodifiableList(onlyLoggers);
        }

        public void setOnlyLoggers(List<String> onlyLoggers) {
            this.onlyLoggers = new ArrayList<>(Objects.requireNonNullElse(onlyLoggers, List.of()));
        }

        public List<String> getIgnoreLoggers() {
            return Collections.unmodifiableList(ignoreLoggers);
        }

        public void setIgnoreLoggers(List<String> ignoreLoggers) {
            this.ignoreLoggers = new ArrayList<>(Objects.requireNonNullElse(ignoreLoggers, List.of()));
        }

        public List<String> getKeyAllowlist() {
            return Collections.unmodifiableList(keyAllowlist);
        }

        public void setKeyAllowlist(List<String> keyAllowlist) {
            this.keyAllowlist = new ArrayList<>(Objects.requireNonNullElse(keyAllowlist, List.of()));
        }

        public List<String> getKeyBlocklist() {
            return Collections.unmodifiableList(keyBlocklist);
        }

        public void setKeyBlocklist(List<String> keyBlocklist) {
            this.keyBlocklist = new ArrayList<>(Objects.requireNonNullElse(keyBlocklist, List.of()));
        }
    }
}
