import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.testing.Test

plugins {
    java
}

group = "dev.thenexusgates"
version = "1.6.3"
description = "Mob markers for the Hytale world map, BetterMap radar, and optional FastMiniMap overlays."

val fastMiniMapJarDir = layout.projectDirectory.dir("../FastMiniMap/build/libs").asFile

repositories {
    mavenCentral()
    maven(url = "https://maven.hytale.com/release")
}

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    add("compileOnly", "com.hypixel.hytale:Server:+")
    add("testImplementation", "com.hypixel.hytale:Server:+")
    val fastMiniMapJars = fileTree(fastMiniMapJarDir) {
        include("FastMiniMap-*.jar")
    }
    if (!fastMiniMapJars.isEmpty) {
        add("compileOnly", fastMiniMapJars)
    }

    add("testImplementation", platform("org.junit:junit-bom:5.12.2"))
    add("testImplementation", "org.junit.jupiter:junit-jupiter")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("MobMapMarkers-${version}.jar")
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to project.group
        )
    }
}
