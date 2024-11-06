import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "8.3.5"
}

group = "net.flamgop"
version = "2.1"

application {
    mainClass = "net.flamgop.Main"
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    shadow("org.jetbrains:annotations:26.0.1")
    implementation("com.formdev:flatlaf:3.5.2")
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
}