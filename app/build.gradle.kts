plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    aaptOptions {
        noCompress("tflite")
    }
    namespace = "com.example.myapplication"
    // compileSdk 36 exigido pelas dependencias activity:1.12.3, core:1.17.0 e compose-bom 2025.05
    // O problema anterior de manifest merger foi causado pelo task-vision (ja removido).
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        // targetSdk pode ser diferente do compileSdk:
        // compileSdk=36 = podemos USAR simbolos da API 36
        // targetSdk=35  = comportamento de runtime permanece no Android 15
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        mlModelBinding = true
    }
}

dependencies {
    // ── TFLite ──────────────────────────────────────────────────────────────
    // NOTA: tensorflow-lite-task-vision foi REMOVIDO intencionalmente.
    // Essa lib de 2022 declara targetSdk<4 em seu AndroidManifest, o que quebra
    // o manifest merger com AGP 9 / compileSdk 35. Os tipos Detection e Category
    // foram substituídos por data classes próprias em DetectionResult.kt.
    // Interpreter direto para inferência YOLO raw
    implementation("org.tensorflow:tensorflow-lite:2.16.1") {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-support-api")
    }
    // GPU delegate (opcional — melhora performance em dispositivos compatíveis)
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")

    // ── Google Services ─────────────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // ── AndroidX / Compose ──────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ── CameraX ─────────────────────────────────────────────────────────────
    val cameraxVersion = "1.5.3"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ── Imagens ─────────────────────────────────────────────────────────────
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ── Ícones Material estendidos ───────────────────────────────────────────
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // ── Testes ──────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

