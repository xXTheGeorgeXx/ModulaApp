plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt") 
}

android {
    namespace = "com.modulaappr1"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.modulaappr1"
        minSdk = 26 
        targetSdk = 34
        versionCode = 1
        versionName = "1.0-Forja"
    }

    // =========================================================
    // 🟢 EL FIX MÁGICO: Todo el proyecto habla Java 17
    // =========================================================
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // =========================================================

    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true 
        }
    }
}

configurations.all {
    exclude(group = "org.jetbrains", module = "annotations-java5")
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
    
    // Markwon (Markdown, Tablas y Código)
    val markwon_version = "4.6.2"
    implementation("io.noties.markwon:core:$markwon_version")
    implementation("io.noties.markwon:syntax-highlight:$markwon_version") 
    implementation("io.noties.markwon:ext-tables:$markwon_version") 
    
    // Prism4j (El motor de coloreado de código y su generador)
    implementation("io.noties:prism4j:2.0.0")          
    kapt("io.noties:prism4j-bundler:2.0.0")            
  
    // BASE DE DATOS VECTORIAL (RAG)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")
}