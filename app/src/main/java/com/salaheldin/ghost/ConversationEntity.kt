package com.salaheldin.ghost

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["contactIdentifier"], unique = true),
        Index(value = ["lastMessageTime"]),
        Index(value = ["status"]),
    ]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactIdentifier: String,   // technical grouping key, e.g. "Instagram:Salaheldin Mahmoud"
    val displayName: String = "",    // clean name shown in the UI
    /**
     * Platform-native handle (phone number, @username, shortcut id) when the
     * notification exposes one. Used for deep links; displayName is NOT a handle.
     */
    val handle: String = "",
    val platform: String = "whatsapp",
    val isGroup: Boolean = false,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0,
    /** Timestamp of the OLDEST unanswered message. 0 = nothing pending. */
    val awaitingSince: Long = 0,
    val status: String = "NEW",
    val priority: String = "MEDIUM",
    /** Timestamp of the last reply you sent; 0 if you never replied. */
    val lastRepliedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)