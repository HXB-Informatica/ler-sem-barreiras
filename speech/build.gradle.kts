import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val whisperTag = "v1.9.5"
val whisperZipUrl = "https://github.com/ggml-org/whisper.cpp/archive/refs/tags/$whisperTag.zip"
val modelFileName = providers.gradleProperty("whisperModel").orElse("ggml-tiny.bin")
val modelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/${modelFileName.get()}"

val whisperSrcDir = layout.projectDirectory.dir("src/main/cpp/third_party/whisper.cpp")
val modelDir = layout.projectDirectory.dir("src/main/assets/models")
val modelFile = modelDir.file(modelFileName.get())

val fetchWhisperCpp = tasks.register("fetchWhisperCpp") {
    outputs.dir(whisperSrcDir)
    outputs.upToDateWhen { whisperSrcDir.asFile.resolve("CMakeLists.txt").exists() }
    doLast {
        val dest = whisperSrcDir.asFile
        if (dest.resolve("CMakeLists.txt").exists()) {
            logger.lifecycle("whisper.cpp já está presente em ${dest.path}")
            return@doLast
        }
        dest.parentFile.mkdirs()
        val tmpZip = File(layout.buildDirectory.get().asFile, "whisper.cpp-$whisperTag.zip")
        tmpZip.parentFile.mkdirs()
        logger.lifecycle("A obter whisper.cpp $whisperTag…")
        URI(whisperZipUrl).toURL().openStream().use { input ->
            tmpZip.outputStream().use { output -> input.copyTo(output) }
        }
        val unzipRoot = File(layout.buildDirectory.get().asFile, "whisper-unzip")
        unzipRoot.deleteRecursively()
        unzipRoot.mkdirs()
        ZipInputStream(tmpZip.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val out = File(unzipRoot, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val extracted = unzipRoot.listFiles()?.firstOrNull { it.isDirectory }
            ?: error("Não foi possível extrair whisper.cpp.")
        dest.deleteRecursively()
        extracted.copyRecursively(dest, overwrite = true)
        logger.lifecycle("whisper.cpp extraído para ${dest.path}")
    }
}

val fetchWhisperModel = tasks.register("fetchWhisperModel") {
    outputs.file(modelFile)
    outputs.upToDateWhen { modelFile.asFile.exists() && modelFile.asFile.length() > 1_000_000 }
    doLast {
        val dest = modelFile.asFile
        if (dest.exists() && dest.length() > 1_000_000) {
            logger.lifecycle("Modelo ${dest.name} já está presente.")
            return@doLast
        }
        dest.parentFile.mkdirs()
        logger.lifecycle("A obter modelo Whisper ${dest.name}…")
        URI(modelUrl).toURL().openStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        logger.lifecycle("Modelo gravado em ${dest.path} (${dest.length()} bytes)")
    }
}

android {
    namespace = "pt.lersembarreiras.speech"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-O3", "-fvisibility=hidden", "-std=c++17")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_OPENMP=OFF",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF",
                    "-DWHISPER_BUILD_SERVER=OFF",
                    "-DBUILD_SHARED_LIBS=OFF",
                )
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}

afterEvaluate {
    tasks.matching { it.name.contains("CMake", ignoreCase = true) || it.name.contains("externalNative", ignoreCase = true) }
        .configureEach { dependsOn(fetchWhisperCpp) }
    tasks.matching { it.name.contains("Assets") || it.name == "preBuild" }
        .configureEach { dependsOn(fetchWhisperModel) }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
