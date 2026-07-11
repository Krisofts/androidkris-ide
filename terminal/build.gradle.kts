plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.androidkris.ide.terminal"
    compileSdk = 35
    ndkVersion = "26.1.10909125"

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += "arm64-v8a"
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
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Termux terminal emulator + view (pulls terminal-emulator transitively; ships arm64 JNI).
    // NOTE: do NOT add the guava `listenablefuture:9999.0-empty-to-avoid-conflict-with-guava`
    // artifact here — terminal-view pulls no Guava, so that empty stub only *removes* the real
    // ListenableFuture class and breaks androidx.concurrent.futures / ProfileInstaller at runtime.
    implementation(libs.termux.terminal.view)
}
