package io.puriflow4j.core.api.models;

import java.util.List;

public record DetectionResult(boolean found, List<Span> spans) {
    public static DetectionResult empty() { return new DetectionResult(false, List.of()); }

    /**
     * Span indices [start, end), the type, and the replacement string.
     */
    public record Span(int start, int end, String type, String replacement) { }
}
