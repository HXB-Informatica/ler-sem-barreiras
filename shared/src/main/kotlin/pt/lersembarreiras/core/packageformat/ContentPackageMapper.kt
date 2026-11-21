package pt.lersembarreiras.core.packageformat

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pt.lersembarreiras.alignment.WordIdentity
import pt.lersembarreiras.core.model.ContentPackage
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.shared.ProjectSerializer

object ContentPackageMapper : ProjectSerializer {
    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override fun toPackage(content: ReadingContent): ContentPackage {
        val words = WordIdentity.ensure(content.text, content.words)
        return ContentPackage(
            id = content.id,
            title = content.title,
            text = content.text,
            audioFile = content.audioRelativePath?.substringAfterLast('/'),
            durationMs = content.durationMs,
            words = words,
            syncStatus = content.syncStatus,
            createdAtEpochMs = content.createdAtEpochMs,
            updatedAtEpochMs = content.updatedAtEpochMs,
        )
    }

    override fun fromPackage(pkg: ContentPackage, audioRelativePath: String?): ReadingContent = ReadingContent(
        id = pkg.id,
        title = pkg.title,
        text = pkg.text,
        audioRelativePath = audioRelativePath ?: pkg.audioFile,
        durationMs = pkg.durationMs,
        words = WordIdentity.ensure(pkg.text, pkg.words),
        syncStatus = pkg.syncStatus,
        createdAtEpochMs = pkg.createdAtEpochMs,
        updatedAtEpochMs = pkg.updatedAtEpochMs,
    )

    override fun encode(content: ReadingContent): String = json.encodeToString(toPackage(content))

    override fun decode(raw: String): ContentPackage {
        val pkg = json.decodeFromString<ContentPackage>(raw)
        return pkg.copy(words = WordIdentity.ensure(pkg.text, pkg.words))
    }
}
