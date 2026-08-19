package com.salaheldin.ghost

object BaselineCalculator {

    private const val MIN_EVENTS_FOR_BASELINE = 3

    data class Baseline(
        val typicalResponseTimeMs: Long,
        val sampleSize: Int,
        val hasEnoughData: Boolean
    )

    fun calculate(events: List<ResponseEventEntity>): Baseline {
        if (events.isEmpty()) {
            return Baseline(typicalResponseTimeMs = 0, sampleSize = 0, hasEnoughData = false)
        }

        val sorted = events.map { it.responseTimeMs }.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        }

        return Baseline(
            typicalResponseTimeMs = median,
            sampleSize = events.size,
            hasEnoughData = events.size >= MIN_EVENTS_FOR_BASELINE
        )
    }

    /**
     * Compares how long a conversation has currently been waiting against
     * the contact's historical baseline. Returns null if there's no
     * reliable baseline yet, or if the conversation isn't waiting.
     */
    fun checkUnusualDelay(
        baseline: Baseline,
        currentWaitMs: Long,
        thresholdMultiplier: Double = 2.5
    ): Boolean? {
        if (!baseline.hasEnoughData) return null
        if (baseline.typicalResponseTimeMs <= 0) return null

        return currentWaitMs > baseline.typicalResponseTimeMs * thresholdMultiplier
    }
}