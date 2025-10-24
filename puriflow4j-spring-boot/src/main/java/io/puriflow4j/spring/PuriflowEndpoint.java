/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.spring;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

@Endpoint(id = "puriflow")
public class PuriflowEndpoint {

    private final MicrometerReporter reporter;

    public PuriflowEndpoint(MicrometerReporter reporter) {
        this.reporter = reporter;
    }

    @ReadOperation
    public Map<String, Object> info() {
        Map<String, Object> m = new HashMap<>();
        m.put("status", "OK");
        m.put("recentFindings", reporter.recentFindings());
        return m;
    }
}
