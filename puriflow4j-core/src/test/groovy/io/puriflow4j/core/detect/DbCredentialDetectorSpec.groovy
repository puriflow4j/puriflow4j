package io.puriflow4j.core.detect

import io.puriflow4j.core.api.model.DetectionResult
import io.puriflow4j.core.preset.KVPatternConfig
import spock.lang.Specification

/**
 * Tests for DbCredentialDetector.
 *
 * Notes:
 *  - We render the sanitized string by applying spans right-to-left.
 *  - We never sort the immutable spans list directly (copy to a mutable list first).
 */
class DbCredentialDetectorSpec extends Specification {

    // ---------- Happy-path URI userinfo ----------

    def "masks user and password in generic URI userinfo scheme://user:pass@host"() {
        given:
        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
        def msg = "jdbc:postgresql://alice:Sup3rP@ss@db.prod/acme"

        when:
        def res = det.detect(msg)

        then:
        res.found()
        res.spans().size() == 2
        applySpans(msg, res) == "jdbc:postgresql://[MASKED_USER]:[MASKED_PASSWORD]@db.prod/acme"
    }

    def "masks only username when scheme://user@host (no password)"() {
        given:
        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
        def msg = "neo4j://bob@graph.local:7687"

        expect:
        applySpans(msg, det.detect(msg)) == "neo4j://[MASKED_USER]@graph.local:7687"
    }

    def "masks only password when redis form scheme://:password@host (no username)"() {
        given:
        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
        def msg = "redis://:secretPwd@localhost:6379/0"

        expect:
        applySpans(msg, det.detect(msg)) == "redis://:[MASKED_PASSWORD]@localhost:6379/0"
    }

    // ---------- Oracle thin ----------

    def "masks Oracle thin jdbc user/pass before '@'"() {
        given:
        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
        def msg = "jdbc:oracle:thin:user1/passW0rd@dbhost:1521/ORCL"

        expect:
        applySpans(msg, det.detect(msg)) == "jdbc:oracle:thin:[MASKED_USER]/[MASKED_PASSWORD]@dbhost:1521/ORCL"
    }

    def "masks Oracle thin jdbc username only if password is absent"() {
        given:
        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
        def msg = "jdbc:oracle:thin:onlyuser@dbhost:1521/ORCL"

        expect:
        applySpans(msg, det.detect(msg)) == "jdbc:oracle:thin:[MASKED_USER]@dbhost:1521/ORCL"
    }

    // ---------- Property-style (SQLServer/JDBC/ODBC-like) ----------

    def "masks property-style user and password in DSN"() {
        given:
        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
        def msg = "jdbc:sqlserver://db;user=alice;password=P@ss123;encrypt=true"

        expect:
        applySpans(msg, det.detect(msg)) == "jdbc:sqlserver://db;user=[MASKED_USER];password=[MASKED_PASSWORD];encrypt=true"
    }

    def "policy: allowlisted user remains visible, password masked; blocklist overrides allowlist for password"() {
        given:
        // allow 'user', block 'password' (block wins for password)
        def kv = KVPatternConfig.of(["user"], ["password"])
        def det = new DbCredentialDetector(kv)
        def msg = "jdbc:sqlserver://db;user=bob;password=TopSecret;app=demo"

        expect:
        applySpans(msg, det.detect(msg)) == "jdbc:sqlserver://db;user=bob;password=[MASKED_PASSWORD];app=demo"
    }

    def "query param ?password=... is masked; blocklist wins even if allowlisted"() {
        given:
        // 'password' is both allowed and blocked → masked
        def kv = KVPatternConfig.of(["password"], ["password"])
        def det = new DbCredentialDetector(kv)
        def msg = "postgres://db.example/app?ssl=true&password=LetMeIn"

        expect:
        applySpans(msg, det.detect(msg)) == "postgres://db.example/app?ssl=true&password=[MASKED_PASSWORD]"
    }

    // ---------- Multiple credentials & ordering ----------

    def "multiple credentials in one line produce ordered, non-overlapping spans"() {
        given:
        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
        def msg = "mysql://u1:p1@h1 db=foo;user=u2;password=p2 redis://:p3@h2/0"

        when:
        def res = det.detect(msg)

        then:
        res.found()

        and: "ordered by start (stable)"
        def ordered = new ArrayList<>(res.spans())
        ordered.sort { a, b -> (a.start() <=> b.start()) ?: (b.end() <=> a.end()) }
        ordered*.start() == ordered*.start().sort(false)

        and: "no overlapping spans"
        for (int i = 1; i < ordered.size(); i++) {
            assert ordered[i - 1].end() <= ordered[i].start()
        }

        and: "rendered as expected"
        applySpans(msg, res) ==
                "mysql://[MASKED_USER]:[MASKED_PASSWORD]@h1 db=foo;user=[MASKED_USER];password=[MASKED_PASSWORD] redis://:[MASKED_PASSWORD]@h2/0"
    }

    // ---------- Negative case delegated to PasswordKVDetector ----------

    def "does not handle generic 'password=...' outside DSN (left for PasswordKVDetector)"() {
        given:
        def det = new DbCredentialDetector(KVPatternConfig.of([], []))
        def msg = "User login attempt: password=NotInDSN"

        expect:
        !det.detect(msg).found()
    }

    // ---------- Helpers ----------

    /**
     * Apply spans right-to-left to avoid index shifting when replacing substrings.
     * Also sort by start asc, end desc to merge-like behavior across adjacent spans.
     */
    private static String applySpans(String s, DetectionResult res) {
        if (!res.found() || res.spans().isEmpty()) return s
        def spans = new ArrayList<>(res.spans()) // copy: original may be immutable
        spans.sort { a, b -> (a.start() <=> b.start()) ?: (b.end() <=> a.end()) }

        def out = new StringBuilder(s)
        for (int i = spans.size() - 1; i >= 0; i--) {
            def sp = spans[i]
            out.replace(sp.start(), sp.end(), sp.replacement())
        }
        return out.toString()
    }
}
