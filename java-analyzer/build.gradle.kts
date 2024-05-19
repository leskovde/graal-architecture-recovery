plugins {
    id("java")
    id("application")
    id("io.freefair.lombok") version "8.6"
}

application {
    mainClass.set("cz.cuni.mff.d3s.Runner")
}

group = "cz.cuni.mff.d3s"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.25.10")
    implementation("com.github.javaparser:javaparser-core:3.25.10")
    implementation("org.graphstream:gs-core:2.0")
    implementation("org.graphstream:gs-ui-swing:2.0")
    implementation("commons-io:commons-io:2.7")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}