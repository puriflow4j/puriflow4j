package io.puriflow4j.core.preset;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Holds allowlist/blocklist for KV keys to reduce false positives.
 */
public final class KVPatternConfig {
    private final Set<String> allow;
    private final Set<String> block;

    private KVPatternConfig(Set<String> allow, Set<String> block) {
        this.allow = allow; this.block = block;
    }

    public static KVPatternConfig of(List<String> allow, List<String> block) {
        return new KVPatternConfig(
                normalize(allow),
                normalize(block == null || block.isEmpty() ? defaultsBlock() : block)
        );
    }

    public static KVPatternConfig defaults() {
        return of(defaultsAllow(), defaultsBlock());
    }

    public boolean isKeyAllowed(String key) {
        if (key == null || key.isBlank()) return false;
        return allow.contains(norm(key));
    }

    public boolean isKeyBlocked(String key) {
        if (key == null || key.isBlank()) return true; // if unknown, be conservative
        return block.contains(norm(key));
    }

    private static Set<String> normalize(List<String> keys) {
        return keys == null ? Set.of() :
                keys.stream().filter(Objects::nonNull).map(KVPatternConfig::norm).collect(Collectors.toSet());
    }

    private static String norm(String s) { return s.toLowerCase(Locale.ROOT).trim(); }

    private static List<String> defaultsAllow() {
        return List.of("traceid","requestid","correlationid");
    }

    private static List<String> defaultsBlock() {
        return List.of("password","pwd","passphrase","secret","apikey","api-key","api_key",
                "token","bearer-token","auth-token","authorization");
    }
}
