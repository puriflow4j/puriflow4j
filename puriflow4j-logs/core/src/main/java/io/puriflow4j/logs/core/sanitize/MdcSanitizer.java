/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.core.sanitize;

import io.puriflow4j.core.api.Sanitizer;

import java.util.*;

/**
 * MDC sanitizer:
 * - For each MDC entry, runs the same Sanitizer pipeline on the full "key=value" string,
 *   so key=value detectors can trigger (e.g., password=, token=, apiKey=, etc).
 * - Extracts the masked value back and returns a sanitized MDC map.
 * - No allowlist, no hardcoded sensitive keys — all logic comes from the Sanitizer detectors.
 */
public final class MdcSanitizer {

    private final Sanitizer sanitizer;

    public MdcSanitizer(Sanitizer sanitizer) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    public Map<String, String> sanitize(Map<String, String> mdc, String logger) {
        if (mdc == null || mdc.isEmpty()) return Map.of();

        var out = new LinkedHashMap<String, String>(mdc.size());
        for (var e : mdc.entrySet()) {
            String key = e.getKey();
            String val = e.getValue();
            if (key == null) continue;
            if (val == null || val.isEmpty()) {
                out.put(key, val);
                continue;
            }

            // (1) Build synthetic "key=value" so key-based detectors can fire
            String combined = key + "=" + val;

            // (2) Run through the same Sanitizer pipeline as message text
            String maskedCombined = sanitizer.apply(combined, logger);

            // (3) Extract sanitized value part
            int eq = maskedCombined.indexOf('=');
            String maskedVal = (eq >= 0 && eq + 1 < maskedCombined.length())
                    ? maskedCombined.substring(eq + 1)
                    : maskedCombined;

            out.put(key, maskedVal);
        }

        return Collections.unmodifiableMap(out);
    }
}