/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.preset;

import java.util.*;

/**
 * @param allow store as normalized
 * @param block store as normalized
 */
public record KVPatternConfig(Set<String> allow, Set<String> block) {

    public static KVPatternConfig of(List<String> allowlist, List<String> blocklist) {
        return new KVPatternConfig(normalizeAll(allowlist), normalizeAll(blocklist));
    }

    public static KVPatternConfig defaults() {
        return of(
                List.of("traceId", "requestId", "correlationId"),
                List.of("password", "secret", "apikey", "token", "authorization"));
    }

    private static Set<String> normalizeAll(List<String> in) {
        Set<String> out = new HashSet<>();
        if (in != null) for (String k : in) if (k != null) out.add(normalizeKey(k));
        return out;
    }

    /**
     * Public so detectors can reuse the same normalization.
     */
    public static String normalizeKey(String k) {
        if (k == null) return "";
        // lower-case, remove separators -, _, and spaces
        return k.toLowerCase(Locale.ROOT).replaceAll("[-_\\s]", "");
    }

    public boolean isAllowedKey(String rawKey) {
        return allow.contains(normalizeKey(rawKey));
    }

    public boolean isBlockedKey(String rawKey) {
        return block.contains(normalizeKey(rawKey));
    }
}
