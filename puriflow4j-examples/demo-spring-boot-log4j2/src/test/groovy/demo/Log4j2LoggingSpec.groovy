package demo

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.test.appender.ListAppender

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import spock.lang.Specification

/**
 * Integration tests for Log4j2 path using built-in test ListAppender.
 * We assert the final, sanitized strings as written to MEM appender after Puriflow rewrite.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = [
                "logging.config=classpath:log4j2-test.xml",

                "puriflow4j.enabled=true",
                "puriflow4j.mode=mask",

                "puriflow4j.detectors[0]=token_bearer",
                "puriflow4j.detectors[1]=cloud_access_key",
                "puriflow4j.detectors[2]=api_token_well_known",
                "puriflow4j.detectors[3]=basic_auth",
                "puriflow4j.detectors[4]=db_credential",
                "puriflow4j.detectors[5]=url_redactor",
                "puriflow4j.detectors[6]=private_key",
                "puriflow4j.detectors[7]=credit_card",
                "puriflow4j.detectors[8]=email",
                "puriflow4j.detectors[9]=password_kv",
                "puriflow4j.detectors[10]=iban",
                "puriflow4j.detectors[11]=ip",

                "puriflow4j.logs.enabled=true",
                "puriflow4j.logs.errors.categorize=true"
        ],
        classes = [
                TestApp,
                DemoController
        ]
)
class Log4j2LoggingSpec extends Specification {

    @SpringBootApplication
    static class TestApp {}

    @LocalServerPort int port
    @Autowired TestRestTemplate rest

    private ListAppender mem

    // ---------- helpers ----------

    // Read current Log4j2 configuration
    private static Configuration cfg() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false)
        assert ctx != null : "Log4j2 LoggerContext is null"
        return ctx.getConfiguration()
    }

    // Obtain our MEM test appender (exists even after Puriflow wraps logger refs)
    private static ListAppender memAppender() {
        def app = cfg().getAppenders().get("MEM")
        assert app instanceof ListAppender : "Appender 'MEM' not found or not a ListAppender. Check log4j2-test.xml and test dependency ':tests'."
        return (ListAppender) app
    }

    private static List<String> allLines() {
        return memAppender().events.collect { LogEvent e -> e.message.formattedMessage }
    }

    private static String joined() { allLines().join("\n") }
    private static String lastLine() { def l = allLines(); l.isEmpty() ? "" : l.last() }

    private String url(String path) { "http://localhost:$port$path" }

    // ---------- lifecycle ----------

    def setup() {
        println "log4j2-test.xml = ${getClass().getResource('/log4j2-test.xml')}"
        mem = memAppender()
        mem.clear() // start from clean buffer
    }

    def cleanup() {
        mem?.clear()
    }

    // ---------- tests (same endpoints & expectations as logback spec) ----------

    def "GET /log/message -> exact masked message string"() {
        when:
        def resp = rest.getForEntity(url("/log/message"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        lastLine() == "Login attempt: email=[MASKED_EMAIL], Authorization: Bearer [MASKED_TOKEN], awsKey=[MASKED_ACCESS_KEY], x-auth-token=lol13"
    }

    // Comment (EN): Verifies MDC (ThreadContext) is sanitized: 'token' is masked and 'traceId' is preserved.
    def "GET /log/message -> MDC is sanitized (token masked, traceId preserved)"() {
        when:
        def resp = rest.getForEntity(url("/log/message"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and: "inspect last LogEvent context data (MDC)"
        def events = mem.events
        assert !events.isEmpty() : "No log events captured"
        LogEvent ev = (LogEvent) events.last()

        // Log4j2 stores MDC in ContextData (StringMap). Convert to normal map for assertions.
        def mdc = ev.getContextData()?.toMap() ?: [:]

        // 'traceId' should be present and non-empty
        assert mdc.containsKey("traceId")
        assert (mdc.get("traceId") as String)?.trim()

        // Since our MDC sanitizer masks like normal keys, 'token' should exist and be masked
        assert mdc.containsKey("token")
        assert mdc.get("token") == "[MASKED_TOKEN]"
    }

    def "GET /log/card -> exact PAN-masked message"() {
        when:
        def resp = rest.getForEntity(url("/log/card"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        lastLine() == "Charge card=[MASKED_CARD]"
    }

    def "GET /log/secrets -> exact masked lines for password and api key"() {
        when:
        def resp = rest.getForEntity(url("/log/secrets"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        def lines = allLines()
        assert lines.any { it == "password=[MASKED]" }
        assert lines.any { it == "x-api-key=[MASKED_ACCESS_KEY]" }
    }

    def "GET /log/mdc -> exact line and no raw JWT leaked"() {
        when:
        def resp = rest.getForEntity(url("/log/mdc"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        lastLine() == "Plain message without args"
        assert !joined().contains("eyJ.hdr.pay.sig")
    }

    def "GET /log/iban -> exact masked IBAN line"() {
        when:
        def resp = rest.getForEntity(url("/log/iban"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        lastLine() == "payout iban=[MASKED_IBAN]"
    }

    def "GET /log/ip -> exact masked IP line"() {
        when:
        def resp = rest.getForEntity(url("/log/ip"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        lastLine() == "client ip=[MASKED_IP]"
    }

    def "GET /log/error -> 5xx and masked fragments present; raw secrets absent"() {
        when:
        def resp = rest.getForEntity(url("/log/error"), String)

        then:
        resp.statusCode.is5xxServerError()

        and:
        def m = joined()
        assert m.contains("Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: java.lang.RuntimeException: Failed to save user, token=[MASKED_TOKEN]] with root cause\n" +
                "[DB] [Masked] SQLException: password=[MASKED] url=jdbc:postgresql://[MASKED_URL]\n" +
                "\tat demo.DemoController.error(DemoController.java:80)\n" +
                "\tat jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)")
    }

    def "GET /log/error-nested -> 5xx and nested causes are masked"() {
        when:
        def resp = rest.getForEntity(url("/log/error-nested"), String)

        then:
        resp.statusCode.is5xxServerError()

        and:
        def m = joined()
        assert m.contains("Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: java.lang.RuntimeException: top-level msg] with root cause\n" +
                "[DB] [Masked] SQLException: jdbcUrl=jdbc:postgresql://[MASKED_URL] secret=[MASKED]\n" +
                "\tat demo.DemoController.nestedError(DemoController.java:90)\n" +
                "\tat jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)")
    }

    def "GET /log/embedded -> embedded block is masked"() {
        when:
        def resp = rest.getForEntity(url("/log/embedded"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def m = joined()
        assert m.contains("Embedded block start:\n" +
                "java.lang.RuntimeException: password=[MASKED]\n" +
                "\tat com.acme.Foo.bar(Foo.java:10)\n" +
                "\tat com.acme.Foo.baz(Foo.java:20)\n" +
                "Caused by: java.sql.SQLException: url=jdbc:postgresql://[MASKED_URL] user=[MASKED_USER]\n" +
                "  at org.postgresql.Driver.connect(Driver.java:42)")
    }

    def "GET /log/plain -> exact unmasked line"() {
        when:
        def resp = rest.getForEntity(url("/log/plain"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        lastLine() == "Hello world"
    }

    def "GET /log/status -> exact unmasked KVs"() {
        when:
        def resp = rest.getForEntity(url("/log/status"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        lastLine() == "status=OK, count=42"
    }

    def "GET /log/url -> token query param masked, user remains"() {
        when:
        def resp = rest.getForEntity(url("/log/url"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        lastLine() == "calling url=https://[MASKED_URL]"
    }

    def "GET /log/basic-auth -> basic header masked"() {
        when:
        def resp = rest.getForEntity(url("/log/basic-auth"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        lastLine() == "Auth header=Basic [MASKED_BASIC_AUTH]"
    }

    def "GET /log/private-key -> PEM content masked"() {
        when:
        def resp = rest.getForEntity(url("/log/private-key"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        def m = joined()
        assert m.contains("[MASKED_PRIVATE_KEY]")
    }
}