/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.Detector;
import io.puriflow4j.core.api.model.DetectionResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects JWT-like tokens and Authorization: Bearer <token>.
 *
 * Golden-middle policy:
 *  - Contextual matches (Bearer / KV) stay permissive.
 *  - Bare matches are stricter to avoid FPs such as class names:
 *      * ≥ 3 segments (still),
 *      * header & payload segments length ≥ 10,
 *      * total token length ≥ 50,
 *      * header starts with "eyJ" (typical JWT),
 *      * at least one digit in either header or payload,
 *      * reject if immediately preceded by a Java package-like prefix.
 */
public final class TokenDetector implements Detector {
    private static final String TYPE = "token";
    private static final String MASK = "[MASKED_TOKEN]";

    // Base64URL chars
    private static final String B64U = "[A-Za-z0-9_-]";

    // --- Contextual token shape (permissive) ---
    private static final String SEG_RELAXED = B64U + "{2,}";
    private static final String MULTI_SEG_RELAXED = "(" + SEG_RELAXED + "(?:\\." + SEG_RELAXED + "){2,})";

    // 1) Authorization: Bearer <token>
    private static final Pattern BEARER = Pattern.compile("(?i)(Authorization\\s*:\\s*Bearer\\s+)" + MULTI_SEG_RELAXED);

    // 2) KV: token=..., access_token: ..., id-token = ...
    private static final Pattern KV_JWT =
            Pattern.compile("(?i)\\b(token|access[_-]?token|id[_-]?token)\\s*[:=]\\s*" + MULTI_SEG_RELAXED);

    // --- Bare token shape (stricter) ---
    private static final String SEG_STRICT = B64U + "{10,}";
    private static final String MULTI_SEG_STRICT = "(" + SEG_STRICT + "(?:\\." + SEG_STRICT + "){2,})";

    // Bare token not glued to base64url chars
    private static final Pattern JWT_BARE =
            Pattern.compile("(?<![A-Za-z0-9_-])" + MULTI_SEG_STRICT + "(?![A-Za-z0-9_-])");

    // Heuristics for bare tokens
    private static final int MIN_TOTAL_LEN = 50; // overall token chars
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");
    // A simple Java package-like left context guard (e.g., "java.lang.")
    private static final Pattern PKG_LEFT = Pattern.compile("([a-z]+\\.){1,}[A-Za-z_$][A-Za-z0-9_$]*\\.?$");

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();

        List<DetectionResult.Span> spans = new ArrayList<>();

        // 1) Authorization: Bearer <token> (keep permissive)
        var m1 = BEARER.matcher(s);
        while (m1.find()) {
            spans.add(span(m1.start(2), m1.end(2)));
        }

        // 2) KV forms (keep permissive)
        var m2 = KV_JWT.matcher(s);
        while (m2.find()) {
            spans.add(span(m2.start(2), m2.end(2)));
        }

        // 3) Bare JWT-like (stricter + post-filter)
        var m3 = JWT_BARE.matcher(s);
        while (m3.find()) {
            int start = m3.start(1);
            int end = m3.end(1);
            String token = s.substring(start, end);
            if (isPlausibleBareToken(s, start, token)) {
                spans.add(span(start, end));
            }
        }

        if (spans.isEmpty()) return DetectionResult.empty();
        spans = mergeOverlapping(spans);
        return new DetectionResult(true, List.copyOf(spans));
    }

    private static DetectionResult.Span span(int a, int b) {
        return new DetectionResult.Span(a, b, TYPE, MASK);
    }

    /** Merge overlapping or adjacent spans into one. */
    private static List<DetectionResult.Span> mergeOverlapping(List<DetectionResult.Span> in) {
        if (in.size() <= 1) return in;
        in.sort(Comparator.comparingInt(DetectionResult.Span::start).thenComparingInt(DetectionResult.Span::end));
        List<DetectionResult.Span> out = new ArrayList<>(in.size());
        DetectionResult.Span cur = in.get(0);
        for (int i = 1; i < in.size(); i++) {
            DetectionResult.Span next = in.get(i);
            if (next.start() <= cur.end()) {
                cur = new DetectionResult.Span(
                        cur.start(), Math.max(cur.end(), next.end()), cur.type(), cur.replacement());
            } else {
                out.add(cur);
                cur = next;
            }
        }
        out.add(cur);
        return out;
    }

    /** Bare-token plausibility filter: length, eyJ header, digits, and left-context guard. */
    private static boolean isPlausibleBareToken(CharSequence source, int startIdx, String token) {
        if (token.length() < MIN_TOTAL_LEN) return false;

        // Split into segments
        String[] segs = token.split("\\.");
        if (segs.length < 3) return false;

        String header = segs[0];
        String payload = segs[1];

        // Typical JWT header starts with "eyJ" (JSON object in base64url)
        if (!header.startsWith("eyJ")) return false;

        // Require a digit in header or payload to avoid simple words/class names
        if (!(DIGIT.matcher(header).matches() || DIGIT.matcher(payload).matches())) return false;

        // Left-context: avoid package-like identifiers directly before the token
        int left = Math.max(0, startIdx - 64);
        String leftCtx = source.subSequence(left, startIdx).toString();
        if (PKG_LEFT.matcher(leftCtx).find()) return false;

        return true;
    }
}
