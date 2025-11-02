package io.puriflow4j.spring

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.Finding
import spock.lang.Specification

class PuriflowEndpointSpec extends Specification {

    def "info returns OK status and empty recentFindings initially"() {
        given:
        def reporter = new MicrometerReporter(new SimpleMeterRegistry(), 10)
        def endpoint = new PuriflowEndpoint(reporter)

        when:
        def info = endpoint.info()

        then:
        info.get("status") == "OK"
        info.get("recentFindings") instanceof List
        (info.get("recentFindings") as List).isEmpty()
    }

    def "info reflects recent findings reported to MicrometerReporter"() {
        given:
        def reporter = new MicrometerReporter(new SimpleMeterRegistry(), 5)
        def endpoint = new PuriflowEndpoint(reporter)

        and: "some findings are reported"
        def f1 = new Finding("email", Action.MASK, 1, 5)
        def f2 = new Finding("ip",    Action.MASK, 10, 20)
        reporter.report([f1, f2])

        when:
        def info = endpoint.info()
        def recent = info.get("recentFindings") as List

        then: "endpoint exposes the same order as reporter's ring buffer"
        recent.size() == 2
        recent[0].type() == "email"
        recent[1].type() == "ip"
    }

    def "recentFindings returned by endpoint is immutable snapshot"() {
        given:
        def reporter = new MicrometerReporter(new SimpleMeterRegistry(), 3)
        def endpoint = new PuriflowEndpoint(reporter)
        reporter.report([new Finding("card", Action.MASK, 0, 4)])

        when:
        def snapshot = endpoint.info().get("recentFindings") as List
        snapshot.add(new Finding("iban", Action.MASK, 5, 9))

        then:
        thrown(UnsupportedOperationException)

        and: "reporter's internal buffer is unaffected"
        def after = reporter.recentFindings()
        after.size() == 1
        after[0].type() == "card"
    }
}
