plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9: kotlin-android plugin not needed (built-in Kotlin), alias stays in version catalog only.
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
    outputs.dir("app/src/main/jniLibs")
    outputs.upToDateWhen { false }
    commandLine("bash", "engine/byedpi/build.sh")
}
