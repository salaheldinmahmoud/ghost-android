package com.salaheldin.ghost

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        ResponseEventEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun responseEventDao(): ResponseEventDao

    companion object {
        private const val TAG = "GhostDatabase"
        private const val DB_NAME = "ghost_database"

        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * 7 -> 8: adds `awaitingSince` to conversations and the
         * indexes the list/stat queries depend on. Purely additive: no data loss.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN awaitingSince INTEGER NOT NULL DEFAULT 0")
                // Seed it so existing waiting rows aren't reported as instant replies.
                db.execSQL("UPDATE conversations SET awaitingSince = lastMessageTime WHERE status = 'WAITING_FOR_REPLY'")

                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_lastMessageTime ON conversations(lastMessageTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_status ON conversations(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_conversationId_timestamp ON messages(conversationId, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_response_events_conversationId ON response_events(conversationId)")

                // Orphan rows would violate the new foreign keys.
                db.execSQL("DELETE FROM messages WHERE conversationId NOT IN (SELECT id FROM conversations)")
                db.execSQL("DELETE FROM response_events WHERE conversationId NOT IN (SELECT id FROM conversations)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: try {
                    buildDatabase(context).also { INSTANCE = it }
                } catch (_: DatabaseKeyProvider.KeyDecryptionException) {
                    Log.e(TAG, "Database key decryption failed; secure database recovery required.")

                    INSTANCE?.close()
                    INSTANCE = null

                    Log.w(TAG, "Deleting unrecoverable local database.")
                    context.deleteDatabase(DB_NAME)
                    DatabaseKeyProvider.clearKeyMaterial(context)

                    // Retry once. If this fails again, let it propagate — no infinite loop.
                    buildDatabase(context).also {
                        INSTANCE = it
                        Log.i(TAG, "Database recovery completed.")
                    }
                }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            // SQLCipher's native library must be loaded before any DB operation.
            System.loadLibrary("sqlcipher")

            val passphrase = DatabaseKeyProvider.getOrCreatePassphrase(context)
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME,
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_7_8)
                .build()
        }
    }
}