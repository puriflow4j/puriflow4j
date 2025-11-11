package demo

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.annotation.DirtiesContext

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = [
                "spring.config.name=__none__",
                "logging.config=classpath:logback-test.xml",

                "puriflow4j.logs.enabled=true",
                "puriflow4j.logs.mode=dry_run",
                "puriflow4j.logs.detectors[0]=token_bearer",
                "puriflow4j.logs.detectors[1]=cloud_access_key",
                "puriflow4j.logs.detectors[2]=api_token_well_known",
                "puriflow4j.logs.detectors[3]=basic_auth",
                "puriflow4j.logs.detectors[4]=db_credential",
                "puriflow4j.logs.detectors[5]=url_redactor",
                "puriflow4j.logs.detectors[6]=private_key",
                "puriflow4j.logs.detectors[7]=credit_card",
                "puriflow4j.logs.detectors[8]=email",
                "puriflow4j.logs.detectors[9]=password_kv",
                "puriflow4j.logs.detectors[10]=iban",
                "puriflow4j.logs.detectors[11]=ip"
        ],
        classes = [
                TestApp,
                DemoController
        ]
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LogbackDryRunModeSpec extends BaseLogbackSpec {

    @SpringBootApplication
    static class TestApp {}

    @LocalServerPort int port
    @Autowired TestRestTemplate rest

    protected String url(String path) { "http://localhost:$port$path" }

    // ---------- tests (exact messages where deterministic) ----------

    def "GET /log/message -> exact NOT masked message string AND puriflow4j DRY-RUN warning is printed"() {
        when:
        def resp = rest.getForEntity(url("/log/message"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 2].formattedMessage == "Login attempt: email=alice@example.com, Authorization: Bearer eyJ.hdr.pay.sig, awsKey=AKIA1234567890ABCDE1, x-auth-token=lol13"
        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 4 sensitive fragment(s) in logger='demo.DemoController' types=[email, token, cloudAccessKey]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }

    def "GET /log/message -> MDC is NOT masked"() {
        when:
        def resp = rest.getForEntity(url("/log/message"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        Map<String, String> mdc = appender.list[logsSize - 2].getMDCPropertyMap() ?: [:]

        and:
        assert mdc.containsKey("traceId")
        assert (mdc.get("traceId") as String)?.trim()

        assert mdc.containsKey("token")
        assert mdc.get("token") == "eyJ.hdr.pay.sig"
    }

    def "GET /log/card -> exact PAN is NOT masked AND puriflow4j DRY-RUN warning is printed"() {
        when:
        def resp = rest.getForEntity(url("/log/card"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 2].formattedMessage == "Charge card=4539 1488 0343 6467"
        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 2 sensitive fragment(s) in logger='demo.DemoController' types=[card, token]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }

    def "GET /log/secrets -> exact lines for password and api key NOT masked AND puriflow4j DRY-RUN warning is printed"() {
        when:
        def resp = rest.getForEntity(url("/log/secrets"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 4].formattedMessage == "password=MySuperSecret123"
        appender.list[logsSize - 3].formattedMessage == "[puriflow4j DRY-RUN]: detected 2 sensitive fragment(s) in logger='demo.DemoController' types=[password, token]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
        appender.list[logsSize - 2].formattedMessage == "x-api-key=AbC1234567890def"
        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 2 sensitive fragment(s) in logger='demo.DemoController' types=[cloudAccessKey, token]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }

    def "GET /log/mdc -> exact line is NOT masked AND puriflow4j DRY-RUN warning is printed"() {
        when:
        def resp = rest.getForEntity(url("/log/mdc"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 2].formattedMessage == "Plain message without args"
        appender.list[logsSize - 2].getMDCPropertyMap().get("token") == "eyJ.hdr.pay.sig"
        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 1 sensitive fragment(s) in logger='demo.DemoController' types=[token]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }

    def "GET /log/iban -> exact NOT masked IBAN line AND puriflow4j DRY-RUN warning is printed"() {
        when:
        def resp = rest.getForEntity(url("/log/iban"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 2].formattedMessage == "payout iban=DE89 3704 0044 0532 0130 00"
        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 2 sensitive fragment(s) in logger='demo.DemoController' types=[iban, token]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }

    def "GET /log/ip -> exact NOT masked IP line AND puriflow4j DRY-RUN warning is printed"() {
        when:
        def resp = rest.getForEntity(url("/log/ip"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 2].formattedMessage == "client ip=203.0.113.42"
        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 2 sensitive fragment(s) in logger='demo.DemoController' types=[ip, token]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }

    def "GET /log/error -> 5xx and NOT masked fragments present; raw secrets absent AND puriflow4j DRY-RUN warning is printed"() {
        when:
        def resp = rest.getForEntity(url("/log/error"), String)

        then:
        resp.statusCode.is5xxServerError()

        and:
        def logsSize = appender.list.size()

        appender.list[logsSize - 2].formattedMessage == "Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: java.lang.RuntimeException: Failed to save user, token=eyJ.hdr.pay.sig] with root cause"
        appender.list[logsSize - 2].throwableProxy.className == "java.sql.SQLException"
        appender.list[logsSize - 2].throwableProxy.message == "password=SuperSecret123 url=jdbc:postgresql://db.prod/acme"

        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 4 sensitive fragment(s) in logger='org.apache.catalina.core.ContainerBase.[Tomcat].[localhost].[/].[dispatcherServlet]' types=[token, password, url]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }

    def "GET /log/error-nested -> 5xx and nested causes are NOT masked AND puriflow4j DRY-RUN warning is printed"() {
        when:
        def resp = rest.getForEntity(url("/log/error-nested"), String)

        then:
        resp.statusCode.is5xxServerError()

        and:
        def logsSize = appender.list.size()

        appender.list[logsSize - 2].formattedMessage == "Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: java.lang.RuntimeException: top-level msg] with root cause"
        appender.list[logsSize - 2].throwableProxy.className == "java.sql.SQLException"
        appender.list[logsSize - 2].throwableProxy.message == "jdbcUrl=jdbc:postgresql://db.prod/foo secret=abc"

        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 3 sensitive fragment(s) in logger='org.apache.catalina.core.ContainerBase.[Tomcat].[localhost].[/].[dispatcherServlet]' types=[url, password, token]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }

    def "GET /log/embedded -> embedded block is NOT masked AND puriflow4j DRY-RUN warning is printed"() {
        when:
        def resp = rest.getForEntity(url("/log/embedded"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 2].formattedMessage == "Embedded block start:\n" +
                "java.lang.RuntimeException: password=TopSecret1\n" +
                "\tat com.acme.Foo.bar(Foo.java:10)\n" +
                "\tat com.acme.Foo.baz(Foo.java:20)\n" +
                "Caused by: java.sql.SQLException: url=jdbc:postgresql://db.prod/acme user=alice\n" +
                "  at org.postgresql.Driver.connect(Driver.java:42)\n" +
                "\n" +
                "--- end"

        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 4 sensitive fragment(s) in logger='demo.DemoController' types=[password, url, dbCredential, token]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }

    def "GET /log/plain -> exact unmasked line"() {
        when:
        def resp = rest.getForEntity(url("/log/plain"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and: "the exact message is written as-is"
        def logsSize = appender.list.size()
        appender.list[logsSize - 2].formattedMessage == "Hello world"
        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 1 sensitive fragment(s) in logger='demo.DemoController' types=[token]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }

    def "GET /log/status -> exact unmasked KVs"() {
        when:
        def resp = rest.getForEntity(url("/log/status"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 2].formattedMessage == "status=OK, count=42"
        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 1 sensitive fragment(s) in logger='demo.DemoController' types=[token]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }

    def "GET /log/url -> token query param is NOT masked, user remains AND puriflow4j DRY-RUN warning is printed"() {
        when:
        def resp = rest.getForEntity(url("/log/url"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 2].formattedMessage == "calling url=https://api.example.com/items?token=eyJ.hdr.pay.sig&user=alice"
        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 2 sensitive fragment(s) in logger='demo.DemoController' types=[url, token]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }

    def "GET /log/basic-auth -> basic header is NOT masked AND puriflow4j DRY-RUN warning is printed"() {
        when:
        def resp = rest.getForEntity(url("/log/basic-auth"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 2].formattedMessage == "Auth header=Basic dXNlcjpwYXNz"
        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 2 sensitive fragment(s) in logger='demo.DemoController' types=[basicAuth, token]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }

    def "GET /log/private-key -> PEM content is NOT masked AND puriflow4j DRY-RUN warning is printed"() {
        when:
        def resp = rest.getForEntity(url("/log/private-key"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()

        appender.list[logsSize - 2].formattedMessage == """pem block:
-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASC...
-----END PRIVATE KEY-----
"""

        appender.list[logsSize - 1].formattedMessage == "[puriflow4j DRY-RUN]: detected 2 sensitive fragment(s) in logger='demo.DemoController' types=[privateKey, token]. Enable puriflow4j.logs.mode=mask (or strict) to auto-sanitize."
    }
}
