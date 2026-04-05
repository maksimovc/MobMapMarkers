import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.Copy
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.register

apply(plugin = "java")

val earlyPluginProjects = setOf(
    "mod_21_hyxin_init_head_mixin",
    "mod_22_hyxin_init_tail_mixin",
    "mod_33_event_priority_probe",
    "mod_34_event_cancellation_probe",
    // companion mixin module should deploy as an early plugin
)

val deployFolderName = if (project.name in earlyPluginProjects) "earlyplugins" else "mods"
val deployDir = rootProject.layout.buildDirectory.dir(deployFolderName)

repositories {
    mavenCentral()
    maven(url = "https://maven.hytale.com/release")
    maven(url = "https://repo.spongepowered.org/repository/maven-public/")
}

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

val usesMixin = (extra.properties["labUseMixin"] as String?)?.toBoolean() ?: false

dependencies {
    add("compileOnly", "com.hypixel.hytale:Server:+")
    if (usesMixin) {
        add("compileOnly", "org.spongepowered:mixin:0.8.5")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Jar>("jar") {
    archiveBaseName.set(project.name)
    manifest.attributes(
        mapOf(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version.toString()
        )
    )
}

val deployBuiltJar = tasks.register<Copy>("deployBuiltJar") {
    dependsOn(tasks.named("jar"))
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    into(deployDir)
}

tasks.named("build") {
    finalizedBy(deployBuiltJar)
}
