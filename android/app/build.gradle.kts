plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tesis.plagasia"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tesis.plagasia"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Configuración de ABI (Application Binary Interface):
        // Se fuerza el uso de la arquitectura 'armeabi-v7a' (32 bits) para garantizar
        // la compatibilidad con dispositivos específicos y librerías nativas de TensorFlow,
        // mitigando problemas de alineación de memoria (ej. error 16 KB page size).
        ndk {
            abiFilters.add("armeabi-v7a")
        }
    }

    // Optimización de empaquetado de Assets:
    // Se instruye a aapt (Android Asset Packaging Tool) para que NO comprima los archivos .tflite.
    // Esto permite que el modelo sea mapeado directamente en memoria (mmap) durante la inferencia,
    // reduciendo la latencia de carga y el consumo de RAM.
    aaptOptions {
        noCompress += "tflite"
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

    packaging {
        jniLibs {
            // Mantenimiento de compatibilidad con librerías nativas antiguas.
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // --- LIBRERÍAS DEL NÚCLEO DE ANDROID ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // --- INTERFAZ DE USUARIO (JETPACK COMPOSE) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // --- MOTOR DE INFERENCIA (TENSORFLOW LITE) ---
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)

    // --- GESTIÓN DE CÁMARA (CAMERAX) ---
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // --- GESTIÓN DE PERMISOS ---
    implementation(libs.accompanist.permissions)

    // --- PRUEBAS Y DEPURACIÓN ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}