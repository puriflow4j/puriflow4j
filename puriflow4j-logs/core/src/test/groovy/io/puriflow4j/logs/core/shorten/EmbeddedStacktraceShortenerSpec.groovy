package io.puriflow4j.logs.core.shorten

import io.puriflow4j.core.api.Detector
import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.DetectionResult
import spock.lang.Specification

import java.util.regex.Pattern

class EmbeddedStacktraceShortenerSpec extends Specification {

    // Real Sanitizer to avoid mocking finals.
    // Masks only the VALUE (group 2) for password|secret in key=value.
    private static Sanitizer mkSanitizer() {
        Detector det = new Detector() {
            private final Pattern P = Pattern.compile("(?i)\\b(password|secret)\\s*[:=]\\s*([^\\s,;]+)")
            @Override
            DetectionResult detect(String s) {
                if (s == null || s.isEmpty()) return DetectionResult.empty()
                def m = P.matcher(s)
                def spans = new ArrayList<DetectionResult.Span>()
                while (m.find()) {
                    spans.add(new DetectionResult.Span(m.start(2), m.end(2), "kv", "[MASKED]"))
                }
                return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans))
            }
        }
        new Sanitizer(List.of(det), Action.MASK)
    }

    // Helper: compare strings in a platform-agnostic way (normalize newlines)
    private static String norm(String s) {
        return s == null ? null : s.replaceAll("\\R", "\n")
    }

    def "returns original when no embedded stack trace is present"() {
        given:
        def san = mkSanitizer()
        def shortener = new EmbeddedStacktraceShortener(san, 5, List.of("java.", "org.springframework"))

        expect:
        shortener.shorten("nothing to shorten here", "demo") == "nothing to shorten here"
        shortener.shorten("", "demo") == ""
        shortener.shorten(null, "demo") == null
    }

    def "masks header lines and 'Caused by:' while preserving frames"() {
        given:
        def san = mkSanitizer()
        def shortener = new EmbeddedStacktraceShortener(san, 10, List.of())

        // Header contains sensitive key=value; Caused by contains secret=...
        def msg = [
                "java.lang.RuntimeException: password=abc",
                "    at app.Service.handle(Service.java:10)",
                "    at app.Controller.route(Controller.java:20)",
                "Caused by: java.sql.SQLException: secret=top",
                "    at app.Repo.exec(Repo.java:30)"
        ].join("\n")

        when:
        def out = shortener.shorten(msg, "demo.Logger")

        then:
        norm(out) == norm([
                "java.lang.RuntimeException: password=[MASKED]",
                "    at app.Service.handle(Service.java:10)",
                "    at app.Controller.route(Controller.java:20)",
                "Caused by: java.sql.SQLException: secret=[MASKED]",
                "    at app.Repo.exec(Repo.java:30)"
        ].join("\n"))
    }

    def "respects maxDepth and hidePackages; counts and prints omitted frames"() {
        given:
        def san = mkSanitizer()
        def shortener = new EmbeddedStacktraceShortener(san, /*maxDepth*/ 2, List.of("org.springframework", "java."))

        def msg = [
                "boom: password=secret123",
                "    at app.Service.a(Service.java:10)",                 // keep #1
                "    at org.springframework.Bean.x(Bean.java:11)",       // hidden → omitted
                "    at java.util.List.get(List.java:12)",               // hidden → omitted
                "    at app.Controller.b(Controller.java:13)",           // keep #2
                "    at app.Repo.c(Repo.java:14)"                        // over depth → omitted
        ].join("\n")

        when:
        def out = shortener.shorten(msg, "demo.Logger")

        then:
        norm(out) == norm([
                "boom: password=[MASKED]",
                "    at app.Service.a(Service.java:10)",
                "    at app.Controller.b(Controller.java:13)",
                " (3 framework frames omitted)"
        ].join("\n"))
    }

    def "drops non-frame trailing lines (e.g. Suppressed: ...) and reports omissions"() {
        given:
        def san = mkSanitizer()
        def shortener = new EmbeddedStacktraceShortener(san, 1, List.of())

        def msg = [
                "X: secret=val",
                "    at app.Foo.bar(Foo.java:1)",     // keep (maxDepth=1)
                "Suppressed: something extra",        // not a frame → omitted
                "... 23 more"                          // not a frame → omitted
        ].join("\n")

        when:
        def out = shortener.shorten(msg, "demo")

        then:
        norm(out) == norm([
                "X: secret=[MASKED]",
                "    at app.Foo.bar(Foo.java:1)",
                " (2 framework frames omitted)"
        ].join("\n"))
    }

    def "handles multiple header lines before first frame (each header sanitized)"() {
        given:
        def san = mkSanitizer()
        def shortener = new EmbeddedStacktraceShortener(san, 2, List.of())

        def msg = [
                "Top line password=abc",
                "Second header: secret=zzz",
                "    at app.Main.run(Main.java:1)",
                "    at app.Next.go(Next.java:2)"
        ].join("\n")

        when:
        def out = shortener.shorten(msg, "demo")

        then:
        norm(out) == norm([
                "Top line password=[MASKED]",
                "Second header: secret=[MASKED]",
                "    at app.Main.run(Main.java:1)",
                "    at app.Next.go(Next.java:2)"
        ].join("\n"))
    }
}
