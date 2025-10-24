//package io.puriflow4j.core
//
//import io.puriflow4j.core.api.*
//import io.puriflow4j.core.api.model.*
//import io.puriflow4j.core.preset.DetectorRegistry
//import io.puriflow4j.core.preset.KVPatternConfig
//import spock.lang.Specification
//
///**
// * Sanity/behavior tests for the Sanitizer with a realistic detector set.
// * Uses enum-based DetectorType and KV allow/block configuration.
// */
//class SanitizerSpec extends Specification {
//
//    private Sanitizer sanitizerMask() {
//        def kvCfg = KVPatternConfig.defaults()
//        def reg   = new DetectorRegistry()
//        def types = [
//                DetectorType.EMAIL,
//                DetectorType.JWT,
//                DetectorType.PASSWORD,
//                DetectorType.AWS_KEY,
//                DetectorType.AUTHORIZATION,
//                DetectorType.CREDIT_CARD
//        ] as List<DetectorType>
//        def detectors = reg.build(types, kvCfg)
//        new Sanitizer(detectors, Modes.actionFor(Mode.MASK))
//    }
//
//    private Sanitizer sanitizerDryRun() {
//        def kvCfg = KVPatternConfig.defaults()
//        def reg   = new DetectorRegistry()
//        def types = [
//                DetectorType.EMAIL,
//                DetectorType.JWT,
//                DetectorType.PASSWORD,
//                DetectorType.AWS_KEY,
//                DetectorType.AUTHORIZATION
//        ] as List<DetectorType>
//        def detectors = reg.build(types, kvCfg)
//        new Sanitizer(detectors, Modes.actionFor(Mode.DRY_RUN))
//    }
//
//    def "masks email, aws key, jwt and password (KV) in one line"() {
//        given:
//        def s = sanitizerMask()
//        def inMsg = "User email: alice@example.com, awsKey: AKIA1234567890ABCDE1, token: eyJ.hdr.pay.sig, password=MySecret123"
//
//        when:
//        def res = s.applyDetailed(inMsg, "test.logger")
//
//        then:
//        res.sanitized() ==
//                "User email: [MASKED_EMAIL], awsKey: [MASKED_AWS_KEY], token: [MASKED_JWT], password=[MASKED]"
//        res.findings()*.type as Set == ["email","awsKey","jwt","password"] as Set
//    }
//
//    def "dry-run mode does not change the message but reports findings"() {
//        given:
//        def s = sanitizerDryRun()
//        def inMsg = "email=bob@example.com auth token: eyJ.hdr.pay.sig"
//
//        when:
//        def res = s.applyDetailed(inMsg, "test.logger")
//
//        then:
//        res.sanitized() == inMsg
//        !res.findings().isEmpty()
//        res.findings().any { it.type() == "email" }
//        res.findings().any { it.type() == "jwt" }
//        res.findings().every { it.action() == Action.WARN }
//    }
//
//    def "authorization bearer KV masks only the token substring"() {
//        given:
//        def s = sanitizerMask()
//        def token = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ._-"
//        def inMsg = "Authorization: Bearer ${token}"
//
//        when:
//        def res = s.applyDetailed(inMsg, "http.header")
//
//        then:
//        res.sanitized() == "Authorization: Bearer [MASKED_JWT]"
//        res.findings().size() == 1
//        res.findings()[0].type() == "authorization"
//    }
//
//    def "credit card detector masks valid Luhn numbers but ignores random sequences"() {
//        given:
//        def s = sanitizerMask()
//        // Valid VISA (Luhn OK)
//        def validCard = "4539 1488 0343 6467"
//        // Fails Luhn
//        def invalidCard = "1234 5678 9012 3456"
//
//        expect:
//        s.apply("Card: ${validCard}") == "Card: [MASKED_CARD]"
//        s.apply("Card: ${invalidCard}") == "Card: ${invalidCard}"
//    }
//
//    def "overlap handling: jwt value matched by KV and bare is replaced once"() {
//        given:
//        def s = sanitizerMask()
//        def value = "eyJ.hdr.pay.sig"
//        def inMsg = "token=${value}"
//
//        when:
//        def res = s.applyDetailed(inMsg, "auth")
//
//        then:
//        res.sanitized() == "token=[MASKED_JWT]"
//        // Even if two detectors hit the same region, result should remain stable.
//        res.findings().count { it.type() == "jwt" } >= 1
//    }
//
//    def "does not mask allowed keys from allowlist (traceId/requestId/correlationId)"() {
//        given:
//        // Build sanitizer with the same detectors but ensure allowlist in effect
//        def kvCfg = KVPatternConfig.of(
//                ["traceId","requestId","correlationId"],
//                ["password","secret","apiKey","token","authorization"]
//        )
//        def reg = new DetectorRegistry()
//        def detectors = reg.build([DetectorType.PASSWORD, DetectorType.JWT] as List<DetectorType>, kvCfg)
//        def s = new Sanitizer(detectors, Modes.actionFor(Mode.MASK))
//
//        when:
//        def res = s.applyDetailed("traceId=abc-123 token=eyJ.hdr.pay.sig", "svc")
//
//        then:
//        // traceId value must be preserved, JWT must be masked
//        res.sanitized() == "traceId=abc-123 token=[MASKED_JWT]"
//    }
//}
