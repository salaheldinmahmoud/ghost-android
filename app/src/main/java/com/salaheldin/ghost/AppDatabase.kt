package com.salaheldin.ghost

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [MessageEntity::class, ConversationEntity::class, ResponseEventEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun responseEventDao(): ResponseEventDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            // Load SQLCipher's native library before any database operation
            System.loadLibrary("sqlcipher")

            val passphrase = DatabaseKeyProvider.getOrCreatePassphrase(context)
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ghost_database",
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}