/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.api.model;

import java.util.List;

public record DetectionResult(boolean found, List<Span> spans) {
    public static DetectionResult empty() {
        return new DetectionResult(false, List.of());
    }

    /** Span indices [start,end), with a suggested replacement. */
    public record Span(int start, int end, String type, String replacement) {}
}
