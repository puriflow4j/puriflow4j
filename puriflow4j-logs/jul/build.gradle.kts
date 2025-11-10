plugins { `java-library` }

dependencies {
    api(project(":puriflow4j-logs:core"))
}

publishing {
    publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
}
