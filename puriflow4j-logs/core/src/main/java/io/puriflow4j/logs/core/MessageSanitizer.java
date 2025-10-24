/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.core;

import io.puriflow4j.core.api.Sanitizer;

public final class MessageSanitizer {
    private final Sanitizer sanitizer;

    public MessageSanitizer(Sanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public String sanitize(String message, String loggerName) {
        return (message == null || message.isEmpty()) ? message : sanitizer.apply(message, loggerName);
    }
}
