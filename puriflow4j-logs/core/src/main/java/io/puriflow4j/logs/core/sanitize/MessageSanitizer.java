/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.core.sanitize;

import io.puriflow4j.core.api.Sanitizer;

public class MessageSanitizer {
    protected final Sanitizer sanitizer;

    public MessageSanitizer(Sanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public String sanitize(String message, String loggerName) {
        return (message == null || message.isEmpty()) ? message : sanitizer.apply(message, loggerName);
    }

    public Sanitizer.Result applyDetailed(String message, String loggerName) {
        return sanitizer.applyDetailed(message, loggerName);
    }
}
