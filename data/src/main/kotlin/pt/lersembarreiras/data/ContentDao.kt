package pt.lersembarreiras.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Query("SELECT * FROM contents ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM contents WHERE id = :id")
    suspend fun getById(id: String): ContentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ContentEntity)

    @Query("DELETE FROM contents WHERE id = :id")
    suspend fun delete(id: String)
}
