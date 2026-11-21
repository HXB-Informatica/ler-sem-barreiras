package pt.lersembarreiras.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.encodeToString
import pt.lersembarreiras.alignment.WordIdentity
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.core.packageformat.ContentPackageMapper

@Entity(tableName = "contents")
data class ContentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val text: String,
    val audioRelativePath: String?,
    val durationMs: Long,
    val wordsJson: String,
    val syncStatus: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    fun toModel(): ReadingContent = ReadingContent(
        id = id,
        title = title,
        text = text,
        audioRelativePath = audioRelativePath,
        durationMs = durationMs,
        words = WordIdentity.ensure(text, ContentPackageMapper.json.decodeFromString<List<WordTiming>>(wordsJson)),
        syncStatus = SyncStatus.valueOf(syncStatus),
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    companion object {
        fun from(content: ReadingContent): ContentEntity = ContentEntity(
            id = content.id,
            title = content.title,
            text = content.text,
            audioRelativePath = content.audioRelativePath,
            durationMs = content.durationMs,
            wordsJson = ContentPackageMapper.json.encodeToString(WordIdentity.ensure(content.text, content.words)),
            syncStatus = content.syncStatus.name,
            createdAtEpochMs = content.createdAtEpochMs,
            updatedAtEpochMs = content.updatedAtEpochMs,
        )
    }
}
