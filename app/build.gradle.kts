plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt") // 🔴 AÑADIDO: Vital para procesar anotaciones de Prism4j (y Room)
}

android {
    namespace = "com.modulaappr1"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.modulaappr1"
        minSdk = 26 // Android 8.0 mínimo
        targetSdk = 34
        versionCode = 1
        versionName = "1.0-Forja"
    }

    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    // Le decimos a Gradle dónde buscar tu motor compilado en C++
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        jniLibs {
            // Vital para que el OS pueda hacer dlopen() nativo
            useLegacyPackaging = true 
        }
    }
}

dependencies {
    // Jetpack Compose Base
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // ViewModel y Corrutinas
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Markwon (Markdown, Matemáticas y Código)
    val markwon_version = "4.6.2"
    implementation("io.noties.markwon:core:$markwon_version")
    implementation("io.noties.markwon:ext-math:$markwon_version") // LaTeX
    implementation("io.noties.markwon:syntax-highlight:$markwon_version") // Plugin de código
    implementation("io.noties.markwon:ext-tables:$markwon_version") // Tablas
    
    // Prism4j (El motor de coloreado de código y su generador)
    implementation("io.noties.prism4j:prism4j:2.0.0")
    kapt("io.noties.prism4j:bundler:2.0.0") // 🔴 AÑADIDO: El generador de gramáticas
    
    /* 
    // 🟢 DESCOMENTA ESTO SI YA INCLUISTE EL ARCHIVO 'RAGDatabase.kt'
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")
    */
}