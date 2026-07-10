plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.androidkris.ide"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.androidkris.ide"
        minSdk = 26
        // targetSdk 28 (like Termux/AndroidIDE): required so the app may execute the
        // bootstrap toolchain (bash/JDK/Gradle) from its own data dir. Android 10+ (API 29+)
        // targets are denied exec on app_data_file (SELinux W^X), which would make on-device
        // builds impossible. Trade-off: legacy external-storage model (handled in StoragePermission).
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0-alpha"

        // The on-device build toolchain (Gradle/AAPT2/d8/JDK) only exists for arm64.
        // We keep the editor usable everywhere but restrict shipped native tooling to arm64.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
}

dependencies {
    implementation(project(":editor"))
    implementation(project(":terminal"))
    implementation(project(":build-engine"))
    implementation(project(":lsp"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
}
