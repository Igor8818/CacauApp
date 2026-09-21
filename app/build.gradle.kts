plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    aaptOptions {
        noCompress("tflite")
    }
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 36
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
    // Bibliotecas TFLite
    // task-vision: mantido APENAS para o tipo Detection (não usamos ObjectDetector)
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    // Interpreter direto para inferência YOLO raw
    implementation("org.tensorflow:tensorflow-lite:2.16.1") {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-support-api")
    }
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // 2. CORREÇÃO: Usamos a versão 2.16.1 que resolve o conflito de namespace
    // Ou adicionamos uma "exclusão" para evitar o erro que você recebeu
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Configuração do CameraX (Versão 1.5.3 sugerida pelo Android Studio)
    val cameraxVersion = "1.5.3"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Biblioteca Coil para visualizar as fotos
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Ícones estendidos (Resolve o erro do CameraAlt)
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}





