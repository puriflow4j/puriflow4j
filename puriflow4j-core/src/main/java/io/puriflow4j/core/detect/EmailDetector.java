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

public final class EmailDetector implements Detector {
    private static final String TYPE = "email";
    private static final String MASK = "[MASKED_EMAIL]";
    private static final Pattern P =
            Pattern.compile("\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();
        var m = P.matcher(s);
        List<DetectionResult.Span> spans = new ArrayList<>();
        while (m.find()) spans.add(new DetectionResult.Span(m.start(), m.end(), TYPE, MASK));
        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }
}
