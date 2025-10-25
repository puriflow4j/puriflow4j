plugins { `java-library` }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    withSourcesJar()
}

dependencies {
    api(project(":puriflow4j-core"))

    // Do not pull Spring or backends transitively
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.3.4")
    compileOnly("org.springframework.boot:spring-boot-actuator:3.3.4")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure:3.3.4")
    compileOnly("io.micrometer:micrometer-core:1.13.6")

    implementation(project(":puriflow4j-logs:logback"))
    // compileOnly(project(":puriflow4j-logs-log4j2")) // future
    // compileOnly(project(":puriflow4j-logs-jul"))    // future

    compileOnly("org.slf4j:slf4j-api:2.0.13")
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
}

publishing {
    publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}
