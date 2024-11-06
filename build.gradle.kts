plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "8.3.5"
}

group = "net.flamgop"
version = "2.0"

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