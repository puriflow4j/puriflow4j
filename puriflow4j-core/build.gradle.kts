plugins {
    `java-library`
    application
}

application {
    mainClass.set("io.puriflow4j.core.Hello")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories { mavenCentral() }
