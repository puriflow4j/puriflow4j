/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.logback;

import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import io.puriflow4j.logs.core.model.StackFrameView;
import io.puriflow4j.logs.core.model.ThrowableView;
import java.util.ArrayList;
import java.util.List;

final class ThrowableViewAdapter {
    private ThrowableViewAdapter() {}

    static ThrowableView toView(IThrowableProxy tp) {
        if (tp == null) return null;
        List<StackFrameView> frames = new ArrayList<>();
        StackTraceElementProxy[] arr = tp.getStackTraceElementProxyArray();
        if (arr != null) {
            for (StackTraceElementProxy ep : arr) {
                var el = ep.getStackTraceElement();
                frames.add(new StackFrameView(
                        el.getClassName(), el.getMethodName(), el.getFileName(), el.getLineNumber()));
            }
        }
        return new ThrowableView(tp.getClassName(), tp.getMessage(), List.copyOf(frames), toView(tp.getCause()));
    }
}
