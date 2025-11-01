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
 * Universal detector for DB credentials inside connection strings and DSN-like property blobs.
 *
 * Masks:
 *  - URI userinfo: scheme://user[:pass]@host
 *  - Oracle thin:  jdbc:oracle:thin:user/pass@host:port:sid
 *  - Property-style pairs in DSN blobs (not in free text): ;user=... ;password=...
 *  - Query params for password: ?password=... &password=...
 *
 * Does NOT mask:
 *  - Generic "password=..." in free text (left to PasswordKVDetector).
 */
public final class DbCredentialDetector implements Detector {

    private static final String TYPE = "dbCredential";
    private static final String MASK_USER = "[MASKED_USER]";
    private static final String MASK_PASS = "[MASKED_PASSWORD]";

    private final KVPatternConfig kv;

    public DbCredentialDetector(KVPatternConfig kv) {
        this.kv = (kv == null) ? KVPatternConfig.defaults() : kv;
    }

    /** URI userinfo: scheme://userinfo@  (userinfo = user:pass | :pass | user)
     *  Tightened: disallow ';', '?', '#', '&', '=' inside userinfo to avoid matching DSN property segments.
     */
    private static final Pattern URI_USERINFO = Pattern.compile("(?i)\\b([a-z][a-z0-9+.-]*://)([^@/\\s;?&#=]+)@");

    /** jdbc:oracle:thin:user/pass@... */
    private static final Pattern ORACLE_THIN_USERPASS = Pattern.compile("(?i)\\b(jdbc:oracle:thin:)([^@\\s]+)@");

    /**
     * Property user key outside of query. Two capture groups:
     *  (1) key  (2) value
     * Preceded by start/whitespace/&/; to avoid mid-token matches.
     */
    private static final Pattern PROP_USER =
            Pattern.compile("(?i)(?:(?<=^)|(?<=[\\s;&]))(user|username|user\\s*id|uid)\\s*=\\s*([^;?&\\s]+)");

    /**
     * Property password key outside of query. Two capture groups:
     *  (1) key  (2) value
     */
    private static final Pattern PROP_PASS =
            Pattern.compile("(?i)(?:(?<=^)|(?<=[\\s;&]))(password|pwd|pass|secret|passphrase)\\s*=\\s*([^;?&\\s]+)");

    /** Query-string passwords (?password=... or &password=...), groups: (2)=key, (3)=value */
    private static final Pattern QUERY_PASS = Pattern.compile("(?i)([?&])(password|pwd|pass|secret)=([^&\\s]+)");

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();

        final List<DetectionResult.Span> spans = new ArrayList<>();

        // 1) Generic URI userinfo: scheme://user[:pass]@host
        // Replace the old URI_USERINFO matcher block with this manual scan:
        int from = 0;
        while (true) {
            // find scheme://
            int schemeIdx = indexOfScheme(s, from);
            if (schemeIdx < 0) break;
            int authorityStart = schemeIdx; // immediately after "://"

            // find end of authority (before path/query/params or whitespace)
            int authorityEnd = findAuthorityEnd(s, authorityStart);

            // find the LAST '@' inside authority
            int at = s.lastIndexOf('@', authorityEnd - 1);
            if (at > authorityStart) {
                // we have userinfo between authorityStart (inclusive) and at (exclusive)
                String userinfo = s.substring(authorityStart, at);
                int colon = userinfo.indexOf(':');
                if (colon >= 0) {
                    // user:pass
                    int userStart = authorityStart;
                    int userEnd = authorityStart + colon;
                    int passStart = userEnd + 1;
                    int passEnd = at;
                    if (userEnd > userStart) {
                        spans.add(new DetectionResult.Span(userStart, userEnd, TYPE, MASK_USER));
                    }
                    if (passEnd > passStart) {
                        spans.add(new DetectionResult.Span(passStart, passEnd, TYPE, MASK_PASS));
                    }
                } else {
                    // only user
                    spans.add(new DetectionResult.Span(authorityStart, at, TYPE, MASK_USER));
                }
            }

            from = authorityEnd; // continue scanning after this authority segment
        }

