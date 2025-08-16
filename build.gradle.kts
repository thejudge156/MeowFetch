import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "8.3.5"
    id("org.graalvm.buildtools.native") version "0.11.0"
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
    implementation("dev.mobile:dadb:1.2.10")
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
}

graalvmNative {
    agent {
        defaultMode = "standard"
        enabled.set(true)
        enableExperimentalPredefinedClasses.set(true)

        metadataCopy {
            mergeWithExisting.set(true)
            outputDirectories.add("src/main/resources/META-INF/native-image/net.flamgop/meowfetch")
        }
    }
    binaries {
        named("main") {
            sharedLibrary.set(false)
            imageName.set("meowfetch")
            mainClass.set("net.flamgop.Main")
        }
    }
    binaries.configureEach {
        buildArgs.addAll(
            "-march=compatibility", "-Os", "-Djava.awt.headless=true",
            "-H:+ForeignAPISupport"
        )
    }
}