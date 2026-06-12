package com.scamdefender.core.patterns

import com.scamdefender.core.domain.DetectedPatternType

enum class FsmState {
    IDLE,
    AUTHORITY_SEEN,
    PROBLEM_SEEN,
    URGENCY_ESCALATION,
    ISOLATION_SEEN,
    ACTION_REQUESTED,
}

class PatternFsm(
  private val transitionOrder: List<DetectedPatternType>,
) {
    private var state: FsmState = FsmState.IDLE
    private val seenPatterns = mutableSetOf<DetectedPatternType>()

    val currentState: FsmState get() = state

    fun reset() {
        state = FsmState.IDLE
        seenPatterns.clear()
    }

    fun update(patterns: List<DetectedPatternType>): Boolean {
        patterns.forEach { pattern ->
            if (seenPatterns.add(pattern)) {
                advance(pattern)
            }
        }
        return scenarioActivated()
    }

    private fun scenarioActivated(): Boolean {
        val hasAuthority = DetectedPatternType.AUTHORITY_PLAY in seenPatterns
        val hasUrgency = DetectedPatternType.URGENCY_ESCALATION in seenPatterns
        val hasAction =
            DetectedPatternType.ACTION_REQUEST in seenPatterns ||
                DetectedPatternType.ISOLATION_SIGNAL in seenPatterns
        return (hasAuthority && hasUrgency && hasAction) ||
            state == FsmState.ACTION_REQUESTED ||
            (state == FsmState.URGENCY_ESCALATION && seenPatterns.size >= 2)
    }

    private fun advance(pattern: DetectedPatternType) {
        state =
            when (pattern) {
                DetectedPatternType.AUTHORITY_PLAY ->
                    if (state == FsmState.IDLE) FsmState.AUTHORITY_SEEN else state
                DetectedPatternType.PROBLEM_INJECTION ->
                    if (state == FsmState.AUTHORITY_SEEN || state == FsmState.IDLE) {
                        FsmState.PROBLEM_SEEN
                    } else {
                        state
                    }
                DetectedPatternType.URGENCY_ESCALATION ->
                    when (state) {
                        FsmState.PROBLEM_SEEN, FsmState.AUTHORITY_SEEN -> FsmState.URGENCY_ESCALATION
                        else -> state
                    }
                DetectedPatternType.ISOLATION_SIGNAL ->
                    when (state) {
                        FsmState.URGENCY_ESCALATION, FsmState.PROBLEM_SEEN -> FsmState.ISOLATION_SEEN
                        else -> state
                    }
                DetectedPatternType.ACTION_REQUEST, DetectedPatternType.FEAR_THREAT ->
                    when (state) {
                        FsmState.ISOLATION_SEEN,
                        FsmState.URGENCY_ESCALATION,
                        FsmState.PROBLEM_SEEN,
                        -> FsmState.ACTION_REQUESTED
                        else -> state
                    }
            }
    }

    fun sequenceProgression(): Float {
        if (transitionOrder.isEmpty()) return 0f
        val matched = transitionOrder.count { seenPatterns.contains(it) }
        return matched.toFloat() / transitionOrder.size
    }
}
