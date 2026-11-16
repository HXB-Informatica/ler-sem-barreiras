package pt.lersembarreiras.core.packageformat

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pt.lersembarreiras.core.model.ContentPackage
import pt.lersembarreiras.core.model.ReadingContent

object ContentPackageMapper {
    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun toPackage(content: ReadingContent): ContentPackage = ContentPackage(
        id = content.id,
        title = content.title,
        text = content.text,
        audioFile = content.audioRelativePath?.substringAfterLast('/'),
        durationMs = content.durationMs,
        words = content.words,
        syncStatus = content.syncStatus,
        createdAtEpochMs = content.createdAtEpochMs,
        updatedAtEpochMs = content.updatedAtEpochMs,
    )

    fun fromPackage(pkg: ContentPackage, audioRelativePath: String?): ReadingContent = ReadingContent(
        id = pkg.id,
        title = pkg.title,
        text = pkg.text,
        audioRelativePath = audioRelativePath ?: pkg.audioFile,
        durationMs = pkg.durationMs,
        words = pkg.words,
        syncStatus = pkg.syncStatus,
        createdAtEpochMs = pkg.createdAtEpochMs,
        updatedAtEpochMs = pkg.updatedAtEpochMs,
    )

    fun encode(content: ReadingContent): String = json.encodeToString(toPackage(content))

    fun decode(raw: String): ContentPackage = json.decodeFromString(raw)
}
