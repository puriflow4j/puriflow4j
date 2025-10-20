import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension

plugins {
    `java-library`
    `maven-publish`
    id("com.diffplug.spotless") version "6.25.0"
    id("com.github.spotbugs") version "6.0.18" apply false
}

allprojects {
    group = "io.puriflow4j"
    version = "0.1.0-SNAPSHOT"
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    java {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    // ------------------ Spotless ------------------
    apply(plugin = "com.diffplug.spotless")

    spotless {
        java {
            palantirJavaFormat()
            trimTrailingWhitespace()
            endWithNewline()
            target("src/**/*.java")
            licenseHeaderFile("${rootProject.projectDir}/config/spotless/license-header.txt")
        }
    }

    // ------------------ SpotBugs ------------------
    apply(plugin = "com.github.spotbugs")

    plugins.withId("java") {
        configure<SpotBugsExtension> {
            effort.set(Effort.MAX)
            reportLevel.set(Confidence.LOW)
            excludeFilter.set(file("${rootProject.projectDir}/config/spotbugs/exclude.xml"))
        }

        tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
            reports.create("html") {
                required.set(true)
                outputLocation.set(file("$buildDir/reports/spotbugs/${project.name}.html"))
            }
        }

        tasks.named("check") { dependsOn("spotbugsMain") }
    }
}

configure(listOf(project(":puriflow4j-core"), project(":puriflow4j-logs"))) {
    apply(plugin = "groovy")

    dependencies {
        testImplementation(platform("org.spockframework:spock-bom:2.3-groovy-4.0"))
        testImplementation("org.spockframework:spock-core")
        testImplementation("org.apache.groovy:groovy-all:4.0.23")

        testImplementation("org.assertj:assertj-core:3.26.3")
        testImplementation("ch.qos.logback:logback-classic:1.5.7")
    }

    tasks.test { useJUnitPlatform() }
}