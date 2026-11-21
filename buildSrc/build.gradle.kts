// Build logic for the native image (ST-004): the reachability rules derived from the
// dependencies, and the GraalVM build inside Visual Studio's environment.
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
