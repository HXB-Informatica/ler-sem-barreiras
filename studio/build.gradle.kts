import java.io.File
import java.net.URI
import java.util.zip.ZipInputStream
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

val whisperWinTag = "v1.9.1"
val whisperWinZip = "https://github.com/ggml-org/whisper.cpp/releases/download/$whisperWinTag/whisper-bin-x64.zip"
val whisperModelName = "ggml-base.bin"
val whisperModelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$whisperModelName"
val whisperPackaging = layout.projectDirectory.dir("packaging/windows")

val fetchWhisperWindows = tasks.register("fetchWhisperWindows") {
    outputs.dir(whisperPackaging)
    outputs.upToDateWhen {
        val dir = whisperPackaging.asFile
        val model = dir.resolve(whisperModelName)
        val siblings = dir.listFiles()?.any {
            it.isFile && it.name.startsWith("ggml-") && it.name.endsWith(".bin") && it.name != whisperModelName
        } == true
        dir.resolve("whisper-cli.exe").exists() &&
            model.exists() && model.length() > 1_000_000 && !siblings
    }
    doLast {
        val dest = whisperPackaging.asFile
        dest.mkdirs()
        val exe = File(dest, "whisper-cli.exe")
        if (!exe.exists()) {
            logger.lifecycle("A obter whisper.cpp Windows $whisperWinTag (whisper-bin-x64.zip)…")
            val tmpZip = File(layout.buildDirectory.get().asFile, "whisper-bin-x64.zip")
            tmpZip.parentFile.mkdirs()
            URI(whisperWinZip).toURL().openStream().use { input ->
                tmpZip.outputStream().use { output -> input.copyTo(output) }
            }
            ZipInputStream(tmpZip.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = File(entry.name).name
                        val keep = name.equals("whisper-cli.exe", true) ||
                            name.equals("whisper.dll", true) ||
                            name.equals("ggml.dll", true) ||
                            name.startsWith("ggml-") && name.endsWith(".dll", true)
                        if (keep) {
                            File(dest, name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        val model = File(dest, whisperModelName)
        if (!model.exists() || model.length() < 1_000_000) {
            logger.lifecycle("A obter modelo $whisperModelName…")
            URI(whisperModelUrl).toURL().openStream().use { input ->
                model.outputStream().use { output -> input.copyTo(output) }
            }
        }
        dest.listFiles()
            ?.filter { it.isFile && it.name.startsWith("ggml-") && it.name.endsWith(".bin") && it.name != whisperModelName }
            ?.forEach {
                logger.lifecycle("A remover modelo antigo ${it.name}")
                it.delete()
            }
        logger.lifecycle("Whisper Windows em ${dest.path}")
    }
}

tasks.matching { it.name == "prepareAppResources" || it.name == "run" || it.name.startsWith("package") || it.name == "createDistributable" }
    .configureEach { dependsOn(fetchWhisperWindows) }

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
