plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.androidkris.ide.editor"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        // language-textmate needs Java 8+ APIs desugared for minSdk < 33.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)

    // Sora Editor — high-performance code editing surface.
    implementation(platform(libs.sora.editor.bom))
    implementation(libs.sora.editor.core)
    // TextMate: highlighting for Java/Kotlin/XML via bundled grammars in assets/textmate/.
    implementation(libs.sora.language.textmate)
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
}
