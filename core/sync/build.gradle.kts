plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.johnyrahul.investmenttracker.core.sync"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:data"))
    implementation(libs.pdfbox.android)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.core)
}
