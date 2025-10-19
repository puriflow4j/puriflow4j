pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "puriflow4j"

include(
        "puriflow4j-core",
        "puriflow4j-pii",
        "puriflow4j-logs",
        "puriflow4j-spring-boot",
        "examples:demo-spring-boot"
)
