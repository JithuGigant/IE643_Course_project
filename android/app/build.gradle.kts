import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The exported model bundle is used straight from the export folder (see gradle.properties).
val bundleDir: File = rootProject.file(providers.gradleProperty("bundleDir").getOrElse("../IE643 Deployment Notebook output/app_bundle"))
val requiredBundleFiles = listOf("app_config.json", "encoder.onnx", "vocab.json", "samples/samples.json")
for (name in requiredBundleFiles) {
    if (!File(bundleDir, name).exists()) {
        throw GradleException(
            "Model bundle file '$name' not found in ${bundleDir.absolutePath}. " +
                "Set bundleDir in gradle.properties to the app_bundle folder written by ie643_export.ipynb."
        )
    }
}
if (!File(bundleDir, "decoder_f16w.onnx").exists() && !File(bundleDir, "decoder_fp32.onnx").exists()) {
    throw GradleException("No decoder .onnx file in ${bundleDir.absolutePath}.")
}

android {
    namespace = "com.ie643.cxr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ie643.cxr"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so that `assembleRelease` gives an APK you can sideload without a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    // Models are read as raw bytes: keep them uncompressed in the APK.
    androidResources { noCompress += listOf("onnx", "bin") }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", bundleDir)
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
}
