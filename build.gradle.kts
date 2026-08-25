plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9: kotlin-android plugin not needed (built-in Kotlin), alias stays in version catalog only.
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.register<Exec>("buildMihomoAar") {
    // Кеш: пересобираем AAR только когда меняются исходники движка.
    inputs.dir("engine")
    outputs.file("engine/libs/engine.aar")
    commandLine("bash", "engine/mihomo/build.sh")
}
