package io.puriflow4j.logs.core.shorten

import io.puriflow4j.core.api.Detector
import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.DetectionResult
import io.puriflow4j.logs.core.model.StackFrameView
import io.puriflow4j.logs.core.model.ThrowableView
import spock.lang.Specification

import java.util.regex.Pattern

class ExceptionShortenerSpec extends Specification {

    // --- helpers -------------------------------------------------------------

    /** Real sanitizer that only masks the VALUE of password|secret in 'key=value'. */
    private static Sanitizer mkSanitizer() {
        Detector det = new Detector() {
            private final Pattern P = Pattern.compile("(?i)\\b(password|secret)\\s*[:=]\\s*([^\\s,;]+)")
            @Override
            DetectionResult detect(String s) {
                if (s == null || s.isEmpty()) return DetectionResult.empty()
                def m = P.matcher(s)
                def spans = new ArrayList<DetectionResult.Span>()
                while (m.find()) spans.add(new DetectionResult.Span(m.start(2), m.end(2), "kv", "[MASKED]"))
                return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans))
            }
        }
        new Sanitizer(List.of(det), Action.MASK)
    }

    private static StackFrameView f(String cls, String m, String file, int line) {
        new StackFrameView(cls, m, file, line)
    }

    private static ThrowableView tv(String cls, String msg, List<StackFrameView> frames = List.of(), ThrowableView cause = null) {
        new ThrowableView(cls, msg, frames, cause)
    }

    private static String norm(String s) {
        s == null ? null : s.replaceAll("\\R", "\n")
    }

    // --- tests ---------------------------------------------------------------

    def "compact: category label + [Masked] only when message changed; frames filtered by hidePackages and maxDepth; single-line cause"() {
        given:
        def san = mkSanitizer()
        // maxDepth=2; hide common libs
        def shortener = new ExceptionShortener(san, true, 2, List.of("java.", "org.springframework"))

        // main throwable has message with password -> should be masked
        def frames = [
                f("app.Service", "a", "Service.java", 10),            // keep #1
                f("org.springframework.Bean", "x", "Bean.java", 11),  // hidden
                f("app.Controller", "b", "Controller.java", 12),      // keep #2
                f("app.Repo", "c", "Repo.java", 13)                   // over depth -> omitted
        ]
        // cause has secret -> should be masked; its frames should be ignored (compact uses single-line cause)
        def cause = tv("java.sql.SQLException", "secret=top", List.of(
                f("app.Repo", "c", "Repo.java", 99)
        ), null)

        def t = tv("java.lang.RuntimeException", "password=abc", frames, cause)

        when:
        def out = shortener.format(t, "demo.Logger", "DB")

        then:
        def lines = norm(out).split("\n", -1) as List

        // header: [DB] + [Masked] + simple class name + masked message
        assert lines[0].startsWith("[DB] [Masked] RuntimeException: password=[MASKED]")

        // frames: exactly 2 printed (non-hidden, within maxDepth)
        assert lines[1] == " → ${frames[0].pretty()}"
        assert lines[2] == " → ${frames[2].pretty()}"

        // omitted counter: 2 (one hidden + one over-depth)
        assert lines[3] == " (2 framework frames omitted)"

        // cause: single line, masked message, simple class name
        assert lines[4] == " Caused by: [Masked] SQLException: secret=[MASKED]"
    }

    def "compact: no [Masked] tag when message didn't change; still prints class and frames"() {
        given:
        def san = mkSanitizer()
        def shortener = new ExceptionShortener(san, true, 1, List.of())

        def t = tv("com.acme.Error", "plain", List.of(
                f("app.Main", "run", "Main.java", 1),
                f("app.More", "go", "More.java", 2)
        ), null)

        when:
        def out = shortener.format(t, "demo", "HTTP")

        then:
        def lines = norm(out).split("\n", -1) as List
        // header: category but NO [Masked] because "plain" doesn't match sanitizer
        assert lines[0] == "[HTTP] Error: plain"
        // only 1 frame due to maxDepth
        assert lines[1] == " → app.Main.run(Main.java:1)"
        // one omitted
        assert lines[2] == " (1 framework frames omitted)"
        // no cause line
        assert lines.size() == 3
    }

    def "full: prints all frames and full recursive cause chain; masks only messages; category present when provided"() {
        given:
        def san = mkSanitizer()
        def shortener = new ExceptionShortener(san, false, 5, List.of("java.")) // shorten=false => full

        def inner = tv("com.db.DriverException", "secret=val", List.of(
                f("db.Driver", "exec", "Driver.java", 77)
        ), null)

        def outer = tv("com.app.Boom", "password=abc", List.of(
                f("app.Svc", "call", "Svc.java", 10),
                f("app.Api", "route", "Api.java", 20)
        ), inner)

        when:
        def out = shortener.format(outer, "svcLogger", "DB")

        then:
        def lines = norm(out).split("\n", -1) as List

        // header includes category and [Masked] because message changed
        assert lines[0] == "[DB] [Masked] Boom: password=[MASKED]"
        // all frames printed with "\tat "
        assert lines[1] == "\tat app.Svc.call(Svc.java:10)"
        assert lines[2] == "\tat app.Api.route(Api.java:20)"

        // cause block printed fully and recursively, with [Masked] if message changed
        assert lines[3] == "Caused by: [Masked] DriverException: secret=[MASKED]"
        assert lines[4] == "\tat db.Driver.exec(Driver.java:77)"
        assert lines.size() == 5
    }

    def "full: no [Masked] prefix when message didn't change; still prints frames and cause chain"() {
        given:
        def san = mkSanitizer()
        def shortener = new ExceptionShortener(san, false, 5, List.of())

        def cause = tv("x.y.Cause", "nothing", List.of(
                f("x.y.C1", "m1", "C1.java", 1)
        ), null)

        def t = tv("x.y.Err", "ok", List.of(
                f("x.y.Z", "z", "Z.java", 9)
        ), cause)

        when:
        def out = shortener.format(t, "logger", null)

        then:
        def lines = norm(out).split("\n", -1) as List
        assert lines[0] == "Err: ok"                        // no category, no [Masked]
        assert lines[1] == "\tat x.y.Z.z(Z.java:9)"
        assert lines[2] == "Caused by: Cause: nothing"      // cause message unchanged => no [Masked]
        assert lines[3] == "\tat x.y.C1.m1(C1.java:1)"
        assert lines.size() == 4
    }

    def "null throwable returns null; empty message is allowed"() {
        given:
        def shortener = new ExceptionShortener(mkSanitizer(), true, 2, List.of())

        expect:
        shortener.format(null as ThrowableView, "demo") == null

        when:
        def out = shortener.format(tv("A.B", "", List.of(), null), "demo", "X")

        then:
        // no message part after class name
        out == "[X] B"
    }
}
