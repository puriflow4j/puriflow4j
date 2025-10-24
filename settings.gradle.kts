pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "puriflow4j"
include(
        "puriflow4j-core",

        "puriflow4j-logs:core",
        "puriflow4j-logs:logback",
        "puriflow4j-logs:log4j2",
        "puriflow4j-logs:jul",

        "puriflow4j-spring-boot",

        "puriflow4j-examples:demo-spring-boot-logback",
        "puriflow4j-examples:demo-spring-boot-log4j2",
        "puriflow4j-examples:demo-plain-jul"
)
include("logs")
include("puriflow4j-logs:core")
findProject(":puriflow4j-logs:core")?.name = "core"
