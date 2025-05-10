plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.example.smartlens"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smartlens"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Configuración para OpenCV (asegurarse de incluir todas las arquitecturas)
        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86")
            abiFilters.add("x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Evitar duplicados en archivos de OpenCV y otras bibliotecas
            pickFirsts.add("**/*.so")
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")

    // AppCompat (para ThemeManager)
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Coil para cargar imágenes
    implementation("io.coil-kt:coil-compose:2.4.0")

    // ML Kit para OCR
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Google AI para Gemini
    implementation("com.google.ai.client.generativeai:generativeai:0.1.2")

    // Apache POI para Excel
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // ZXing para códigos QR
    implementation("com.google.zxing:core:3.5.1")

    // WorkManager para procesamiento en segundo plano
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("androidx.hilt:hilt-work:1.1.0")

    // LiveData para observar resultados de Work
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")

    // OpenCV
    // Opción 1: Usar una implementación probada de OpenCV para Android
    implementation("com.quickbirdstudios:opencv:4.5.3.0")

    // Opción 2 (alternativa): Otra implementación de OpenCV desde Maven
    // Solo descomenta esta si la anterior da problemas
    // implementation("org.bytedeco:opencv:4.6.0-1.5.8")
    // implementation("org.bytedeco:opencv-platform:4.6.0-1.5.8")

    // Opciones 3 y 4 (otras alternativas)
    // Descomenta solo una de estas si las anteriores fallan
    // implementation("io.github.hadilq.opencv:opencv:4.5.0")
    // implementation("ai.onnxruntime:onnxruntime-android:1.15.1")

    // EasyPermissions para manejo de permisos
    implementation("pub.devrel:easypermissions:3.0.0")

    // Multidex para manejar el límite de métodos DEX
    implementation("androidx.multidex:multidex:2.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Material Components
    implementation("com.google.android.material:material:1.10.0")
}

// Permite usar @HiltViewModel
kapt {
    correctErrorTypes = true
    useBuildCache = false
    javacOptions {
        option("-Xmaxerrs", 1000)
    }
}