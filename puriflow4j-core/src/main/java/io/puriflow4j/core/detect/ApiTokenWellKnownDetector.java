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

/** Detects well-known vendor token formats (Stripe, Slack, GitHub...). */
public final class ApiTokenWellKnownDetector implements Detector {
    private static final String TYPE = "apiToken";
    private static final String MASK = "[MASKED_API_TOKEN]";

    private static final Pattern STRIPE = Pattern.compile("\\b(sk|pk)_(test|live)_[A-Za-z0-9]{10,}\\b");
    private static final Pattern SLACK = Pattern.compile("\\bxox[abprs]-[A-Za-z0-9-]{8,}\\b");
    private static final Pattern GITHUB =
            Pattern.compile("\\b(gh[pousr])_[A-Za-z0-9]{20,}\\b|\\bgithub_pat_[A-Za-z0-9_]{20,}\\b");

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();
        List<DetectionResult.Span> spans = new ArrayList<>();
        var m1 = STRIPE.matcher(s);
        while (m1.find()) spans.add(span(m1.start(), m1.end()));
        var m2 = SLACK.matcher(s);
        while (m2.find()) spans.add(span(m2.start(), m2.end()));
        var m3 = GITHUB.matcher(s);
        while (m3.find()) spans.add(span(m3.start(), m3.end()));
        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }

    private static DetectionResult.Span span(int a, int b) {
        return new DetectionResult.Span(a, b, TYPE, MASK);
    }
}
