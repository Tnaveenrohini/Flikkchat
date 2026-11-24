import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase config (app/google-services.json) unte matrame Firebase plugin on avutundi.
// Lekapothe app build avutundi kani "Setup needed" screen chupistundi.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.flikk.chat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.flikk.chat"
        minSdk = 26
        targetSdk = 34
        versionCode = 18
        versionName = "0.20.1"
        // Phones ki matrame (APK size takkuva)
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Fixed key: kotha build paata app meedha update ga install avutundi.
    signingConfigs {
        create("flikkchat") {
            storeFile = file("flikkchat.keystore")
            storePassword = "flikkchat123"
            keyAlias = "flikkchat"
            keyPassword = "flikkchat123"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("flikkchat") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("flikkchat")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // 🗜️ Video / audio compress (send mundu)
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // 📞 Voice / video calls
    implementation("io.getstream:stream-webrtc-android:1.3.8")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
