package io.puriflow4j.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "puriflow4j.logs")
public class PuriflowProperties {
    /** Main flag: Enable log sanitization on all appenders. */
    private boolean enabled = true;

    /** Wrap only the root logger (true) or all loggers recursively (false - more aggressive). */
    private boolean rootOnly = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isRootOnly() { return rootOnly; }
    public void setRootOnly(boolean rootOnly) { this.rootOnly = rootOnly; }
}
