/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.Detector;
import io.puriflow4j.core.api.model.DetectionResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

public final class BasicAuthDetector implements Detector {
    private static final String TYPE = "basicAuth";
    private static final String MASK = "[MASKED_BASIC_AUTH]";

    // Match “Basic <base64>” anywhere (case-insensitive).
    // NOTE: no trailing \b; instead we assert the next char is NOT a base64 char.
    private static final Pattern BASIC = Pattern.compile("(?i)\\bBasic\\s+([A-Za-z0-9+/=]{8,})(?![A-Za-z0-9+/=])");

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();

        var spans = new ArrayList<DetectionResult.Span>();
        var m = BASIC.matcher(s);

        while (m.find()) {
            // Optional: validate it decodes to “user:pass” to reduce false positives.
            String token = m.group(1);
            if (looksLikeBasicCredentials(token)) {
                spans.add(new DetectionResult.Span(m.start(1), m.end(1), TYPE, MASK));
            }
        }

        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }

    private static boolean looksLikeBasicCredentials(String b64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(b64);
            String val = new String(decoded, StandardCharsets.ISO_8859_1);
            // Basic auth is typically "username:password"
            int idx = val.indexOf(':');
            return idx > 0 && idx < val.length() - 1;
        } catch (IllegalArgumentException e) {
            return false; // not valid base64
        }
    }
}
