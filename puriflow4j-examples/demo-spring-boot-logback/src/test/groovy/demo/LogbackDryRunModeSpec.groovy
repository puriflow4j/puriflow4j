//package demo
//
//
//import ch.qos.logback.classic.spi.ILoggingEvent
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.boot.autoconfigure.SpringBootApplication
//import org.springframework.boot.test.context.SpringBootTest
//import org.springframework.boot.test.web.client.TestRestTemplate
//import org.springframework.boot.test.web.server.LocalServerPort
//import org.springframework.http.HttpStatus
//
//@SpringBootTest(
//        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
//        properties = [
//                "spring.config.name=__none__",
//                // make sure our in-memory appender is active
//                "logging.config=classpath:logback-test.xml",
//
//                "puriflow4j.logs.enabled=true",
//                "puriflow4j.logs.mode=dry_run",
//                "puriflow4j.logs.detectors[0]=token_bearer",
//                "puriflow4j.logs.detectors[1]=cloud_access_key",
//                "puriflow4j.logs.detectors[2]=api_token_well_known",
//                "puriflow4j.logs.detectors[3]=basic_auth",
//                "puriflow4j.logs.detectors[4]=db_credential",
//                "puriflow4j.logs.detectors[5]=url_redactor",
//                "puriflow4j.logs.detectors[6]=private_key",
//                "puriflow4j.logs.detectors[7]=credit_card",
//                "puriflow4j.logs.detectors[8]=email",
//                "puriflow4j.logs.detectors[9]=password_kv",
//                "puriflow4j.logs.detectors[10]=iban",
//                "puriflow4j.logs.detectors[11]=ip"
//        ],
//        classes = [
//                TestApp,
//                DemoController
//        ]
//)
//class LogbackDryRunModeSpec extends BaseLogbackSpec {
//
//    @SpringBootApplication
//    static class TestApp {}
//
//    @LocalServerPort int port
//    @Autowired TestRestTemplate rest
//
//    protected String url(String path) { "http://localhost:$port$path" }
//
//    // ---------- tests (exact messages where deterministic) ----------
//
//    def "GET /log/message -> exact NOT masked message string"() {
//        when:
//        def resp = rest.getForEntity(url("/log/message"), String)
//
//        then: "endpoint returns OK"
//        resp.statusCode == HttpStatus.OK
//
//        and: "the exact masked line is present"
//        lastMessage() == "Login attempt: email=alice@example.com, Authorization: Bearer eyJ.hdr.pay.sig, awsKey=AKIA1234567890ABCDE1, x-auth-token=lol13"
//    }
//
//    def "GET /log/message -> MDC is NOT masked"() {
//        when:
//        def resp = rest.getForEntity(url("/log/message"), String)
//
//        then: "endpoint returns OK"
//        resp.statusCode == HttpStatus.OK
//
//        and: "we have at least one log event captured"
//        assert !appender.list.isEmpty() : "No log events captured"
//
//        and: "inspect MDC of the last event"
//        ILoggingEvent ev = (ILoggingEvent) appender.list.last()
//        Map<String, String> mdc = ev.getMDCPropertyMap() ?: [:]
//
//        mdc.isEmpty() // TODO: figure out why it's empty by default
//
////        assert mdc.containsKey("traceId")
////        assert (mdc.get("traceId") as String)?.trim()
////
////        assert mdc.containsKey("token")
////        assert mdc.get("token") == "eyJ.hdr.pay.sig"
//    }
//
//    def "GET /log/card -> exact PAN is NOT masked"() {
//        when:
//        def resp = rest.getForEntity(url("/log/card"), String)
//
//        then:
//        resp.statusCode == HttpStatus.OK
//
//        and:
//        lastMessage() == "Charge card=4539 1488 0343 6467"
//    }
//
//    def "GET /log/secrets -> exact lines for password and api key NOT masked"() {
//        when:
//        def resp = rest.getForEntity(url("/log/secrets"), String)
//
//        then:
//        resp.statusCode == HttpStatus.OK
//
//        and:
//        def lines = allMessages().readLines()
//        assert lines.any { it == "password=MySuperSecret123" }
//        assert lines.any { it == "x-api-key=AbC1234567890def" }
//    }
//
//    def "GET /log/mdc -> exact line is NOT masked"() {
//        when:
//        def resp = rest.getForEntity(url("/log/mdc"), String)
//
//        then:
//        resp.statusCode == HttpStatus.OK
//
//        and:
//        lastMessage() == "Plain message without args"
//
//        and:
//        assert !appender.list.isEmpty() : "No log events captured"
//
//        and:
//        ILoggingEvent ev = (ILoggingEvent) appender.list.last()
//        Map<String, String> mdc = ev.getMDCPropertyMap() ?: [:]
//
//        mdc.isEmpty() // TODO: figure out why it's empty by default
//
////        assert mdc.containsKey("token")
////        assert mdc.get("token") == "eyJ.hdr.pay.sig"
//    }
//
//    def "GET /log/iban -> exact NOT masked IBAN line"() {
//        when:
//        def resp = rest.getForEntity(url("/log/iban"), String)
//
//        then:
//        resp.statusCode == HttpStatus.OK
//
//        and:
//        lastMessage() == "payout iban=DE89 3704 0044 0532 0130 00"
//    }
//
//    def "GET /log/ip -> exact NOT masked IP line"() {
//        when:
//        def resp = rest.getForEntity(url("/log/ip"), String)
//
//        then:
//        resp.statusCode == HttpStatus.OK
//
//        and:
//        lastMessage() == "client ip=203.0.113.42"
//    }
//
//    def "GET /log/error -> 5xx and NOT masked fragments present; raw secrets absent"() {
//        when:
//        def resp = rest.getForEntity(url("/log/error"), String)
//
//        then:
//        resp.statusCode.is5xxServerError()
//
//        and:
//        def m = allMessages()
//        assert m.contains("Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: java.lang.RuntimeException: Failed to save user, token=eyJ.hdr.pay.sig] with root cause")
//        assert appender.list.last.throwableProxy.className == "java.sql.SQLException"
//        assert appender.list.last.throwableProxy.message == "password=SuperSecret123 url=jdbc:postgresql://db.prod/acme"
//    }
//
//    def "GET /log/error-nested -> 5xx and nested causes are NOT masked"() {
//        when:
//        def resp = rest.getForEntity(url("/log/error-nested"), String)
//
//        then:
//        resp.statusCode.is5xxServerError()
//
//        and:
//        def m = allMessages()
//        assert m.contains("Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: java.lang.RuntimeException: top-level msg] with root cause")
//        assert appender.list.last.throwableProxy.className == "java.sql.SQLException"
//        assert appender.list.last.throwableProxy.message == 'jdbcUrl=jdbc:postgresql://db.prod/foo secret=abc'
//    }
//
//    def "GET /log/embedded -> embedded block is NOT masked"() {
//        when:
//        def resp = rest.getForEntity(url("/log/embedded"), String)
//
//        then:
//        resp.statusCode == HttpStatus.OK
//
//        and:
//        def m = allMessages()
//        assert m.contains("Embedded block start:\n" +
//                "java.lang.RuntimeException: password=TopSecret1\n" +
//                "\tat com.acme.Foo.bar(Foo.java:10)\n" +
//                "\tat com.acme.Foo.baz(Foo.java:20)\n" +
//                "Caused by: java.sql.SQLException: url=jdbc:postgresql://db.prod/acme user=alice\n" +
//                "  at org.postgresql.Driver.connect(Driver.java:42)")
//    }
//
//    def "GET /log/plain -> exact unmasked line"() {
//        when:
//        def resp = rest.getForEntity(url("/log/plain"), String)
//
//        then:
//        resp.statusCode == HttpStatus.OK
//
//        and: "the exact message is written as-is"
//        lastMessage() == "Hello world"
//    }
//
//    def "GET /log/status -> exact unmasked KVs"() {
//        when:
//        def resp = rest.getForEntity(url("/log/status"), String)
//
//        then:
//        resp.statusCode == HttpStatus.OK
//
//        and:
//        lastMessage() == "status=OK, count=42"
//    }
//
//    def "GET /log/url -> token query param is NOT masked, user remains"() {
//        when:
//        def resp = rest.getForEntity(url("/log/url"), String)
//
//        then:
//        resp.statusCode == HttpStatus.OK
//
//        and:
//        lastMessage() == "calling url=https://api.example.com/items?token=eyJ.hdr.pay.sig&user=alice"
//    }
//
//    def "GET /log/basic-auth -> basic header is NOT masked"() {
//        when:
//        def resp = rest.getForEntity(url("/log/basic-auth"), String)
//
//        then:
//        resp.statusCode == HttpStatus.OK
//
//        and:
//        lastMessage() == "Auth header=Basic dXNlcjpwYXNz"
//    }
//
//    def "GET /log/private-key -> PEM content is NOT masked"() {
//        when:
//        def resp = rest.getForEntity(url("/log/private-key"), String)
//
//        then:
//        resp.statusCode == HttpStatus.OK
//
//        and:
//        def msg = allMessages()
//        assert msg.contains("""pem block:
//-----BEGIN PRIVATE KEY-----
//MIIEvQIBADANBgkqhkiG9w0BAQEFAASC...
//-----END PRIVATE KEY-----""")
//    }
//}
