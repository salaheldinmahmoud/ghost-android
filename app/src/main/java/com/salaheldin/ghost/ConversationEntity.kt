package com.salaheldin.ghost

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["contactIdentifier"], unique = true)]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactIdentifier: String,
    val platform: String = "whatsapp",
    val isGroup: Boolean = false,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0,
    val status: String = "NEW",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)