import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // JVM screenshot tests of the Compose UI (no device): ./gradlew :app:recordPaparazziDebug
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.gesturecontrol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gesturecontrol"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Phones (arm) plus x86_64 for the emulator; drops 32-bit x86 MediaPipe libraries.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
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
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // Models must stay uncompressed so MediaPipe / LiteRT can memory-map them.
        noCompress += listOf("task", "tflite")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Downloads the MediaPipe hand landmarker model once; it is not committed to git.
val downloadHandModel by tasks.registering {
    val modelFile = layout.projectDirectory.file("src/main/assets/hand_landmarker.task").asFile
    outputs.file(modelFile)
    onlyIf { !modelFile.exists() }
    doLast {
        modelFile.parentFile.mkdirs()
        val url = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
        URI(url).toURL().openStream().use { input -> modelFile.outputStream().use { input.copyTo(it) } }
    }
}
tasks.named("preBuild") { dependsOn(downloadHandModel) }

dependencies {
    implementation(project(":gestures"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.litert)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
