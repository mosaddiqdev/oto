import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release signing comes from the environment (CI sets it from GitHub secrets). Without it the
// build falls back to the debug key, so local and pull-request builds still work. An APK signed
// with the fallback key cannot update an installed Oto, and CI refuses to publish one from main.
val otoKeystore: File? = providers.environmentVariable("OTO_KEYSTORE_PATH").orNull
    ?.let { file(it) }
    ?.takeIf { it.exists() }
val otoKeystorePassword: String? = providers.environmentVariable("OTO_KEYSTORE_PASSWORD").orNull
    ?.takeIf { it.isNotEmpty() }

android {
    namespace = "com.oto.stats"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.oto.stats"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "0.13.0"
    }

    signingConfigs {
        if (otoKeystore != null && otoKeystorePassword != null) {
            create("oto") {
                storeFile = otoKeystore
                storeType = "pkcs12"
                storePassword = otoKeystorePassword
                keyAlias = "oto"
                keyPassword = otoKeystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("oto") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

// Room writes one JSON file per database version here. They are committed, so a schema change
// without a matching migration is caught by CI instead of crashing on a user's phone.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.work.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.backdrop) // Liquid Glass, Apache-2.0: github.com/Kyant0/AndroidLiquidGlass
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
}
