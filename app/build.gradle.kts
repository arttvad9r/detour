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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }

    // Бинарник ByeDPI должен лежать распакованным файлом в nativeLibraryDir.
    packaging { jniLibs { useLegacyPackaging = true } }

    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(rootProject.tasks.named("buildMihomoAar"))
        dependsOn(rootProject.tasks.named("buildByeDpi"))
    }

    lint {
        // compileSdk/targetSdk 36 закреплены в android-блоке; CI явно ставит
        // platform 36 и build-tools 36.0.0. QUERY_ALL_PACKAGES не добавляем намеренно:
        // если ownership shared UID скрыт package visibility, EffectiveRoutes
        // отклоняет неоднозначный UID до любых TUN side effects (fail-closed).
        disable += listOf(
            "OldTargetApi",
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
            "QueryPermissionsNeeded",
        )
    }
}

// AGP 9 built-in Kotlin targets Java 17 via compileOptions above (no kotlin-android plugin).
dependencies {
    testImplementation(libs.org.json)
    implementation(files("../engine/libs/engine.aar"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.snakeyaml)
    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestDebugImplementation(libs.androidx.compose.ui.test.manifest)
}
