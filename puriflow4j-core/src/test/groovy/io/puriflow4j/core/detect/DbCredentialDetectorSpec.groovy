//package io.puriflow4j.core.detect
//
//import io.puriflow4j.core.api.model.DetectionResult
//import io.puriflow4j.core.preset.KVPatternConfig
//import spock.lang.Specification
//
///**
// * Tests for DbCredentialDetector:
// * - URI userinfo masking: scheme://user:pass@host, redis://:pass@host, scheme://user@host
// * - Oracle thin JDBC: jdbc:oracle:thin:user/pass@host
// * - Property-style masking: ;user=..., ;password=..., ?password=...
// * - Allowlist/blocklist behavior
// * - Ordering and non-overlap of spans
// * - Null/empty input handling
// * - Ensures it doesn't catch generic password outside DSN (left to PasswordKVDetector)
// */
//class DbCredentialDetectorSpec extends Specification {
//
//    /**
//     * Helper that renders a masked string using spans.
//     * We copy spans into a mutable list before sorting to avoid UnsupportedOperationException
//     * when the detector returns an immutable list.
//     */
//    private static String applySpans(String msg, DetectionResult res) {
//        def spans = new ArrayList<>(res.spans())
//        // sort by start asc, then by end desc
//        spans.sort { a, b -> (a.start() <=> b.start()) ?: (b.end() <=> a.end()) }
//
//        def sb = new StringBuilder(msg.length())
//        int pos = 0
//        for (def s : spans) {
//            if (s.start() > pos) sb.append(msg, pos, s.start())
//            sb.append(s.replacement())
//            pos = s.end()
//        }
//        if (pos < msg.length()) sb.append(msg, pos, msg.length())
//        return sb.toString()
//    }
//
//    def "masks user and password in generic URI userinfo scheme://user:pass@host"() {
//        given:
//        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
//        def msg = "jdbc:postgresql://alice:Sup3rP@ss@db.prod/acme"
//
//        when:
//        def res = det.detect(msg)
//
//        then:
//        res.found()
//        res.spans().size() == 2
//        applySpans(msg, res) == "jdbc:postgresql://[MASKED_USER]:[MASKED_PASSWORD]@db.prod/acme"
//    }
//
//    def "masks password in redis://:pass@host (no username present)"() {
//        given:
//        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
//        def msg = "redis://:secret123@cache.internal:6379/0"
//
//        when:
//        def res = det.detect(msg)
//
//        then:
//        res.found()
//        res.spans().size() == 1
//        applySpans(msg, res) == "redis://:[MASKED_PASSWORD]@cache.internal:6379/0"
//    }
//
//    def "masks only username when scheme://user@host (no password)"() {
//        given:
//        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
//        def msg = "neo4j://bob@graph.local:7687"
//
//        expect:
//        applySpans(msg, det.detect(msg)) == "neo4j://[MASKED_USER]@graph.local:7687"
//    }
//
//    def "Oracle thin jdbc:oracle:thin:user/pass@host is masked"() {
//        given:
//        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
//        def msg = "jdbc:oracle:thin:scott/tiger@ora.prod:1521/ORCL"
//
//        expect:
//        applySpans(msg, det.detect(msg)) == "jdbc:oracle:thin:[MASKED_USER]/[MASKED_PASSWORD]@ora.prod:1521/ORCL"
//    }
//
//    def "property-style user/password in DSN are masked; allowlist can leave user visible"() {
//        given:
//        // allowlist includes user keys -> username left as-is, password still masked
//        def kv = KVPatternConfig.of(["user", "username", "user id"], [])
//        def det = new DbCredentialDetector(kv)
//        def msg = "Server=db; Database=acme; User ID=alice; Password=MySecret; Encrypt=true"
//
//        when:
//        def res = det.detect(msg)
//
//        then:
//        res.found()
//        applySpans(msg, res) == "Server=db; Database=acme; User ID=alice; Password=[MASKED_PASSWORD]; Encrypt=true"
//    }
//
//    def "query param ?password=... is masked; blocklist overrides allowlist"() {
//        given:
//        // password is both allowed and blocked -> blocklist wins -> masked
//        def kv = KVPatternConfig.of(["password"], ["password"])
//        def det = new DbCredentialDetector(kv)
//        def msg = "jdbc:sqlserver://db;user=alice;password=P@ss123;encrypt=true"
//
//        expect:
//        applySpans(msg, det.detect(msg)) == "jdbc:sqlserver://db;user=alice;password=[MASKED_PASSWORD];encrypt=true"
//    }
//
//    def "multiple credentials in one line produce ordered, non-overlapping spans"() {
//        given:
//        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
//        def msg = "mysql://u1:p1@h1 db=foo;user=u2;password=p2 redis://:p3@h2/0"
//
//        when:
//        def res = det.detect(msg)
//
//        then:
//        res.found()
//
//        and: "ordered by start"
//        def ordered = new ArrayList<>(res.spans())
//        ordered.sort { a, b -> (a.start() <=> b.start()) ?: (b.end() <=> a.end()) }
//        ordered*.start() == ordered*.start().sort(false)
//
//        and: "no overlap"
//        for (int i = 1; i < ordered.size(); i++) {
//            assert ordered[i - 1].end() <= ordered[i].start()
//        }
//
//        and: "rendered as expected"
//        applySpans(msg, res) ==
//                "mysql://[MASKED_USER]:[MASKED_PASSWORD]@h1 db=foo;user=[MASKED_USER];password=[MASKED_PASSWORD] redis://:[MASKED_PASSWORD]@h2/0"
//    }
//
//    def "does not handle generic 'password=...' outside DSN (left for PasswordKVDetector)"() {
//        given:
//        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
//        def msg = "User login attempt: password=NotInDSN"
//
//        expect:
//        !det.detect(msg).found()
//    }
//
//    def "handles null and empty inputs safely"() {
//        given:
//        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
//
//        expect:
//        !det.detect(null).found()
//        !det.detect("").found()
//    }
//}
