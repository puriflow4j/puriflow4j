/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.Detector;
import io.puriflow4j.core.api.model.DetectionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Detects JWT-like tokens and Authorization: Bearer <token>. */
public final class TokenDetector implements Detector {
    private static final String TYPE = "token";
    private static final String MASK = "[MASKED_TOKEN]";
    private static final Pattern JWT =
            Pattern.compile("(?<![A-Za-z0-9-_])([A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+)(?![A-Za-z0-9-_])");
    private static final Pattern BEARER =
            Pattern.compile("(?i)(Authorization\\s*:\\s*Bearer\\s+)([A-Za-z0-9-_.~+/=]+)");

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();
        List<DetectionResult.Span> spans = new ArrayList<>();
        var m1 = BEARER.matcher(s);
        while (m1.find()) spans.add(span(m1.start(2), m1.end(2)));
        var m2 = JWT.matcher(s);
        while (m2.find()) spans.add(span(m2.start(1), m2.end(1)));
        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }

    private static DetectionResult.Span span(int a, int b) {
        return new DetectionResult.Span(a, b, TYPE, MASK);
    }
}
