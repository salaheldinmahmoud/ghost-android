package com.salaheldin.ghost

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "response_events")
data class ResponseEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val messageReceivedAt: Long,
    val repliedAt: Long,
    val responseTimeMs: Long
)