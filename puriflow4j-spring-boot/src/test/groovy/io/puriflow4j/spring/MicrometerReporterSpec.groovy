package io.puriflow4j.spring

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.Finding
import spock.lang.Specification

class MicrometerReporterSpec extends Specification {

    def "increments counters and respects minimum ring capacity (10)"() {
        given:
        def registry = new SimpleMeterRegistry()
        // Passing 3 still results in capacity = 10 by design
        def reporter = new MicrometerReporter(registry, 3)

        def fEmail1 = new Finding("email", Action.MASK, 0, 5)
        def fEmail2 = new Finding("email", Action.MASK, 10, 20)
        def fCard   = new Finding("card",  Action.MASK, 30, 40)
        def fIban   = new Finding("iban",  Action.MASK, 50, 60)
        def fIp     = new Finding("ip",    Action.MASK, 70, 80)

        when:
        reporter.report([fEmail1, fEmail2])  // 2 emails
        reporter.report([fCard])             // 1 card
        reporter.report([fIban])             // 1 iban
        reporter.report([fIp])               // 1 ip

        then: "counters reflect totals per type"
        registry.counter("puriflow4j_pii_detected_total", "type", "email").count() == 2d
        registry.counter("puriflow4j_pii_detected_total", "type", "card").count()  == 1d
        registry.counter("puriflow4j_pii_detected_total", "type", "iban").count()  == 1d
        registry.counter("puriflow4j_pii_detected_total", "type", "ip").count()    == 1d

        and: "ring contains all 5 since min capacity is 10 (no eviction yet)"
        def snapshot = reporter.recentFindings()
        snapshot*.type() == ["email", "email", "card", "iban", "ip"]
    }

    def "evicts oldest when exceeding the minimum capacity (10)"() {
        given:
        def registry = new SimpleMeterRegistry()
        // Any value <= 10 becomes 10 internally; we’ll push 12 to force eviction
        def reporter = new MicrometerReporter(registry, 1)

        when:
        // Insert 12 items; only last 10 should remain
        (1..12).each { i ->
            reporter.report([new Finding("t${i}", Action.MASK, i, i)])
        }

        then:
        def last = reporter.recentFindings()
        last.size() == 10
        // Should keep t3..t12 (t1 and t2 evicted)
        last*.type() == (3..12).collect { "t$it" }
    }

    def "recentFindings returns unmodifiable snapshot"() {
        given:
        def registry = new SimpleMeterRegistry()
        def reporter = new MicrometerReporter(registry, 2)
        reporter.report([new Finding("email", Action.MASK, 0, 1)])

        when:
        reporter.recentFindings().add(new Finding("card", Action.MASK, 2, 3))

        then:
        thrown(UnsupportedOperationException)
    }

    def "null or empty report is a no-op"() {
        given:
        def registry = new SimpleMeterRegistry()
        def reporter = new MicrometerReporter(registry, 2)

        when:
        reporter.report(null)
        reporter.report([])

        then:
        registry.find("puriflow4j_pii_detected_total").counters().isEmpty()
        reporter.recentFindings().isEmpty()
    }
}
