/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.Detector;
import io.puriflow4j.core.api.model.*;
import java.util.*;
import java.util.regex.*;

/** Bare card numbers: 13–19 digits (with optional spaces/dashes), validated via Luhn. */
public final class CreditCardDetector implements Detector {
    private static final String TYPE = "card";
    private static final String MASK = "[MASKED_CARD]";
    private static final Pattern DIGITS = Pattern.compile("(?<!\\d)(\\d[\\d\\s-]{11,20}\\d)(?!\\d)");

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();
        List<DetectionResult.Span> spans = new ArrayList<>();
        Matcher m = DIGITS.matcher(s);
        while (m.find()) {
            String raw = m.group(1).replaceAll("[\\s-]", "");
            if (raw.length() >= 13 && raw.length() <= 19 && luhn(raw)) {
                spans.add(new DetectionResult.Span(m.start(1), m.end(1), TYPE, MASK));
            }
        }
        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }

    private static boolean luhn(String s) {
        int sum = 0;
        boolean dbl = false;
        for (int i = s.length() - 1; i >= 0; i--) {
            int d = s.charAt(i) - '0';
            if (dbl) {
                d += d;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}
