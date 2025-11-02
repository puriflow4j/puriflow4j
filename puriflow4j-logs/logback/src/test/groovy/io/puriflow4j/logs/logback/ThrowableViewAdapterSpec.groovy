package io.puriflow4j.logs.logback

import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.ThrowableProxy
import io.puriflow4j.logs.core.model.StackFrameView
import io.puriflow4j.logs.core.model.ThrowableView
import spock.lang.Specification

class ThrowableViewAdapterSpec extends Specification {

    // --- helpers (instance, not static) -------------------------------------

    private static StackTraceElement ste(String cn, String mn, String file, int line) {
        new StackTraceElement(cn, mn, file, line)
    }

    private Throwable withStack(Throwable t, List<StackTraceElement> st) {
        t.setStackTrace(st as StackTraceElement[])
        t
    }

    // --- tests ---------------------------------------------------------------

    def "returns null when proxy is null"() {
        expect:
        ThrowableViewAdapter.toView(null) == null
    }

    def "maps class, message, frames and cause recursively"() {
        given: "cause with its own stack"
        def cause = withStack(
                new IllegalStateException("bad state"),
                [ste("c.CauseClass", "doC", "Cause.java", 10)]
        )

        and: "top exception with 2 frames"
        def ex = withStack(
                new RuntimeException("top boom", cause),
                [
                        ste("a.Top", "m1", "Top.java", 111),
                        ste("a.Top", "m2", "Top.java", 222)
                ]
        )
        def proxy = new ThrowableProxy(ex)

        when:
        ThrowableView tv = ThrowableViewAdapter.toView(proxy)

        then: "top-level mapping"
        tv.className() == RuntimeException.class.getName()
        tv.message() == "top boom"
        tv.frames().size() == 2

        and: "frame fields are mapped 1:1 and order preserved"
        with(tv.frames().get(0)) { StackFrameView f ->
            assert f.className() == "a.Top"
            assert f.methodName() == "m1"
            assert f.fileName() == "Top.java"
            assert f.line() == 111
        }
        with(tv.frames().get(1)) { StackFrameView f ->
            assert f.className() == "a.Top"
            assert f.methodName() == "m2"
            assert f.fileName() == "Top.java"
            assert f.line() == 222
        }

        and: "cause is mapped recursively"
        tv.cause() != null
        tv.cause().className() == IllegalStateException.class.getName()
        tv.cause().message() == "bad state"
        tv.cause().frames().size() == 1
        with(tv.cause().frames().get(0)) { StackFrameView f ->
            assert f.className() == "c.CauseClass"
            assert f.methodName() == "doC"
            assert f.fileName() == "Cause.java"
            assert f.line() == 10
        }
    }

    def "handles proxies with null stack frame array (produces empty frame list)"() {
        given:
        IThrowableProxy p = Mock() {
            1 * getClassName() >> "X"
            1 * getMessage() >> "Y"
            1 * getStackTraceElementProxyArray() >> null
            1 * getCause() >> null
        }

        when:
        ThrowableView tv = ThrowableViewAdapter.toView(p)

        then:
        tv.className() == "X"
        tv.message() == "Y"
        tv.frames().isEmpty()
        tv.cause() == null
    }

    def "frames list is immutable"() {
        given:
        def ex = withStack(new RuntimeException("r"),
                [ste("pkg.A", "m", "A.java", 1)])
        def tv = ThrowableViewAdapter.toView(new ThrowableProxy(ex))

        when:
        tv.frames().add(new StackFrameView("X","Y","Z",0))

        then:
        thrown(UnsupportedOperationException)
    }
}
