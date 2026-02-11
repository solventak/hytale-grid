plugins {
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "8.3.6"
    idea
}

group = "dev.hytalemodding"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(files("libs/HytaleServer.jar"))
}

tasks.processResources {
    filesMatching("manifest.json") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    dependencies {
        include(dependency("org.jetbrains.kotlin:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
