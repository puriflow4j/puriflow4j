pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "puriflow4j"
include(
        "puriflow4j-core",
        "puriflow4j-logs-logback",
        "puriflow4j-logs-log4j2",
        "puriflow4j-logs-jul",
        "puriflow4j-spring-boot",
        "examples:demo-spring-boot-logback",
        "examples:demo-spring-boot-log4j2",
        "examples:demo-plain-jul"
)
