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
val mihomoRevision = providers.exec {
    commandLine("git", "-C", "third_party/mihomo", "rev-parse", "HEAD")
}.standardOutput.asText
val byeDpiRevision = providers.exec {
    commandLine("git", "-C", "third_party/byedpi", "rev-parse", "HEAD")
}.standardOutput.asText

val engineGoSources = fileTree("engine/mihomo/go") {
    include("*.go")
    exclude("*_test.go")
}
val engineGoVendor = fileTree("engine/mihomo/go/vendor") {
    include("**/*")
}

tasks.register<Exec>("buildMihomoAar") {
    // The native engine build is self-contained: pinned source submodule,
    // committed Go vendor graph, pinned Go toolchain and Android NDK.
    inputs.files(
        "engine/mihomo/build.sh",
        "engine/mihomo/build-offline.sh",
        "engine/mihomo/go/go.mod",
        "engine/mihomo/go/go.sum",
        "engine/mihomo/go/tools.go",
    )
    inputs.files(engineGoSources)
    inputs.files(engineGoVendor)
    inputs.property("goVersion", goVersion)
    inputs.property("mihomoRevision", mihomoRevision)
    inputs.property("androidNdkHome", providers.environmentVariable("ANDROID_NDK_HOME").orElse(""))
    outputs.file("engine/libs/engine.aar")
    commandLine("bash", "engine/mihomo/build-offline.sh")
}

tasks.register<Exec>("buildByeDpi") {
    inputs.files(
        "engine/byedpi/build.sh",
        "engine/byedpi/build-offline.sh",
        "engine/byedpi/apply_socks_auth.py",
    )
    inputs.property("byeDpiRevision", byeDpiRevision)
    // A different NDK may produce different binaries even with the same script.
    inputs.property("androidNdkHome", providers.environmentVariable("ANDROID_NDK_HOME").orElse(""))
    outputs.dir("app/src/main/jniLibs")
    commandLine("bash", "engine/byedpi/build-offline.sh")
}
