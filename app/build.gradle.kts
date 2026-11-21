import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Kotlin kompiluje přímo AGP 9 (built-in Kotlin); Compose compiler plugin je potřeba zvlášť.
    alias(libs.plugins.kotlin.compose)
}

// Podpisový klíč pro release verzi: soubor keystore.properties v kořeni projektu (viz README.md)
// nebo proměnné prostředí SF_KEYSTORE_FILE, SF_KEYSTORE_PASSWORD, SF_KEY_ALIAS, SF_KEY_PASSWORD.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun signingValue(property: String, env: String): String? =
    keystoreProperties.getProperty(property) ?: System.getenv(env)

val releaseStoreFile = signingValue("storeFile", "SF_KEYSTORE_FILE")?.let { rootProject.file(it) }
val hasReleaseKey = releaseStoreFile?.isFile == true

android {
    namespace = "cz.slovnifotbal"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cz.slovnifotbal"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // každý instrumentační test běží s čistými daty aplikace
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    signingConfigs {
        // Veřejný ladicí klíč projektu (záměrně v repozitáři, heslo „android“): všechna sestavení
        // z CI i z různých počítačů mají stejný podpis, takže novější APK jde nainstalovat přes starší
        // bez ztráty statistik. Pro veřejné šíření použij vlastní klíč (keystore.properties).
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            if (hasReleaseKey) {
                storeFile = releaseStoreFile
                storePassword = signingValue("storePassword", "SF_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SF_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SF_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Bez vlastního klíče se release podepíše ladicím klíčem, aby šel APK rovnou nainstalovat.
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        unitTests.all {
            it.maxHeapSize = "2g"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Úplný výpis selhaných testů v logu sestavení (i v CI)
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}
