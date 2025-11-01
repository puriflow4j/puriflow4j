package io.puriflow4j.core.preset

import spock.lang.Specification

/**
 * Tests for KVPatternConfig:
 * - key normalization (lower-case, strip -, _, spaces)
 * - defaults content
 * - allow/block membership with various key variants
 * - null/empty inputs
 * - deduplication after normalization
 */
class KVPatternConfigSpec extends Specification {

    def "normalizeKey removes dashes/underscores/spaces and lowercases"() {
        expect:
        KVPatternConfig.normalizeKey(input) == expected

        where:
        input                  || expected
        "X-AUTH-TOKEN"         || "xauthtoken"
        "x_auth_token"         || "xauthtoken"
        "X Auth Token"         || "xauthtoken"
        "  Api_Key  "          || "apikey"       // spaces are stripped by regex replaceAll (including inner spaces)
        "Authorization"        || "authorization"
        "traceId"              || "traceid"
        null                   || ""             // contract in implementation
    }

    def "defaults contain expected allow and block keys (normalized)"() {
        when:
        def cfg = KVPatternConfig.defaults()

        then:
        cfg.allow().containsAll(["traceid","requestid","correlationid"] as Set)
        cfg.block().containsAll(["password","secret","apikey","token","authorization"] as Set)
    }

    def "of() gracefully handles null/empty lists"() {
        when:
        def cfg1 = KVPatternConfig.of(null, null)
        def cfg2 = KVPatternConfig.of([], [])

        then:
        cfg1.allow().isEmpty()
        cfg1.block().isEmpty()
        cfg2.allow().isEmpty()
        cfg2.block().isEmpty()
    }

    def "isAllowedKey is true for any stylistic variant of the same key"() {
        given:
        def cfg = KVPatternConfig.of(["Trace-Id"], [])

        expect:
        cfg.isAllowedKey("traceId")
        cfg.isAllowedKey("TRACE_ID")
        cfg.isAllowedKey("Trace Id")
        cfg.isAllowedKey("trace-id")
        cfg.isAllowedKey("trace_id")
    }

    def "isBlockedKey is true for any stylistic variant of the same key"() {
        given:
        def cfg = KVPatternConfig.of([], ["X-AUTH-TOKEN"])

        expect:
        cfg.isBlockedKey("x-auth-token")
        cfg.isBlockedKey("X_AUTH_TOKEN")
        cfg.isBlockedKey("X Auth Token")
        cfg.isBlockedKey("xauthtoken")
    }

    def "deduplication after normalization: different forms end up as one entry"() {
        when:
        def cfg = KVPatternConfig.of(
                ["TraceId", "trace-id", "trace_id", "TRACE ID"],
                ["ApiKey", "API_KEY", "api key", "api-key"]
        )

        then:
        cfg.allow().size() == 1
        cfg.allow().contains("traceid")

        cfg.block().size() == 1
        cfg.block().contains("apikey")
    }

    def "membership checks are independent: key can be both allowed and blocked; downstream policy decides"() {
        given:
        // Current implementation allows intersection; resolution (block wins) is handled by detectors/policy.
        def cfg = KVPatternConfig.of(["token"], ["token"])

        expect:
        cfg.isAllowedKey("ToKeN")
        cfg.isBlockedKey("token")
    }
}