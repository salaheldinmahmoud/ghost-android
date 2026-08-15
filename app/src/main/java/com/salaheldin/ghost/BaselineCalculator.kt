package com.salaheldin.ghost

object BaselineCalculator {

    private const val MIN_EVENTS_FOR_BASELINE = 3

    data class Baseline(
        val averageResponseTimeMs: Long,
        val sampleSize: Int,
        val hasEnoughData: Boolean
    )

    fun calculate(events: List<ResponseEventEntity>): Baseline {
        if (events.isEmpty()) {
            return Baseline(averageResponseTimeMs = 0, sampleSize = 0, hasEnoughData = false)
        }

        val average = events.map { it.responseTimeMs }.average().toLong()

        return Baseline(
            averageResponseTimeMs = average,
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
        if (baseline.averageResponseTimeMs <= 0) return null

        return currentWaitMs > baseline.averageResponseTimeMs * thresholdMultiplier
    }
}