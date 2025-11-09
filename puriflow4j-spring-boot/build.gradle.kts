plugins { `java-library` }

dependencies {
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.3.4")
    compileOnly("org.springframework.boot:spring-boot-actuator:3.3.4")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure:3.3.4")
    compileOnly("io.micrometer:micrometer-core:1.13.6")

    implementation(project(":puriflow4j-logs:logback"))
    implementation(project(":puriflow4j-logs:log4j2"))

    // compileOnly(project(":puriflow4j-logs-jul"))    // future

    compileOnly("org.slf4j:slf4j-api:2.0.13")
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    testImplementation("io.micrometer:micrometer-core:1.13.6")
    testImplementation("org.springframework.boot:spring-boot-test:3.3.4")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure:3.3.4")
}

publishing {
    publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}
