/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.Detector;
import io.puriflow4j.core.api.model.DetectionResult;
import io.puriflow4j.core.preset.KVPatternConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class PasswordKVDetector implements Detector {
    private static final String TYPE = "password";
    private static final String MASK = "[MASKED]";
    private final KVPatternConfig kv;

    private static final Pattern P =
            Pattern.compile("(?i)\\b(password|passwd|pwd|secret|passphrase)\\s*[:=]\\s*([^\\s,;]{1,256})");

    public PasswordKVDetector(KVPatternConfig kv) {
        this.kv = kv;
    }

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();
        var m = P.matcher(s);
        List<DetectionResult.Span> spans = new ArrayList<>();
        while (m.find()) {
            String key = m.group(1);
            if (kv.isAllowedKey(key) && !kv.isBlockedKey(key)) continue; // honor allowlist unless blocked explicitly
            spans.add(new DetectionResult.Span(m.start(2), m.end(2), TYPE, MASK));
        }
        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }
}
