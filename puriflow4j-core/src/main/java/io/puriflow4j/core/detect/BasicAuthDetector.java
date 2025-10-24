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

public final class BasicAuthDetector implements Detector {
    private static final String TYPE = "basicAuth";
    private static final String MASK = "[MASKED_BASIC_AUTH]";
    private static final Pattern BASIC = Pattern.compile("(?i)(Authorization\\s*:\\s*Basic\\s+)([A-Za-z0-9+/=]{6,})");

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();
        List<DetectionResult.Span> spans = new ArrayList<>();
        var m = BASIC.matcher(s);
        while (m.find()) spans.add(new DetectionResult.Span(m.start(2), m.end(2), TYPE, MASK));
        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }
}
