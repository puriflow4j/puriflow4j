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

/** Detect common cloud access keys (AWS/Azure/GCP) and generic x-api-key like values. */
public final class CloudAccessKeyDetector implements Detector {
    private static final String TYPE = "cloudAccessKey";
    private static final String MASK = "[MASKED_ACCESS_KEY]";
    private final KVPatternConfig kv;

    public CloudAccessKeyDetector(KVPatternConfig kv) {
        this.kv = kv;
    }

    // AWS Access Key: AKIA/ASIA/AIDA…
    private static final Pattern AWS = Pattern.compile("\\b(AKIA|ASIA|AIDA|AGPA)[A-Z0-9]{12,20}\\b");

    // Azure SAS signature in URL: sig=<...>
    private static final Pattern AZURE_SAS = Pattern.compile("(?i)([?&]sig=)([A-Za-z0-9%+/=_-]{10,})");

    // KV: x-api-key, apiKey, accessKey=...
    private static final Pattern KV_GENERIC = Pattern.compile(
            "(?i)\\b([A-Za-z0-9_\\-]*?(?:api[-_]?key|access[-_]?key|accessKey|x-api-key))\\s*[:=]\\s*([A-Za-z0-9\\-_.~+/=]{8,})");

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();
        List<DetectionResult.Span> spans = new ArrayList<>();

        var a = AWS.matcher(s);
        while (a.find()) spans.add(span(a.start(), a.end()));

        var z = AZURE_SAS.matcher(s);
        while (z.find()) spans.add(span(z.start(2), z.end(2)));

        var kvM = KV_GENERIC.matcher(s);
        while (kvM.find()) {
            String key = kvM.group(1);
            if (kv.isAllowedKey(key)) continue; // respect allowlist
            // blocklist is not needed here - we will mask anyway; blocklist is useful for PasswordKV
            spans.add(span(kvM.start(2), kvM.end(2)));
        }

        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }

    private static DetectionResult.Span span(int a, int b) {
        return new DetectionResult.Span(a, b, TYPE, MASK);
    }
}
