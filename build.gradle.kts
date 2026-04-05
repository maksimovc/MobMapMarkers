import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.SourceSetContainer

plugins {
    java
}

group = "dev.thenexusgates"
version = "1.5.0"

val simpleMinimapJar = layout.projectDirectory.file("../../OtherMapMods/SimpleMinimap-8.4.0.jar").asFile
val forceSimpleMinimapStubs = providers.gradleProperty("mobMapMarkers.useSimpleMinimapStubs")
    .map(String::toBoolean)
    .orElse(false)
val useSimpleMinimapJar = !forceSimpleMinimapStubs.get() && simpleMinimapJar.exists()

repositories {
    mavenCentral()
    maven(url = "https://maven.hytale.com/release")
}

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

extensions.configure<SourceSetContainer>("sourceSets") {
    named("main") {
        if (!useSimpleMinimapJar) {
            java.srcDir("src/simpleminimap-stubs/java")
        }
    }
}

dependencies {
    add("compileOnly", "com.hypixel.hytale:Server:+")
    add("testImplementation", "com.hypixel.hytale:Server:+")
    if (useSimpleMinimapJar) {
        add("compileOnly", files(simpleMinimapJar))
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
    exclude("com/Landscaper/**")
}
