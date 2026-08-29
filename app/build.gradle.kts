plugins {
    alias(libs.plugins.android.application)
    // AGP 9 built-in Kotlin; explicit org.jetbrains.kotlin.android plugin must NOT be applied.
    alias(libs.plugins.kotlin.compose)
}

val releaseAbi = providers.gradleProperty("detourReleaseAbi").orNull
val supportedReleaseAbis = setOf("arm64-v8a", "x86_64")
require(releaseAbi == null || releaseAbi in supportedReleaseAbis) {
    "Unsupported detourReleaseAbi=$releaseAbi; expected one of ${supportedReleaseAbis.sorted()}"
}

val versionNameOverride = providers.gradleProperty("detourVersionName").orNull
require(versionNameOverride == null || versionNameOverride.isNotBlank()) {
    "detourVersionName must not be blank"
}
val versionCodeProperty = providers.gradleProperty("detourVersionCode").orNull
val versionCodeOverride = versionCodeProperty?.toIntOrNull()
require(versionCodeProperty == null || (versionCodeOverride != null && versionCodeOverride in 1..2_100_000_000)) {
    "detourVersionCode must be an integer from 1 through 2100000000"
}

val releaseKeystorePath = providers.environmentVariable("DETOUR_RELEASE_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("DETOUR_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("DETOUR_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("DETOUR_RELEASE_KEY_PASSWORD").orNull
val releaseSigningValueCount = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).count { !it.isNullOrBlank() }
require(releaseSigningValueCount == 0 || releaseSigningValueCount == 4) {
    "Release signing requires DETOUR_RELEASE_KEYSTORE, DETOUR_RELEASE_STORE_PASSWORD, " +
        "DETOUR_RELEASE_KEY_ALIAS and DETOUR_RELEASE_KEY_PASSWORD together"
}

android {
    namespace = "dev.triplet.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.triplet.app"
        minSdk = 29
        targetSdk = 36
        versionCode = versionCodeOverride ?: 1
        versionName = versionNameOverride ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Normal builds keep both ABIs for emulator/device coverage. Distribution
        // release builds may opt into one ABI without changing debug/androidTest packaging.
        releaseAbi?.let { abi ->
            ndk { abiFilters += abi }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    signingConfigs {
        if (releaseSigningValueCount == 4) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    // Бинарник ByeDPI должен лежать распакованным файлом в nativeLibraryDir.
    packaging { jniLibs { useLegacyPackaging = true } }

    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(rootProject.tasks.named("buildMihomoAar"))
        dependsOn(rootProject.tasks.named("buildByeDpi"))
    }

    lint {
        // compileSdk 37 / targetSdk 36 are deliberate: use current AndroidX APIs
        // without changing target-SDK behavior in the same maintenance pass.
        // QUERY_ALL_PACKAGES remains intentionally absent; ambiguous shared UIDs
        // fail closed before any TUN side effects.
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
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.snakeyaml)
    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestDebugImplementation(libs.androidx.compose.ui.test.manifest)
}
