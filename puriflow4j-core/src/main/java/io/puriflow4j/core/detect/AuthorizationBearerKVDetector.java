/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.models.*;
import io.puriflow4j.core.preset.KVPatternConfig;
import java.util.*;
import java.util.regex.*;

/** Matches "Authorization: Bearer <token>" and masks only the token. */
public final class AuthorizationBearerKVDetector implements Detector {
    private static final Pattern P =
            Pattern.compile("(?i)authorization\\s*[:=]\\s*bearer\\s*(?<val>[A-Za-z0-9._~-]{20,400})");
    private final String replacement;
    private final KVPatternConfig cfg;

    public AuthorizationBearerKVDetector(String replacement, KVPatternConfig cfg) {
        this.replacement = replacement;
        this.cfg = (cfg == null) ? KVPatternConfig.defaults() : cfg;
    }

    @Override
    public String name() {
        return "authorizationBearerKV";
    }

    @Override
    public DetectionResult detect(String input) {
        if (input == null || input.isEmpty()) return DetectionResult.empty();
        Matcher m = P.matcher(input);
        List<DetectionResult.Span> spans = new ArrayList<>();
        while (m.find()) {
            if (!cfg.isKeyAllowed("authorization") && cfg.isKeyBlocked("authorization")) {
                spans.add(new DetectionResult.Span(m.start("val"), m.end("val"), "authorization", replacement));
            }
        }
        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, spans);
    }
}
