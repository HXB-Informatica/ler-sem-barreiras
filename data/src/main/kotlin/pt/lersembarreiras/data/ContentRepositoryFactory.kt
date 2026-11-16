package pt.lersembarreiras.data

import android.content.Context

object ContentRepositoryFactory {
    fun create(context: Context): ContentRepository {
        val database = AppDatabase.create(context.applicationContext)
        return ContentRepository(context.applicationContext, database.contentDao())
    }
}
