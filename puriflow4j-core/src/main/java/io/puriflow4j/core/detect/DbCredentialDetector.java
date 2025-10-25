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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal detector for database credentials appearing in connection strings and property-style
 * configurations across popular datastores.
 * <p>
 * <b>What it masks</b>
 * <ul>
 *   <li><b>URI userinfo</b>: {@code scheme://user:pass@host} (e.g., PostgreSQL/MySQL/MariaDB/SQLServer,
 *       MongoDB(+srv), Redis/Rediss, AMQP, Neo4j, ClickHouse, etc.). Also supports Redis form
 *       {@code redis://:password@host} (no username).</li>
 *   <li><b>JDBC Oracle thin</b>: {@code jdbc:oracle:thin:user/pass@host:port:sid}.</li>
 *   <li><b>Inline properties</b> in DSNs: {@code ;user=...;password=...}, {@code ?password=...},
 *       {@code User ID=...; Password=...} (ODBC-like / SQLServer-style).</li>
 * </ul>
 * <p>
 * <b>What it does not mask</b>
 * <ul>
 *   <li>Generic {@code password=...} outside of connection-string context is handled by
 *       {@link PasswordKVDetector}. This detector focuses on DB/DSN formats to avoid conflicts and
 *       keep masking semantics consistent.</li>
 * </ul>
 * <p>
 * <b>Mask style</b>: {@code [MASKED_USER]} for usernames, {@code [MASKED_PASSWORD]} for passwords.
 * <p>
 * <b>Allow/Block lists</b>:
 * <ul>
 *   <li>For KV/property matches, the detector honors {@link KVPatternConfig#isAllowedKey(String)}
 *       and {@link KVPatternConfig#isBlockedKey(String)}. URI userinfo is <i>always</i> masked.</li>
 * </ul>
 * <p>
 * <b>Performance</b>: Single-pass regex scans; linear in input length. Designed for log-time
 * sanitization with negligible overhead compared to writing large stack traces.
 */
public final class DbCredentialDetector implements Detector {

    private static final String TYPE = "dbCredential";
    private static final String MASK_USER = "[MASKED_USER]";
    private static final String MASK_PASS = "[MASKED_PASSWORD]";

    private final KVPatternConfig kv;

    public DbCredentialDetector(KVPatternConfig kv) {
        this.kv = kv;
    }

    /** URI userinfo: scheme://userinfo@  (userinfo = user:pass | :pass | user) */
    private static final Pattern URI_USERINFO = Pattern.compile("(?i)\\b([a-z][a-z0-9+.-]*://)([^@/\\s]+)@");

    /** Oracle thin JDBC with user/pass segment: jdbc:oracle:thin:user/pass@host:port:sid */
    private static final Pattern ORACLE_THIN_USERPASS = Pattern.compile("(?i)\\b(jdbc:oracle:thin:)([^@\\s]+)@");

    /** Property-style user keys: ;user=... , ?user=... , &user=... , 'User ID=...' etc. */
    private static final Pattern PROP_USER =
            Pattern.compile("(?i)([;?&\\s\\p{Zs}]|^)\\s*(user|username|user\\s*id|uid)\\s*=\\s*([^;?&\\s]+)");

    /** Property-style password keys: ;password=... , ?password=... , &password=... , 'Password=...' etc. */
    private static final Pattern PROP_PASS =
            Pattern.compile("(?i)([;?&\\s\\p{Zs}]|^)\\s*(password|pwd|pass|secret|passphrase)\\s*=\\s*([^;?&\\s]+)");

    /** Query-param-only password (redundant to PROP_PASS but explicit for clarity). */
    private static final Pattern QUERY_PASS = Pattern.compile("(?i)([?&])(password|pwd|pass|secret)=([^&\\s]+)");

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();

        List<DetectionResult.Span> spans = new ArrayList<>();

        // 1) Generic URI userinfo: scheme://user[:pass]@...
        Matcher mu = URI_USERINFO.matcher(s);
        while (mu.find()) {
            int userinfoStart = mu.start(2);
            int userinfoEnd = mu.end(2);
            String userinfo = s.substring(userinfoStart, userinfoEnd);

            int colon = userinfo.indexOf(':');
            if (colon >= 0) {
                if (colon > 0) {
                    // mask username
                    spans.add(new DetectionResult.Span(userinfoStart, userinfoStart + colon, TYPE, MASK_USER));
                }
                if (colon + 1 < userinfo.length()) {
                    // mask password (redis://:password@host case included)
                    spans.add(new DetectionResult.Span(userinfoStart + colon + 1, userinfoEnd, TYPE, MASK_PASS));
                }
            } else {
                // only username present → still mask
                spans.add(new DetectionResult.Span(userinfoStart, userinfoEnd, TYPE, MASK_USER));
            }
        }

        // 2) Oracle thin with user/pass before '@'
        Matcher mo = ORACLE_THIN_USERPASS.matcher(s);
        while (mo.find()) {
            int credStart = mo.start(2);
            int credEnd = mo.end(2);
            String creds = s.substring(credStart, credEnd); // "user/pass" or just "user"
            int slash = creds.indexOf('/');
            if (slash >= 0) {
                if (slash > 0) {
                    spans.add(new DetectionResult.Span(credStart, credStart + slash, TYPE, MASK_USER));
                }
                if (slash + 1 < creds.length()) {
                    spans.add(new DetectionResult.Span(credStart + slash + 1, credEnd, TYPE, MASK_PASS));
                }
            } else {
                spans.add(new DetectionResult.Span(credStart, credEnd, TYPE, MASK_USER));
            }
        }

        // 3) Property-style user/password (SQLServer/JDBC/ODBC-like)
        Matcher mpUser = PROP_USER.matcher(s);
        while (mpUser.find()) {
            String key = normalizeKey(mpUser.group(2));
            if (!kv.isAllowedKey(key)) {
                spans.add(new DetectionResult.Span(mpUser.start(3), mpUser.end(3), TYPE, MASK_USER));
            }
        }
        Matcher mpPass = PROP_PASS.matcher(s);
        while (mpPass.find()) {
            String key = normalizeKey(mpPass.group(2));
            if (!kv.isAllowedKey(key) || kv.isBlockedKey(key)) {
                spans.add(new DetectionResult.Span(mpPass.start(3), mpPass.end(3), TYPE, MASK_PASS));
            }
        }

        // 4) Query-param-only password (?password=... or &password=...)
        Matcher mq = QUERY_PASS.matcher(s);
        while (mq.find()) {
            String key = normalizeKey(mq.group(2));
            if (!kv.isAllowedKey(key) || kv.isBlockedKey(key)) {
                spans.add(new DetectionResult.Span(mq.start(3), mq.end(3), TYPE, MASK_PASS));
            }
        }

        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }

    private static String normalizeKey(String k) {
        return (k == null) ? "" : k.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
