package pt.lersembarreiras.mobile

internal object WhisperJni {
    val available: Boolean = runCatching {
        System.loadLibrary("lersembarreiras_whisper")
        true
    }.getOrDefault(false)

    external fun nativeInit(modelPath: String, nThreads: Int): Long
    external fun nativeFree(handle: Long)
    external fun nativeCancel(handle: Long)
    external fun nativeProgress(handle: Long): Int
    external fun nativeTranscribe(
        handle: Long,
        samples: FloatArray,
        language: String,
        prompt: String?,
    ): String
}
