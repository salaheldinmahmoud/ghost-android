package com.salaheldin.ghost

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ResponseEventDao {

    @Insert
    suspend fun insert(event: ResponseEventEntity): Long

    @Query("SELECT * FROM response_events WHERE conversationId = :conversationId ORDER BY repliedAt DESC")
    suspend fun getEventsForConversation(conversationId: Long): List<ResponseEventEntity>
}