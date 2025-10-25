/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.Detector;
import io.puriflow4j.core.api.model.DetectionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts the address/authority+path of connection URLs while preserving the scheme.
 *
 * <p>Examples:
 * <ul>
 *   <li>jdbc:postgresql://db.prod/app → jdbc:postgresql://[MASKED_URL]</li>
 *   <li>mongodb://cluster0.example.com/db?authSource=admin → mongodb://[MASKED_URL]</li>
 *   <li>redis://:secret@localhost:6379/0 → redis://[MASKED_URL]</li>
 *   <li>https://api.example.com/v1/users → https://[MASKED_URL]</li>
 *   <li>s3://my-bucket/private/path → s3://[MASKED_URL]</li>
 * </ul>
 *
 * <p>Note: This detector does NOT mask user/password in userinfo. That is the job of
 * {@link DbCredentialDetector}. Keep registry order so credentials are masked first, then URL redaction.
 */
public final class UrlRedactorDetector implements Detector {

    private static final String TYPE = "url";
    private static final String MASK = "[MASKED_URL]";

    /**
     * Whitelist of schemes that typically carry sensitive hosts/paths. Explicit list reduces false positives.
     */
    private static final Pattern SCHEME_SLASHES = Pattern.compile("(?i)\\b("
            + "(?:jdbc:(?:postgresql|mysql|mariadb|sqlserver|h2))"
            + "|postgres(?:ql)?"
            + "|mysql"
            + "|mariadb"
            + "|sqlserver"
            + "|mongodb(?:\\+srv)?"
            + "|redis|rediss"
            + "|amqp|kafka"
            + "|clickhouse"
            + "|neo4j"
            + "|cassandra"
            + "|http|https|ftp"
            + "|s3|gs"
            + ")://([^\\s\"'\\)\\]]+)");

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();

        List<DetectionResult.Span> spans = new ArrayList<>();
        Matcher m = SCHEME_SLASHES.matcher(s);
        while (m.find()) {
            // redact everything after "://"
            spans.add(new DetectionResult.Span(m.start(2), m.end(2), TYPE, MASK));
        }
        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }
}
