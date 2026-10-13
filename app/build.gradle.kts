import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: the GitHub release workflow writes keystore.properties (never committed)
// from the repository secrets before building:
//   storeFile=/path/to/release.jks
//   storePassword=...
//   keyAlias=...
//   keyPassword=...
val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

/** The checkout described from the latest v* tag, or "0.0.0-dev" without tags or git. */
fun gitVersion(): String =
    runCatching {
        providers.exec {
            commandLine("git", "describe", "--tags", "--match", "v[0-9]*", "--dirty")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().removePrefix("v")
    }.getOrNull()?.ifBlank { null } ?: "0.0.0-dev"

android {
    namespace = "io.github.impca201.trackerforsimkl"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.impca201.trackerforsimkl"
        minSdk = 26
        targetSdk = 37
        // Releases: the release workflow passes -PversionName (from the tag) and -PversionCode
        // (major * 10000 + minor * 100 + patch). Every other build describes its checkout instead
        // (e.g. "1.0.0-3-gabc1234"), so a development build never looks like a release.
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("versionName").getOrElse(gitVersion())
        // The app's SIMKL client ID (-PsimklClientId=..., from the SIMKL_CLIENT_ID Actions variable
        // in CI). It is not a secret; sign-in fails with a clear message in builds without it.
        buildConfigField("String", "SIMKL_CLIENT_ID", "\"${providers.gradleProperty("simklClientId").getOrElse("")}\"")
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("en", "nl", "fr", "de", "es", "it")
        // Lists the languages for Android 13+'s per-app language setting.
        generateLocaleConfig = true
    }

    testOptions {
        // Robolectric launches the real activity in the JVM unit tests (see LaunchTest).
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                showStandardStreams = false
            }
        }
    }

    dependenciesInfo {
        includeInApk = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
}
