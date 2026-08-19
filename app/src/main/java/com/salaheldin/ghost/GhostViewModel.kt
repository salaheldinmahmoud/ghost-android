package com.salaheldin.ghost

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GhostViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)

    private val _filterPlatform = MutableStateFlow("All")
    val filterPlatform: StateFlow<String> = _filterPlatform

    val conversations: StateFlow<List<ConversationEntity>> = db.conversationDao()
        .getAllConversations()
        .combine(_filterPlatform) { list, platform ->
            if (platform == "All") list
            else list.filter { it.platform.equals(platform, ignoreCase = true) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    data class RowInfo(
        val baseline: BaselineCalculator.Baseline,
        val isUnusual: Boolean?,
        val risk: RiskAssessment
    )

    /**
     * Pre-calculates all row information in ONE place to avoid N+1 database
     * queries while scrolling the list.
     */
    val rowInfo: StateFlow<Map<Long, RowInfo>> = combine(
        db.conversationDao().getAllConversations(),
        db.responseEventDao().getAllEvents(),
        db.messageDao().getLatestMessagePerConversation(),
    ) { convs, events, latest ->
        val eventsByConv = events.groupBy { it.conversationId }
        val latestByConv = latest.associateBy { it.conversationId }
        val now = System.currentTimeMillis()

        convs.associate { conv ->
            val baseline = BaselineCalculator.calculate(eventsByConv[conv.id].orEmpty())
            val waitMs = if (conv.status == "WAITING_FOR_REPLY") {
                now - (conv.awaitingSince.takeIf { it > 0 } ?: conv.lastMessageTime)
            } else {
                0L
            }
            val requirement = latestByConv[conv.id]?.requiresReply
                ?.let { runCatching { ReplyRequirement.valueOf(it) }.getOrNull() }
                ?: ReplyRequirement.POSSIBLY_REQUIRES_REPLY

            conv.id to RowInfo(
                baseline = baseline,
                isUnusual = BaselineCalculator.checkUnusualDelay(baseline, waitMs),
                risk = RiskScoreCalculator.assess(conv, baseline, waitMs, requirement),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap(),
    )

    fun setFilterPlatform(platform: String) {
        _filterPlatform.value = platform
    }

    private companion object {
        const val MAX_PLAUSIBLE_RESPONSE_MS = 24L * 60 * 60 * 1000 // 24h
    }

    fun markAsReplied(conversation: ConversationEntity) {
        viewModelScope.launch {
            val repliedAt = System.currentTimeMillis()
            val receivedAt = conversation.awaitingSince.takeIf { it > 0L } ?: conversation.lastMessageTime
            val elapsed = repliedAt - receivedAt

            // Only record a baseline sample when the measurement is meaningful.
            if (elapsed in 0..MAX_PLAUSIBLE_RESPONSE_MS) {
                db.responseEventDao().insert(
                    ResponseEventEntity(
                        conversationId = conversation.id,
                        messageReceivedAt = receivedAt,
                        repliedAt = repliedAt,
                        responseTimeMs = elapsed,
                    ),
                )
            }

            db.conversationDao().update(
                conversation.copy(
                    status = "REPLIED",
                    awaitingSince = 0L,
                    updatedAt = repliedAt,
                )
            )
        }
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

    suspend fun getStatistics(): Statistics = withContext(Dispatchers.IO) {
        val totalMessages = db.messageDao().getTotalMessageCount()
        val repliesNeeded = db.conversationDao().getReplyNeededConversationCount()
        val repliesCompleted = db.conversationDao().getRepliedCount()
        val avgResponseTime = db.responseEventDao().getAverageResponseTimeMs() ?: 0L

        val responseRate = if (repliesNeeded > 0) {
            ((repliesCompleted.toFloat() / repliesNeeded.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

        Statistics(
            totalMessages = totalMessages,
            repliesNeeded = repliesNeeded,
            repliesCompleted = repliesCompleted,
            responseRatePercent = responseRate,
            averageResponseTimeMs = avgResponseTime
        )
    }
}