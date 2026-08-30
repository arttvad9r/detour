plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9: kotlin-android plugin is not needed; Kotlin is built into AGP.
    alias(libs.plugins.kotlin.compose) apply false
}

val goVersion = providers.exec {
    commandLine("go", "version")
}.standardOutput.asText

tasks.register<Exec>("buildMihomoAar") {
    // Engine output depends on both its sources and the exact Go toolchain.
    inputs.files("engine/mihomo/build.sh", "engine/mihomo/go/go.mod", "engine/mihomo/go/go.sum", "engine/mihomo/go/engine.go")
    inputs.property("goVersion", goVersion)
    outputs.file("engine/libs/engine.aar")
    commandLine("bash", "engine/mihomo/build.sh")
}

tasks.register<Exec>("buildByeDpi") {
    inputs.files(
        "engine/byedpi/build.sh",
        "engine/byedpi/patch_auth.py",
        "engine/byedpi/auth_smoke.py",
    )
    // A different NDK may produce different binaries even with the same sources.
    inputs.property("androidNdkHome", providers.environmentVariable("ANDROID_NDK_HOME").orElse(""))
    outputs.dir("app/src/main/jniLibs")
    commandLine("bash", "engine/byedpi/build.sh")
}
