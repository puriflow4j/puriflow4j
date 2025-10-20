package io.puriflow4j.core.report;

import io.puriflow4j.core.api.models.Finding;
import java.util.List;

public final class NoopReporter implements Reporter {
    @Override public void report(List<Finding> findings) { /* no-op */ }
}
