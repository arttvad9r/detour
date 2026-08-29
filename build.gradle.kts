plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9: kotlin-android plugin is not needed; Kotlin is built into AGP.
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.register<Exec>("buildMihomoAar") {
    // Кеш: пересобираем AAR только когда меняются исходники движка.
    inputs.files("engine/mihomo/build.sh", "engine/mihomo/go/go.mod", "engine/mihomo/go/go.sum", "engine/mihomo/go/engine.go")
    outputs.file("engine/libs/engine.aar")
    commandLine("bash", "engine/mihomo/build.sh")
}

tasks.register<Exec>("buildByeDpi") {
    inputs.file("engine/byedpi/build.sh")
    // A different NDK may produce different binaries even with the same script.
    inputs.property("androidNdkHome", providers.environmentVariable("ANDROID_NDK_HOME").orElse(""))
    outputs.dir("app/src/main/jniLibs")
    commandLine("bash", "engine/byedpi/build.sh")
}
