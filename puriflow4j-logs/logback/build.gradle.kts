plugins { `java-library` }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    withSourcesJar()
}

dependencies {
    api(project(":puriflow4j-logs:core"))
    compileOnlyApi("ch.qos.logback:logback-classic:1.5.12") // do not pull logback transitively
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.6")
}

publishing {
    publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}