        // 2) jdbc:oracle:thin:user/pass@...
        for (Matcher m = ORACLE_THIN_USERPASS.matcher(s); m.find(); ) {
            int credStart = m.start(2);
            int credEnd = m.end(2);
            String creds = s.substring(credStart, credEnd);
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

        // Heuristic: only treat property pairs if the string is likely a DSN/config (not free text).
        if (isLikelyDsnContext(s)) {
            // 3a) user=...
            for (Matcher m = PROP_USER.matcher(s); m.find(); ) {
                // Skip if inside query (just in case):
                int i = m.start();
                char prev = (i > 0) ? s.charAt(i - 1) : '\0';
                if (prev == '?' || prev == '&') continue;

                String key = normalizeKey(m.group(1));
                if (!kv.isAllowedKey(key)) {
                    spans.add(new DetectionResult.Span(m.start(2), m.end(2), TYPE, MASK_USER));
                }
            }
            // 3b) password=...
            for (Matcher m = PROP_PASS.matcher(s); m.find(); ) {
                int i = m.start();
                char prev = (i > 0) ? s.charAt(i - 1) : '\0';
                if (prev == '?' || prev == '&') continue;

                String key = normalizeKey(m.group(1));
                if (!kv.isAllowedKey(key) || kv.isBlockedKey(key)) {
                    spans.add(new DetectionResult.Span(m.start(2), m.end(2), TYPE, MASK_PASS));
                }
            }
        }

        // 4) Query-string password always considered DSN-ish
        for (Matcher m = QUERY_PASS.matcher(s); m.find(); ) {
            String key = normalizeKey(m.group(2));
            if (!kv.isAllowedKey(key) || kv.isBlockedKey(key)) {
                spans.add(new DetectionResult.Span(m.start(3), m.end(3), TYPE, MASK_PASS));
            }
        }

        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }

    /** Cheap heuristic: looks like a DSN/config, not free text. */
    private static boolean isLikelyDsnContext(String s) {
        String low = s.toLowerCase(Locale.ROOT);
        if (low.contains("://") || low.contains("jdbc:")) return true;
        // ODBC/SQLServer-ish hints or multiple ';' k=v pairs
        if (low.contains("server=")
                || low.contains("data source=")
                || low.contains("addr=")
                || low.contains("address=")) return true;
        int semi = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == ';') semi++;
        return semi >= 2;
    }

    private static String normalizeKey(String k) {
        return (k == null) ? "" : k.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    // Finds start index of authority right after "scheme://", or -1 if not found.
    private static int indexOfScheme(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            // cheap check for "://"
            if (c == ':' && i + 2 < s.length() && s.charAt(i + 1) == '/' && s.charAt(i + 2) == '/') {
                // ensure valid scheme (RFC 3986-ish): [a-z][a-z0-9+.-]*
                int j = i - 1;
                if (j >= 0 && isSchemeChar(s.charAt(j))) {
                    // backtrack to the start of scheme
                    while (j - 1 >= 0 && isSchemeChar(s.charAt(j - 1))) j--;
                    // authority starts after "://"
                    return i + 3;
                }
            }
        }
        return -1;
    }

    private static boolean isSchemeChar(char ch) {
        return (ch >= 'a' && ch <= 'z')
                || (ch >= 'A' && ch <= 'Z')
                || (ch >= '0' && ch <= '9')
                || ch == '+'
                || ch == '.'
                || ch == '-';
    }

    // Authority ends at '/', ';', '?', '#', '&', whitespace, or end of string.
    private static int findAuthorityEnd(String s, int start) {
        int i = start, n = s.length();
        for (; i < n; i++) {
            char ch = s.charAt(i);
            if (ch == '/' || ch == ';' || ch == '?' || ch == '#' || ch == '&' || Character.isWhitespace(ch)) {
                break;
            }
        }
        return i;
    }
}
