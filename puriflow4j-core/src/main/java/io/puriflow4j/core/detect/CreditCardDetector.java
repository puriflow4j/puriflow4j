/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.models.*;
import java.util.*;
import java.util.regex.*;

/** Bare card numbers: 13–19 digits (with optional spaces/dashes), validated via Luhn. */
public final class CreditCardDetector implements Detector {
    private static final Pattern DIGITS = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");
    private final String replacement;

    public CreditCardDetector(String replacement) {
        this.replacement = replacement;
    }

    @Override
    public String name() {
        return "creditCardBare";
    }

    @Override
    public DetectionResult detect(String input) {
        if (input == null || input.isEmpty()) return DetectionResult.empty();
        Matcher m = DIGITS.matcher(input);
        List<DetectionResult.Span> spans = new ArrayList<>();

        while (m.find()) {
            String raw = m.group().replaceAll("[ -]", "");
            if (luhn(raw)) {
                spans.add(new DetectionResult.Span(m.start(), m.end(), "creditCard", replacement));
            }
        }

        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, spans);
    }

    static boolean luhn(String s) {
        int sum = 0;
        boolean alt = false;

        for (int i = s.length() - 1; i >= 0; i--) {
            int n = s.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alt = !alt;
        }

        return sum % 10 == 0;
    }
}
