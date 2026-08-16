package com.salaheldin.ghost

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GhostViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)

    val conversations: StateFlow<List<ConversationEntity>> = db.conversationDao()
        .getAllConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    fun markAsReplied(conversation: ConversationEntity) {
        viewModelScope.launch {
            val repliedAt = System.currentTimeMillis()
            val receivedAt = conversation.lastMessageTime

            db.responseEventDao().insert(
                ResponseEventEntity(
                    conversationId = conversation.id,
                    messageReceivedAt = receivedAt,
                    repliedAt = repliedAt,
                    responseTimeMs = repliedAt - receivedAt
                )
            )

            db.conversationDao().update(
                conversation.copy(
                    status = "REPLIED",
                    updatedAt = repliedAt
                )
            )
        }
    }

    data class RowInfo(
        val baseline: BaselineCalculator.Baseline,
        val isUnusual: Boolean?,
        val risk: RiskAssessment
    )

    suspend fun getRowInfo(conversation: ConversationEntity): RowInfo {
        val events = db.responseEventDao().getEventsForConversation(conversation.id)
        val baseline = BaselineCalculator.calculate(events)

        val currentWaitMs = if (conversation.status == "WAITING_FOR_REPLY") {
            System.currentTimeMillis() - conversation.lastMessageTime
        } else {
            0
        }

        val isUnusual = BaselineCalculator.checkUnusualDelay(baseline, currentWaitMs)

        val latestMessage = db.messageDao().getLatestMessage(conversation.id)
        val requirement = latestMessage?.requiresReply
            ?.let { ReplyRequirement.valueOf(it) }
            ?: ReplyRequirement.POSSIBLY_REQUIRES_REPLY

        val risk = RiskScoreCalculator.assess(conversation, baseline, currentWaitMs, requirement)

        return RowInfo(baseline, isUnusual, risk)
    }

    fun getMessagesFlow(conversationId: Long) = db.messageDao().getMessagesForConversation(conversationId)

    fun deleteAllData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }
        }
    }

    data class Statistics(
        val totalMessages: Int,
        val repliesNeeded: Int,
        val repliesCompleted: Int,
        val responseRatePercent: Int,
        val averageResponseTimeMs: Long
    )

    suspend fun getStatistics(): Statistics {
        val totalMessages = db.messageDao().getTotalMessageCount()
        val repliesNeeded = db.messageDao().getRepliesNeededCount()
        val repliesCompleted = db.conversationDao().getRepliedCount()
        val avgResponseTime = db.responseEventDao().getAverageResponseTimeMs() ?: 0L

        val responseRate = if (repliesNeeded > 0) {
            ((repliesCompleted.toFloat() / repliesNeeded.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

        return Statistics(
            totalMessages = totalMessages,
            repliesNeeded = repliesNeeded,
            repliesCompleted = repliesCompleted,
            responseRatePercent = responseRate,
            averageResponseTimeMs = avgResponseTime
        )
    }
}