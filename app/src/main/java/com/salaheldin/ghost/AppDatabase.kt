package com.salaheldin.ghost

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        ResponseEventEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun responseEventDao(): ResponseEventDao

    companion object {
        private const val TAG = "GhostDatabase"
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: try {
                    buildDatabase(context).also {
                        INSTANCE = it
                        debugDatabaseInspect(it)
                    }
                } catch (_: DatabaseKeyProvider.KeyDecryptionException) {
                    Log.e(TAG, "Database key decryption failed; secure database recovery required.")

                    // Close instance if it somehow exists (though unlikely here)
                    INSTANCE?.close()
                    INSTANCE = null

                    Log.w(TAG, "Deleting unrecoverable local database.")
                    context.deleteDatabase("ghost_database")

                    DatabaseKeyProvider.clearKeyMaterial(context)

                    // Retry once. If this fails again, let the exception propagate to avoid infinite loops.
                    val newDb = buildDatabase(context)
                    Log.i(TAG, "Database recovery completed.")
                    newDb.also {
                        INSTANCE = it
                        debugDatabaseInspect(it)
                    }
                }
            }
        }

        private fun debugDatabaseInspect(db: AppDatabase) {
            try {
                val sdb = db.openHelper.readableDatabase
                Log.d(TAG, "--- DEBUG: Database Inspection Start ---")
                Log.d(TAG, "Database path: ${sdb.path}")

                // 1. List all tables
                val cursor = sdb.query("SELECT name FROM sqlite_master WHERE type='table'")
                val tables = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    tables.add(cursor.getString(0))
                }
                cursor.close()
                Log.d(TAG, "Tables present: ${tables.joinToString(", ")}")



                Log.d(TAG, "--- DEBUG: Database Inspection End ---")
            } catch (e: Exception) {
                Log.e(TAG, "Debug inspection failed: ${e.message}")
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