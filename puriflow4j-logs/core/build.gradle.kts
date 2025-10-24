plugins { `java-library` }

dependencies {
    api(project(":puriflow4j-core"))
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.6")
}
