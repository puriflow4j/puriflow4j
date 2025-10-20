/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.api.models;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;

@SuppressFBWarnings
public record DetectionResult(boolean found, List<Span> spans) {
    public static DetectionResult empty() {
        return new DetectionResult(false, List.of());
    }

    /** Span indices [start, end), the type, and the replacement string. */
    public record Span(int start, int end, String type, String replacement) {}
}
