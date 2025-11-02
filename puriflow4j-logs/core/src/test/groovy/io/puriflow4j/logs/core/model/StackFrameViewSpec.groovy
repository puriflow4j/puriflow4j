package io.puriflow4j.logs.core.model

import spock.lang.Specification
import spock.lang.Unroll

class StackFrameViewSpec extends Specification {

    def "stores fields correctly"() {
        when:
        def view = new StackFrameView("com.example.Class", "method", "Class.java", 42)

        then:
        view.className() == "com.example.Class"
        view.methodName() == "method"
        view.fileName() == "Class.java"
        view.line() == 42
        view.toString().contains("StackFrameView[className=com.example.Class, methodName=method, fileName=Class.java, line=42]")
    }

    @Unroll
    def "pretty() formats as 'class.method(file:line)' with file '#file', line #line"(String file, int line, String expected) {
        expect:
        new StackFrameView("pkg.A", "m", file, line).pretty() == expected

        where:
        file            | line | expected
        "A.java"        | 10   | "pkg.A.m(A.java:10)"
        "B.java"        | -1   | "pkg.A.m(B.java)"
        null            | 99   | "pkg.A.m(Unknown Source)"
        "C.java"        | -1   | "pkg.A.m(C.java)"
    }

    def "pretty() handles null filename and negative line gracefully"() {
        expect:
        new StackFrameView("X", "y", null, -5).pretty() == "X.y(Unknown Source)"
    }
}
