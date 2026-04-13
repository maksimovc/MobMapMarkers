import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.testing.Test

plugins {
    java
}

group = "dev.thenexusgates"
version = "1.6.0"

val fastMiniMapJar = layout.projectDirectory.file("../FastMiniMap/build/libs/FastMiniMap-1.1.0.jar").asFile

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
    if (fastMiniMapJar.exists()) {
        add("compileOnly", files(fastMiniMapJar))
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
}
