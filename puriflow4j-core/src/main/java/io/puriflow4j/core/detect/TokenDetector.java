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

/** Detects JWT-like tokens (2+ dots) and Authorization: Bearer <token>. */
public final class TokenDetector implements Detector {
    private static final String TYPE = "token";
    private static final String MASK = "[MASKED_TOKEN]";

    // Base64URL-char class (RFC 7515), без точки
    private static final String B64U = "[A-Za-z0-9_-]";
    // Разрешим короткие сегменты (2+), т.к. в демо токене сегменты по 3 символа
    private static final String SEG = B64U + "{2,}";
    // Токен: минимум 3 сегмента => минимум 2 точки. Разрешаем больше (грязные/усечённые варианты)
    private static final String MULTI_SEG = "(" + SEG + "(?:\\." + SEG + "){2,})";

    // 1) Bare JWT-подобная строка (не спаянная по краям с base64url-символами)
    private static final Pattern JWT_BARE = Pattern.compile("(?<![A-Za-z0-9_-])" + MULTI_SEG + "(?![A-Za-z0-9_-])");

    // 2) Authorization: Bearer <token>
    private static final Pattern BEARER = Pattern.compile("(?i)(Authorization\\s*:\\s*Bearer\\s+)" + MULTI_SEG);

    // 3) KV: token=..., access_token: ..., id-token = ...
    private static final Pattern KV_JWT =
            Pattern.compile("(?i)\\b(token|access[_-]?token|id[_-]?token)\\s*[:=]\\s*" + MULTI_SEG);

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();

        List<DetectionResult.Span> spans = new ArrayList<>();

        // Authorization: Bearer <token>
        var m1 = BEARER.matcher(s);
        while (m1.find()) spans.add(span(m1.start(2), m1.end(2)));

        // KV формы
        var m2 = KV_JWT.matcher(s);
        while (m2.find()) spans.add(span(m2.start(2), m2.end(2)));

        // Bare
        var m3 = JWT_BARE.matcher(s);
        while (m3.find()) spans.add(span(m3.start(1), m3.end(1)));

        if (spans.isEmpty()) return DetectionResult.empty();

        // На всякий случай склеим пересекающиеся/соседние спаны
        spans = mergeOverlapping(spans);
        return new DetectionResult(true, List.copyOf(spans));
    }

    private static DetectionResult.Span span(int a, int b) {
        return new DetectionResult.Span(a, b, TYPE, MASK);
    }

    /** Merge overlapping or adjacent spans into one to ensure full-token masking (avoid leftovers like ".sig"). */
    private static List<DetectionResult.Span> mergeOverlapping(List<DetectionResult.Span> in) {
        if (in.size() <= 1) return in;
        in.sort(Comparator.comparingInt(DetectionResult.Span::start).thenComparingInt(DetectionResult.Span::end));

        List<DetectionResult.Span> out = new ArrayList<>(in.size());
        DetectionResult.Span cur = in.get(0);

        for (int i = 1; i < in.size(); i++) {
            DetectionResult.Span next = in.get(i);
            if (next.start() <= cur.end()) {
                // overlap/touch → extend
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
}
