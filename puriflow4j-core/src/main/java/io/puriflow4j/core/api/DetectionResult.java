package io.puriflow4j.core.api;

import java.util.List;

public record DetectionResult(boolean found, List<Span> spans) {
    public static DetectionResult empty() { return new DetectionResult(false, List.of()); }

    public record Span(int start, int end, String replacement) {}
}
