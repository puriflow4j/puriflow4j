/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.core.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;

@SuppressFBWarnings
public record ThrowableView(
        String className,
        String message,
        List<StackFrameView> frames, // top-down frames
        ThrowableView cause // nullable
        ) {}
