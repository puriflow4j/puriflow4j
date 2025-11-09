plugins { `java-library` }

dependencies {
    api(project(":puriflow4j-logs:core"))
    //compileOnlyApi("org.apache.logging.log4j:log4j-core") // do not pull log4j transitively
}

publishing {
    publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}
