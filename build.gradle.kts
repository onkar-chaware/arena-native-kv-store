plugins {
    java
    id("me.champeau.jmh") version "0.7.2"
}

group = "com.arena.kv"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.assertj:assertj-core:3.24.1")

    // Hashing
    implementation("net.openhft:zero-allocation-hashing:0.16")

    // JMH
    jmhImplementation("org.openjdk.jmh:jmh-core:1.37")
    jmhImplementation("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()

    // Enable preview features and native access for FFM API
    jvmArgs = listOf(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED"
    )
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf(
        "--enable-preview",
        "-Xlint:preview"
    ))
}

// JMH task configuration
tasks.jmh {
    jvmArgs = listOf(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED"
    )
}
