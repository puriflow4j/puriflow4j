/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.log4j2;

import io.puriflow4j.logs.core.model.StackFrameView;
import io.puriflow4j.logs.core.model.ThrowableView;

import java.util.ArrayList;
import java.util.List;

/** Converts java.lang.Throwable into our neutral ThrowableView model (same idea as Logback adapter). */
final class ThrowableViewAdapter {
    private ThrowableViewAdapter() {}

    static ThrowableView toView(Throwable t) {
        if (t == null) return null;
        List<StackFrameView> frames = new ArrayList<>();
        for (StackTraceElement el : t.getStackTrace()) {
            frames.add(new StackFrameView(
                    el.getClassName(), el.getMethodName(), el.getFileName(), el.getLineNumber()
            ));
        }
        return new ThrowableView(
                t.getClass().getName(),
                t.getMessage(),
                List.copyOf(frames),
                toView(t.getCause())
        );
    }
}
