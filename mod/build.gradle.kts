plugins {
    id("eclipse")
    id("idea")
    kotlin("jvm") version "1.9.22"
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
}

group = "com.example.presentationmod"
version = "1.0.0"

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))

jarJar.enable()

fun org.gradle.api.artifacts.dsl.DependencyHandler.embedLibrary(
    group: String,
    name: String,
    version: String,
    maxExclusive: String
) {
    val pinned = "$group:$name:$version"
    val ranged = "$group:$name:[$version,$maxExclusive)"
    implementation(pinned)
    jarJar(ranged)
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net/")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

dependencies {
    minecraft("net.minecraftforge:forge:1.20.1-47.3.0")

    implementation(kotlin("stdlib"))
    implementation("thedarkcolour:kotlinforforge:4.11.0")

    embedLibrary("org.apache.pdfbox", "pdfbox", "2.0.36", "3.0")
    embedLibrary("org.apache.pdfbox", "fontbox", "2.0.36", "3.0")
    embedLibrary("org.apache.poi", "poi-ooxml", "5.3.0", "6.0")
    embedLibrary("org.apache.poi", "poi", "5.3.0", "6.0")
    embedLibrary("org.apache.poi", "poi-ooxml-lite", "5.3.0", "6.0")
    embedLibrary("org.apache.xmlbeans", "xmlbeans", "5.2.1", "6.0")
    embedLibrary("org.apache.commons", "commons-compress", "1.26.2", "2.0")
    embedLibrary("commons-io", "commons-io", "2.16.1", "3.0")
    embedLibrary("org.apache.commons", "commons-collections4", "4.4", "5.0")
    embedLibrary("com.github.virtuald", "curvesapi", "1.08", "2.0")
    embedLibrary("com.zaxxer", "SparseBitSet", "1.3", "2.0")
    embedLibrary("commons-logging", "commons-logging", "1.2", "2.0")

}

minecraft {
    mappings("official", "1.20.1")

    copyIdeResources = true

    runs {
        create("client") {
            workingDirectory(project.file("run"))

            mods {
                create("presentationmod") {
                    source(sourceSets.getByName("main"))
                }
            }
        }
    }
}

sourceSets.named("main") {
    java.srcDir("src/main/kotlin")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

tasks.named<Jar>("jar") {
    manifest.attributes["Implementation-Title"] = project.name
}
