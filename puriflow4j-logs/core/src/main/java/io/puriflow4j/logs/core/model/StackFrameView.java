/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.core.model;

public record StackFrameView(String className, String methodName, String fileName, int line) {
    public String pretty() {
        String f = (fileName == null ? "Unknown Source" : (line >= 0 ? fileName + ":" + line : fileName));
        return className + "." + methodName + "(" + f + ")";
    }
}
