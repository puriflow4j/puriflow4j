plugins { `java-library` }

dependencies {
    api(project(":puriflow4j-logs:core"))
    compileOnlyApi("org.apache.logging.log4j:log4j-core:2.24.1")
}

publishing {
    publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}
