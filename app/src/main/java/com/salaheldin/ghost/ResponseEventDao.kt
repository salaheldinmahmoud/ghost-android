package com.salaheldin.ghost

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ResponseEventDao {

    @Insert
    suspend fun insert(event: ResponseEventEntity): Long

    @Query("SELECT * FROM response_events WHERE conversationId = :conversationId ORDER BY repliedAt DESC")
    suspend fun getEventsForConversation(conversationId: Long): List<ResponseEventEntity>

    @Query("SELECT * FROM response_events")
    fun getAllEvents(): Flow<List<ResponseEventEntity>>

    @Query("SELECT MAX(repliedAt) FROM response_events WHERE conversationId = :conversationId")
    suspend fun getLastRepliedAt(conversationId: Long): Long?

    @Query("SELECT AVG(responseTimeMs) FROM response_events")
    suspend fun getAverageResponseTimeMs(): Long?

    @Query("SELECT COUNT(*) FROM response_events")
    suspend fun getTotalResponseEventCount(): Int
}