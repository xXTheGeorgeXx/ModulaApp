plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.modulaappr1"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.modulaappr1"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            // Solo arm64-v8a: el Galaxy A52 es ARM64 y el .so solo existe para esa ABI.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        jniLibs {
            // El .so ya viene precompilado en src/main/jniLibs; no reempaquetar duplicados.
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // Sin dependencias externas a propósito: solo android.jar + kotlin-stdlib.
    // Así el proyecto también se puede compilar a mano (aapt2 + kotlinc + d8)
    // en máquinas donde Gradle no puede correr su daemon (ver README).
}
