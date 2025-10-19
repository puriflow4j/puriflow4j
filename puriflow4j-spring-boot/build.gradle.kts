plugins { `java-library` }

dependencies {
    api(project(":puriflow4j-logs"))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.3.4")
}
