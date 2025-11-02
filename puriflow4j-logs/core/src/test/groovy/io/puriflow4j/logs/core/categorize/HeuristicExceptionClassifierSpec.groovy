package io.puriflow4j.logs.core.categorize

import io.puriflow4j.logs.core.model.ThrowableView
import spock.lang.Specification
import spock.lang.Unroll

class HeuristicExceptionClassifierSpec extends Specification {

    private static ThrowableView tv(String className) {
        // message/frames/cause are irrelevant for classification
        new ThrowableView(className, null, List.of(), null)
    }

    def "returns NONE for null class name"() {
        given:
        def c = new HeuristicExceptionClassifier()

        when:
        def res = c.classify(tv(null))

        then:
        res != null
        !res.hasLabel()
    }

    @Unroll
    def "classifies DB-like exceptions for '#cn'"(String cn) {
        given:
        def c = new HeuristicExceptionClassifier()

        expect:
        def res = c.classify(tv(cn))
        res.hasLabel()
        res.label() == "DB"

        where:
        cn << [
                "java.sql.SQLException",
                "org.postgresql.util.PSQLException",
                "com.mysql.jdbc.JDBC4MySQLSyntaxErrorException",
                "oracle.jdbc.OracleException",
                "com.mongodb.MongoException",
                "io.lettuce.core.RedisException",
                "com.myapp.datasource.CustomDataSourceError",
                "com.acme.jdbc.CustomDbException"
        ]
    }

    @Unroll
    def "classifies JSON exceptions for '#cn'"(String cn) {
        given:
        def c = new HeuristicExceptionClassifier()

        expect:
        def res = c.classify(tv(cn))
        res.hasLabel()
        res.label() == "JSON"

        where:
        cn << [
                "com.fasterxml.jackson.core.JsonParseException",
                "org.json.JSONException",
                "my.app.parsing.JSONMappingFailure"
        ]
    }

    @Unroll
    def "classifies HTTP exceptions for '#cn'"(String cn) {
        given:
        def c = new HeuristicExceptionClassifier()

        expect:
        def res = c.classify(tv(cn))
        res.hasLabel()
        res.label() == "HTTP"

        where:
        cn << [
                "org.springframework.web.client.HttpServerErrorException",
                "jakarta.servlet.ServletException",
                "feign.RetryableException",
                "com.acme.client.RestClientFailure"
        ]
    }

    @Unroll
    def "classifies IO exceptions for '#cn'"(String cn) {
        given:
        def c = new HeuristicExceptionClassifier()

        expect:
        def res = c.classify(tv(cn))
        res.hasLabel()
        res.label() == "IO"

        where:
        cn << [
                "java.io.IOException",
                "java.net.SocketTimeoutException",     // contains 'socket'
                "com.acme.nio.ChannelBrokenException", // contains 'channel' / 'nio'
                "org.foo.io.CustomIoFailure"           // contains 'io.'
        ]
    }

    @Unroll
    def "classifies SECURITY exceptions for '#cn'"(String cn) {
        given:
        def c = new HeuristicExceptionClassifier()

        expect:
        def res = c.classify(tv(cn))
        res.hasLabel()
        res.label() == "SECURITY"

        where:
        cn << [
                "org.springframework.security.access.AccessDeniedException",
                "com.acme.auth.AuthenticationError",
                "com.acme.auth.AuthorizationError",
                "my.pkg.ForbiddenOperationException",
                "my.pkg.UnauthorizedAccess" // 'unauthor' substring
        ]
    }

    @Unroll
    def "classifies TIMEOUT exceptions for '#cn'"(String cn) {
        given:
        def c = new HeuristicExceptionClassifier()

        expect:
        def res = c.classify(tv(cn))
        res.hasLabel()
        res.label() == "TIMEOUT"

        where:
        cn << [
                "java.util.concurrent.TimeoutException",
                "com.acme.net.OperationTimedOut"
        ]
    }

    def "returns NONE when no heuristic matches"() {
        given:
        def c = new HeuristicExceptionClassifier()

        when:
        def res = c.classify(tv("com.company.domain.BusinessRuleViolation"))

        then:
        !res.hasLabel()
    }

    def "classification is case-insensitive"() {
        given:
        def c = new HeuristicExceptionClassifier()

        when:
        def res = c.classify(tv("ORG.JSON.JSONEXCEPTION"))

        then:
        res.hasLabel()
        res.label() == "JSON"
    }
}