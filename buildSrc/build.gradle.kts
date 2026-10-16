plugins {
    `kotlin-dsl` // lets us write plugins as .gradle.kts files in src/main/kotlin
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Versions come from gradle/libs.versions.toml
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.dependency.management.plugin)
}
