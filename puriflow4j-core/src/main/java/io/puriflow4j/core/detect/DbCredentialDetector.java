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

/** Detects credentials in DB/DSN/URL forms and generic user/password KVs. */
public final class DbCredentialDetector implements Detector {
    private static final String TYPE = "dbCredential";
    private static final String MASK_USER = "******";
    private static final String MASK_PASS = "******";
    private final KVPatternConfig kv;

    public DbCredentialDetector(KVPatternConfig kv) {
        this.kv = kv;
    }

    // user:pass@host (jdbc, amqp, redis, mongo, etc.)
    private static final Pattern USER_PASS_AT =
            Pattern.compile("(?i)([A-Za-z0-9._%+-]{1,64}):([A-Za-z0-9._%+\\-!@#$%^&*]{1,128})@");

    // KV forms: dbUser=..., dbPassword=..., username=..., pass=...
    private static final Pattern KV_USER =
            Pattern.compile("(?i)\\b(user|username|dbUser|dbUsername)\\s*[:=]\\s*([A-Za-z0-9._%+-]{1,64})");
    private static final Pattern KV_PASS =
            Pattern.compile("(?i)\\b(pass|password|dbPass|dbPassword)\\s*[:=]\\s*([^\\s,;]{1,128})");

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();
        List<DetectionResult.Span> spans = new ArrayList<>();

        var mAt = USER_PASS_AT.matcher(s);
        while (mAt.find()) {
            spans.add(new DetectionResult.Span(mAt.start(1), mAt.end(1), TYPE, MASK_USER));
            spans.add(new DetectionResult.Span(mAt.start(2), mAt.end(2), TYPE, MASK_PASS));
        }

        var mu = KV_USER.matcher(s);
        while (mu.find()) {
            String key = mu.group(1);
            if (!kv.isAllowedKey(key)) {
                spans.add(new DetectionResult.Span(mu.start(2), mu.end(2), TYPE, MASK_USER));
            }
        }
        var mp = KV_PASS.matcher(s);
        while (mp.find()) {
            String key = mp.group(1);
            if (!kv.isAllowedKey(key) || kv.isBlockedKey(key)) {
                spans.add(new DetectionResult.Span(mp.start(2), mp.end(2), TYPE, MASK_PASS));
            }
        }

        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }
}
