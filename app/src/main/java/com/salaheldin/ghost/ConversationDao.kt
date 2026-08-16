package com.salaheldin.ghost

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("SELECT * FROM conversations WHERE contactIdentifier = :contactIdentifier LIMIT 1")
    suspend fun findByContact(contactIdentifier: String): ConversationEntity?

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY lastMessageTime DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT COUNT(*) FROM conversations WHERE status = 'REPLIED'")
    suspend fun getRepliedCount(): Int
}