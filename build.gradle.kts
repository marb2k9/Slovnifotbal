// Kořenový build skript projektu Slovní fotbal.
//
// AGP 9 kompiluje Kotlin přímo (built-in Kotlin) – plugin org.jetbrains.kotlin.android se nepoužívá.
// Verze Kotlin Gradle pluginu se sjednocuje s verzí Compose compiler pluginu (viz libs.versions.toml),
// jak doporučuje dokumentace AGP 9 („To use a higher version of KGP … add classpath to the top-level build file“).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // musí odpovídat [versions] kotlin v gradle/libs.versions.toml
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
