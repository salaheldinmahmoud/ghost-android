package com.salaheldin.ghost

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["sender", "content", "timestamp"], unique = true),
        Index(value = ["conversationId", "timestamp"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long = 0,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val platform: String = "whatsapp",
    val requiresReply: String = "POSSIBLY_REQUIRES_REPLY",
    val priority: String = "MEDIUM",
    val insertedAt: Long = System.currentTimeMillis()
)