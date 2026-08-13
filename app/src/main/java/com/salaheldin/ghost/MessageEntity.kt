package com.salaheldin.ghost

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index(value = ["sender", "content", "timestamp"], unique = true)]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val platform: String = "whatsapp",
    val insertedAt: Long = System.currentTimeMillis()
)