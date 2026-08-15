package com.salaheldin.ghost

data class RiskAssessment(
    val score: Int,
    val reasons: List<String>
)

object RiskScoreCalculator {

    fun assess(
        conversation: ConversationEntity,
        baseline: BaselineCalculator.Baseline,
        currentWaitMs: Long,
        latestRequirement: ReplyRequirement
    ): RiskAssessment {
        if (conversation.status != "WAITING_FOR_REPLY") {
            return RiskAssessment(score = 0, reasons = emptyList())
        }

        var score = 0
        val reasons = mutableListOf<String>()

        when (conversation.priority) {
            "HIGH" -> { score += 40; reasons.add("Message appears high priority") }
            "MEDIUM" -> { score += 20; reasons.add("Message may need a response") }
        }

        when (latestRequirement) {
            ReplyRequirement.REPLY_REQUIRED -> { score += 20; reasons.add("Looks like a question or request") }
            ReplyRequirement.POSSIBLY_REQUIRES_REPLY -> score += 10
            ReplyRequirement.NO_REPLY_REQUIRED -> {}
        }

        if (baseline.hasEnoughData && baseline.averageResponseTimeMs > 0) {
            val ratio = currentWaitMs.toDouble() / baseline.averageResponseTimeMs
            when {
                ratio >= 2.5 -> { score += 40; reasons.add("Waiting much longer than usual for this contact") }
                ratio >= 1.5 -> { score += 20; reasons.add("Waiting longer than usual for this contact") }
            }
        } else {
            val waitMinutes = currentWaitMs / 60000
            if (waitMinutes >= 60) { score += 20; reasons.add("Waiting over an hour") }
        }

        val finalScore = score.coerceIn(0, 100)
        if (reasons.isEmpty() && finalScore > 0) reasons.add("Flagged based on message content")

        return RiskAssessment(score = finalScore, reasons = reasons)
    }
}