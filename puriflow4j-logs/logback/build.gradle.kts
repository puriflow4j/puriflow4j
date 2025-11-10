plugins { `java-library` }

dependencies {
    api(project(":puriflow4j-logs:core"))
    compileOnlyApi("ch.qos.logback:logback-classic:1.5.12") // do not pull logback transitively
    testImplementation("ch.qos.logback:logback-classic:1.5.12")
}

publishing {
    publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}
