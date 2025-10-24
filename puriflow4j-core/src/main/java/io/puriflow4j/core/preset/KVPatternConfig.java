/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.preset;

import java.util.*;
import java.util.stream.Collectors;

public record KVPatternConfig(Set<String> allowKeys, Set<String> blockKeys) {
    public static KVPatternConfig of(List<String> allow, List<String> block) {
        return new KVPatternConfig(toSet(allow), toSet(block));
    }

    public static KVPatternConfig defaults() {
        return new KVPatternConfig(Set.of(), Set.of());
    }

    private static Set<String> toSet(List<String> in) {
        if (in == null) return Set.of();
        return in.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAllowedKey(String key) {
        return allowKeys.contains(lc(key));
    }

    public boolean isBlockedKey(String key) {
        return blockKeys.contains(lc(key));
    }

    private static String lc(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
