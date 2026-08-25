plugins {
    alias(libs.plugins.android.application)
    // AGP 9 built-in Kotlin; explicit org.jetbrains.kotlin.android plugin must NOT be applied.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.triplet.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.triplet.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    // Бинарник ByeDPI должен лежать распакованным файлом в nativeLibraryDir.
    packaging { jniLibs { useLegacyPackaging = true } }

    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(rootProject.tasks.named("buildMihomoAar"))
    }
}

// AGP 9 built-in Kotlin targets Java 17 via compileOptions above (no kotlin-android plugin).
// AAR движка появится в Task 2; до этого строку закомментировать нельзя —
// поэтому Task 2 выполняется сразу за этим и preBuild-зависимость включается там.
dependencies {
    testImplementation("org.json:json:20240303")
    implementation(files("../engine/libs/engine.aar"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
}
