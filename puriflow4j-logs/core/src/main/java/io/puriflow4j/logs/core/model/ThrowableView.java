/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.core.model;

import java.util.List;

public record ThrowableView(
        String className,
        String message,
        List<StackFrameView> frames, // top-down frames
        ThrowableView cause // nullable
        ) {}
