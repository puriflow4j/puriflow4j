/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.core.sanitize;

import io.puriflow4j.core.api.Sanitizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MdcSanitizer {
    private final Sanitizer sanitizer;

    public MdcSanitizer(Sanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public Map<String, String> sanitize(Map<String, String> mdc, String logger) {
        if (mdc == null || mdc.isEmpty()) return mdc;
        var out = new LinkedHashMap<String, String>(mdc.size());
        for (var e : mdc.entrySet()) {
            String v = e.getValue();
            out.put(e.getKey(), (v == null || v.isEmpty()) ? v : sanitizer.apply(v, logger));
        }
        return Collections.unmodifiableMap(out);
    }
}
