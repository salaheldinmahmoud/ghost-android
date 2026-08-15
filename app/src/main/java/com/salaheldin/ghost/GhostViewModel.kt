package com.salaheldin.ghost

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GhostViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)

    val conversations: StateFlow<List<ConversationEntity>> = db.conversationDao()
        .getAllConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
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
    suspend fun getRiskAssessment(conversation: ConversationEntity): RiskAssessment {
        val events = db.responseEventDao().getEventsForConversation(conversation.id)
        val baseline = BaselineCalculator.calculate(events)

        val currentWaitMs = if (conversation.status == "WAITING_FOR_REPLY") {
            System.currentTimeMillis() - conversation.lastMessageTime
        } else {
            0
        }

        val latestMessage = db.messageDao().getLatestMessage(conversation.id)
        val requirement = latestMessage?.requiresReply
            ?.let { ReplyRequirement.valueOf(it) }
            ?: ReplyRequirement.POSSIBLY_REQUIRES_REPLY

        return RiskScoreCalculator.assess(conversation, baseline, currentWaitMs, requirement)
    }
    suspend fun getDelayInfo(conversation: ConversationEntity): Pair<BaselineCalculator.Baseline, Boolean?> {
        val events = db.responseEventDao().getEventsForConversation(conversation.id)
        val baseline = BaselineCalculator.calculate(events)

        val currentWaitMs = if (conversation.status == "WAITING_FOR_REPLY") {
            System.currentTimeMillis() - conversation.lastMessageTime
        } else {
            0
        }

        val isUnusual = BaselineCalculator.checkUnusualDelay(baseline, currentWaitMs)
        return baseline to isUnusual
    }
}