plugins {
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "8.3.6"
    idea
}

group = "dev.hytalemodding"
version = "0.1.2"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(files("libs/HytaleServer.jar"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Djava.util.logging.manager=com.hypixel.hytale.logger.HytaleLogManager")
}

tasks.processResources {
    filesMatching("manifest.json") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("Grid")
    archiveVersion.set("v${project.version}")
    archiveClassifier.set("")
    dependencies {
        include(dependency("org.jetbrains.kotlin:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
