/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

/**
 * Lightweight wrapper over a real Logback event.
 * It overrides message/MDC/throwable parts while delegating the rest.
 */
final class SanitizedLoggingEvent implements ILoggingEvent {

    private final ILoggingEvent delegate;
    private final String message;
    private final Map<String, String> mdc;
    private final IThrowableProxy throwable; // this may be null intentionally

    SanitizedLoggingEvent(
            ILoggingEvent delegate, String newMessage, Map<String, String> newMdc, IThrowableProxy newThrowable) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.message = newMessage;
        this.mdc = (newMdc != null ? newMdc : delegate.getMDCPropertyMap());
        this.throwable = newThrowable;
    }

    // --- overridden parts
    @Override
    public String getFormattedMessage() {
        return message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Map<String, String> getMDCPropertyMap() {
        return mdc;
    }

    @Override
    public Map<String, String> getMdc() {
        return mdc;
    }

    @Override
    public IThrowableProxy getThrowableProxy() {
        return throwable;
    }

    // --- pure delegation for the rest
    @Override
    public String getThreadName() {
        return delegate.getThreadName();
    }

    @Override
    public Level getLevel() {
        return delegate.getLevel();
    }

    @Override
    public String getLoggerName() {
        return delegate.getLoggerName();
    }

    @Override
    public LoggerContextVO getLoggerContextVO() {
        return delegate.getLoggerContextVO();
    }

    @Override
    public Marker getMarker() {
        return delegate.getMarker();
    }

    @Override
    public List<Marker> getMarkerList() {
        return delegate.getMarkerList();
    }

    @Override
    public long getTimeStamp() {
        return delegate.getTimeStamp();
    }

    @Override
    public int getNanoseconds() {
        return delegate.getNanoseconds();
    }

    @Override
    public long getSequenceNumber() {
        return delegate.getSequenceNumber();
    }

    @Override
    public List<KeyValuePair> getKeyValuePairs() {
        return delegate.getKeyValuePairs();
    }

    @Override
    public StackTraceElement[] getCallerData() {
        return delegate.getCallerData();
    }

    @Override
    public boolean hasCallerData() {
        return delegate.hasCallerData();
    }

    @Override
    public Object[] getArgumentArray() {
        return null;
    }

    @Override
    public void prepareForDeferredProcessing() {
        delegate.prepareForDeferredProcessing();
    }
}
