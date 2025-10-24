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

public final class IpDetector implements Detector {
    private static final String TYPE = "ip";
    private static final String MASK = "[MASKED_IP]";
    private static final Pattern IPV4 = Pattern.compile("(?<!\\d)(?:\\d{1,3}\\.){3}\\d{1,3}(?!\\d)");
    private static final Pattern IPV6 =
            Pattern.compile("\\b([0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();
        List<DetectionResult.Span> spans = new ArrayList<>();
        var a = IPV4.matcher(s);
        while (a.find()) spans.add(new DetectionResult.Span(a.start(), a.end(), TYPE, MASK));
        var b = IPV6.matcher(s);
        while (b.find()) spans.add(new DetectionResult.Span(b.start(), b.end(), TYPE, MASK));
        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }
}
