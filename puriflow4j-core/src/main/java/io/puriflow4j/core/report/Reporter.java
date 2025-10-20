package io.puriflow4j.core.report;

import io.puriflow4j.core.api.models.Finding;
import java.util.List;

public interface Reporter {
    void report(List<Finding> findings);
}
