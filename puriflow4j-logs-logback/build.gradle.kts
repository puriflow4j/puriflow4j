plugins { `java-library`; groovy }

dependencies {
    api(project(":puriflow4j-core"))
    api("ch.qos.logback:logback-classic:1.5.8")

    testImplementation(project(":puriflow4j-core"))
    testImplementation("ch.qos.logback:logback-classic:1.5.7")
}
