package io.puriflow4j.logs.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.LoggerContextVO
import org.slf4j.Marker
import org.slf4j.event.KeyValuePair
import spock.lang.Specification

/**
 * Behavioral tests for SanitizedLoggingEvent.
 * Verifies (1) overridden fields (message/MDC/throwable) and
 * (2) pure delegation of remaining properties/methods.
 */
class SanitizedLoggingEventSpec extends Specification {

    // Creates a stubbed delegate with deterministic values for delegation tests
    private ILoggingEvent mkDelegate(Map<String, String> delegateMdc = [orig: "mdc"]) {
        def delegate = Mock(ILoggingEvent)
        _ * delegate.getThreadName() >> "t-main"
        _ * delegate.getLevel() >> Level.INFO
        _ * delegate.getLoggerName() >> "demo.Logger"
        _ * delegate.getLoggerContextVO() >> new LoggerContextVO("ctx", [:], 123)
        _ * delegate.getMarker() >> null
        _ * delegate.getMarkerList() >> (List<Marker>) Collections.emptyList()
        _ * delegate.getTimeStamp() >> 42L
        _ * delegate.getNanoseconds() >> 7
        _ * delegate.getSequenceNumber() >> 99L
        _ * delegate.getKeyValuePairs() >> (List<KeyValuePair>) Collections.emptyList()
        _ * delegate.getCallerData() >> new StackTraceElement[0]
        _ * delegate.hasCallerData() >> false
        _ * delegate.getMDCPropertyMap() >> delegateMdc
        // formatted message of the *delegate* should not leak; we still stub it for completeness
        _ * delegate.getFormattedMessage() >> "ORIGINAL"
        _ * delegate.getMessage() >> "ORIGINAL"
        _ * delegate.prepareForDeferredProcessing() >> { }
        delegate
    }

    def "overrides message, MDC and throwable"() {
        given:
        def delegate = mkDelegate([orig: "mdc"])
        def mdc = [a: "b"]
        def thr = Mock(IThrowableProxy)

        when:
        def ev = new SanitizedLoggingEvent(delegate, "sanitized", mdc, thr)

        then: "overridden fields"
        ev.getFormattedMessage() == "sanitized"
        ev.getMessage() == "sanitized"
        ev.getMDCPropertyMap() == mdc
        ev.getMdc() == mdc
        ev.getThrowableProxy().is(thr)

        and: "delegated fields"
        ev.getThreadName() == "t-main"
        ev.getLevel() == Level.INFO
        ev.getLoggerName() == "demo.Logger"
        ev.getLoggerContextVO().getName() == "ctx"
        ev.getMarker() == null
        ev.getMarkerList().isEmpty()
        ev.getTimeStamp() == 42L
        ev.getNanoseconds() == 7
        ev.getSequenceNumber() == 99L
        ev.getKeyValuePairs().isEmpty()
        ev.getCallerData().length == 0
        !ev.hasCallerData()

        and: "argument array is always null by design"
        ev.getArgumentArray() == null
    }

    def "when new MDC is null, delegate MDC is used"() {
        given:
        def delegate = mkDelegate([k: "v"])

        when:
        def ev = new SanitizedLoggingEvent(delegate, "msg", null, null)

        then:
        ev.getMDCPropertyMap() == [k: "v"]
        ev.getMdc() == [k: "v"]
    }

    def "prepareForDeferredProcessing is delegated"() {
        given:
        def delegate = mkDelegate()
        def ev = new SanitizedLoggingEvent(delegate, "x", [m: "d"], null)

        when:
        ev.prepareForDeferredProcessing()

        then:
        1 * delegate.prepareForDeferredProcessing()
    }

    def "constructor requires a non-null delegate"() {
        when:
        new SanitizedLoggingEvent(null, "x", [:], null)

        then:
        def ex = thrown(NullPointerException)
        ex.message.contains("delegate")
    }

    def "formattedMessage and message are identical (both overridden)"() {
        given:
        def delegate = mkDelegate()
        def ev = new SanitizedLoggingEvent(delegate, "same", [:], null)

        expect:
        ev.getFormattedMessage() == "same"
        ev.getMessage() == "same"
    }
}
