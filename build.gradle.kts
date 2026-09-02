plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    // AGP 9: kotlin-android plugin is not needed; Kotlin is built into AGP.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

val goVersion = providers.exec {
    commandLine("go", "version")
}.standardOutput.asText

val engineGoSources = fileTree("engine/mihomo/go") {
    include("*.go")
    exclude("*_test.go")
}

tasks.register<Exec>("buildMihomoAar") {
    // Engine output depends on every production bridge source, module metadata,
    // the embedding script and the exact Go toolchain. Keeping this source set
    // aligned with build.sh prevents stale AARs after adding bridge files.
    inputs.files("engine/mihomo/build.sh", "engine/mihomo/go/go.mod", "engine/mihomo/go/go.sum")
    inputs.files(engineGoSources)
    inputs.property("goVersion", goVersion)
    outputs.file("engine/libs/engine.aar")
    commandLine("bash", "engine/mihomo/build.sh")
}

tasks.register<Exec>("buildByeDpi") {
    inputs.files("engine/byedpi/build.sh", "engine/byedpi/apply_socks_auth.py")
    // A different NDK may produce different binaries even with the same script.
    inputs.property("androidNdkHome", providers.environmentVariable("ANDROID_NDK_HOME").orElse(""))
    outputs.dir("app/src/main/jniLibs")
    commandLine("bash", "engine/byedpi/build.sh")
}
