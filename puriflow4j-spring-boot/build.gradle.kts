plugins { `java-library` }

dependencies {
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.3.4")

    implementation(project(":puriflow4j-logs:logback"))
    implementation(project(":puriflow4j-logs:log4j2"))
    implementation(project(":puriflow4j-logs:jul"))

    compileOnly("org.slf4j:slf4j-api:2.0.13")
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    testImplementation("org.springframework.boot:spring-boot-test:3.3.4")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure:3.3.4")

    testImplementation("ch.qos.logback:logback-classic:1.5.12")
    testImplementation("org.apache.logging.log4j:log4j-core:2.24.1")
}

publishing {
    publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}
