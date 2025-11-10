package io.puriflow4j.logs.log4j2

import io.puriflow4j.logs.core.model.StackFrameView
import io.puriflow4j.logs.core.model.ThrowableView
import spock.lang.Specification

class ThrowableViewAdapterSpec extends Specification {

    private static StackTraceElement ste(String cls, String m, String file, int line) {
        new StackTraceElement(cls, m, file, line)
    }

    def "toView(null) returns null"() {
        expect:
        ThrowableViewAdapter.toView(null) == null
    }

    def "maps class, message, frames and cause"() {
        given: "exception with custom stack and cause chain"
        def leaf = new IllegalArgumentException("leaf-msg")
        leaf.stackTrace = [
                ste("a.A", "m1", "A.java", 10),
                ste("a.B", "m2", "B.java", 20),
        ] as StackTraceElement[]

        def mid = new IllegalStateException("mid-msg", leaf)
        mid.stackTrace = [
                ste("b.C", "x", "C.java", 30)
        ] as StackTraceElement[]

        def top = new RuntimeException("top-msg", mid)
        top.stackTrace = [
                ste("c.D", "y", "D.java", 40),
                ste("c.E", "z", "E.java", 50),
        ] as StackTraceElement[]

        when:
        ThrowableView tv = ThrowableViewAdapter.toView(top)

        then: "top level mapped"
        tv != null
        tv.className == RuntimeException.class.name
        tv.message == "top-msg"

        and: "top frames mapped in order"
        tv.frames.size() == 2
        with(tv.frames[0]) {
            className == "c.D"
            methodName == "y"
            fileName == "D.java"
            line == 40
        }
        with(tv.frames[1]) {
            className == "c.E"
            methodName == "z"
            fileName == "E.java"
            line == 50
        }

        and: "first cause mapped"
        tv.cause != null
        tv.cause.className == IllegalStateException.class.name
        tv.cause.message == "mid-msg"
        tv.cause.frames.size() == 1
        with(tv.cause.frames[0]) {
            className == "b.C"
            methodName == "x"
            fileName == "C.java"
            line == 30
        }

        and: "leaf cause mapped"
        tv.cause.cause != null
        tv.cause.cause.className == IllegalArgumentException.class.name
        tv.cause.cause.message == "leaf-msg"
        tv.cause.cause.frames.size() == 2
        with(tv.cause.cause.frames[0]) {
            className == "a.A"; methodName == "m1"; fileName == "A.java"; line == 10
        }
        with(tv.cause.cause.frames[1]) {
            className == "a.B"; methodName == "m2"; fileName == "B.java"; line == 20
        }

        and: "no further cause"
        tv.cause.cause.cause == null
    }

    def "frames list is unmodifiable (adapter uses List.copyOf)"() {
        given:
        def ex = new RuntimeException("msg")
        ex.stackTrace = [ ste("x.Y", "f", "Y.java", 1) ] as StackTraceElement[]
        def tv = ThrowableViewAdapter.toView(ex)

        when: "try to mutate frames"
        tv.frames.add(new StackFrameView("q.W","g","W.java",2))

        then:
        thrown(UnsupportedOperationException)
    }
}
