package com.salaheldin.ghost

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("SELECT * FROM conversations WHERE contactIdentifier = :contactIdentifier LIMIT 1")
    suspend fun findByContact(contactIdentifier: String): ConversationEntity?

    /**
     * Atomic get-or-create. Removes the insert/read race that two notifications
     * arriving in the same millisecond could hit.
     */
    @Transaction
    suspend fun getOrCreate(conversation: ConversationEntity): ConversationEntity {
        val rowId = insert(conversation)
        return if (rowId != -1L) {
            conversation.copy(id = rowId)
        } else {
            findByContact(conversation.contactIdentifier)!!
        }
    }

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY lastMessageTime DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT COUNT(*) FROM conversations WHERE status = 'REPLIED'")
    suspend fun getRepliedCount(): Int

    /** Conversations that ever needed a reply — the correct denominator. */
    @Query("SELECT COUNT(*) FROM conversations WHERE status IN ('WAITING_FOR_REPLY', 'REPLIED')")
    suspend fun getReplyNeededConversationCount(): Int
}