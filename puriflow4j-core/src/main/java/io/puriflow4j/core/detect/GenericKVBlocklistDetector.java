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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * A generic key-value (KV) detector that enforces masking policy defined by
 * {@link io.puriflow4j.core.preset.KVPatternConfig}.
 * </p>
 *
 * <h2>Purpose</h2>
 * <p>
 * This detector handles any {@code key=value} or {@code key:value} pairs that may appear in log messages
 * (including MDC, structured JSON logs, or plain text). It applies the configured
 * <strong>allowlist/blocklist policy</strong> on the key name itself, regardless of the value format.
 * </p>
 *
 * <h3>Policy</h3>
 * <ul>
 *   <li>If the key name is <b>blocklisted</b> — the corresponding value is <b>always masked</b>, regardless of content.</li>
 *   <li>If the key name is <b>allowlisted</b> — the value is <b>never masked</b>.</li>
 *   <li>Otherwise — no action is taken; the value may still be masked later by
 *       more specific detectors (e.g. {@code PasswordKVDetector}, {@code TokenDetector}, etc.).</li>
 * </ul>
 *
 * <h3>Normalization</h3>
 * <p>
 * Key names are normalized before comparison:
 * lower-cased and stripped of dashes, underscores, and whitespace.
 * For example:
 * <pre>
 *   "X-AUTH-TOKEN", "x_auth_token", "xAuthToken" → "xauthtoken"
 * </pre>
 * This ensures configuration keys in YAML match a wide range of log variants.
 * </p>
 *
 * <h3>Supported patterns</h3>
 * <p>
 * The detector matches typical key/value pairs separated by '=' or ':'
 * with optional surrounding whitespace:
 * </p>
 * <pre>
 *   x-auth-token=abc123
 *   apiKey : xyz
 *   X-API-KEY   =   SECRET
 *   user_id=42
 * </pre>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // application.yml
 * puriflow4j:
 *   logs:
 *     key-blocklist: [x-auth-token, apiKey, cookie]
 *
 * // log message
 * log.info("user={}, x-auth-token={}, apiKey={}", "alice", "abc", "xyz");
 *
 * // output
 * user=alice, x-auth-token=[MASKED], apiKey=[MASKED]
 * }</pre>
 *
 * <h3>Order in registry</h3>
 * <p>
 * This detector should be placed <strong>first</strong> in the registry chain,
 * before other detectors, to ensure blocklisted keys are masked early
 * and their values never reach further detectors.
 * </p>
 *
 * @see io.puriflow4j.core.preset.KVPatternConfig
 * @see io.puriflow4j.core.detect.PasswordKVDetector
 * @see io.puriflow4j.core.detect.DbCredentialDetector
 */
public final class GenericKVBlocklistDetector implements Detector {
    private static final String TYPE = "blockedKey";
    private static final String MASK = "[MASKED]";

    private final KVPatternConfig kv;

    public GenericKVBlocklistDetector(KVPatternConfig kv) {
        this.kv = (kv == null) ? KVPatternConfig.defaults() : kv;
    }

    // key [:=] value
    // key: letters/digits/._- (dash is important!)
    // value: until whitespace/comma/semicolon/quote/closing bracket
    private static final Pattern KV =
            Pattern.compile("(?i)\\b([A-Za-z0-9._-]{1,128})\\s*[:=]\\s*([^\\s,;\"'\\]\\)\\}]{1,2048})");

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();

        List<DetectionResult.Span> spans = new ArrayList<>();
        Matcher m = KV.matcher(s);
        while (m.find()) {
            String rawKey = m.group(1);
            String normKey = KVPatternConfig.normalizeKey(rawKey); // см. обновление ниже
            if (kv.isBlockedKey(normKey)) {
                // Always mask the value region (group 2)
                spans.add(new DetectionResult.Span(m.start(2), m.end(2), TYPE, MASK));
            }
            // allowlist -> explicitly do nothing here
        }
        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }
}
