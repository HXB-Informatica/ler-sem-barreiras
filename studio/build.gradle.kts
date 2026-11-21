import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "pt.lersembarreiras.studio.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "LerSemBarreirasStudio"
            packageVersion = "0.5.0"
            description = "Ler sem Barreiras Studio — preparação de conteúdos offline"
            vendor = "Escola Gil Vicente"
            copyright = "GPL-3.0-only"
            appResourcesRootDir.set(project.layout.projectDirectory.dir("packaging"))
            windows {
                dirChooser = true
                menuGroup = "Ler sem Barreiras"
                upgradeUuid = "8c3e1d2a-4b7f-4c91-9e0a-2f6d18b4a771"
            }
        }
    }
}
