plugins { `java-library` }

dependencies {
    api(project(":puriflow4j-core"))
    api(project(":puriflow4j-logs"))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.3.4")
    compileOnly("org.springframework.boot:spring-boot-starter-actuator:3.3.4")
    compileOnly("io.micrometer:micrometer-core:1.13.4")
}
