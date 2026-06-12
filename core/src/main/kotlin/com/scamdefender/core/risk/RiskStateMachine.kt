package com.scamdefender.core.risk

import com.scamdefender.core.domain.RiskLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RiskThresholds(
    val monitoring: Float = 0.20f,
    val suspicious: Float = 0.40f,
    val highRisk: Float = 0.60f,
    val critical: Float = 0.80f,
)

class RiskStateMachine(
    private val thresholds: RiskThresholds = RiskThresholds(),
    private val momentumRequired: Int = 2,
) {
    private val _riskLevel = MutableStateFlow(RiskLevel.SAFE)
    val riskLevel: StateFlow<RiskLevel> = _riskLevel.asStateFlow()

    private var highSignalStreak = 0
    private var currentLevel = RiskLevel.SAFE

    fun reset() {
        highSignalStreak = 0
        currentLevel = RiskLevel.SAFE
        _riskLevel.value = RiskLevel.SAFE
    }

    fun update(riskScore: Float): RiskLevel {
        val target = levelForScore(riskScore)
        val isHighSignal = riskScore >= thresholds.suspicious

        if (isHighSignal) {
            highSignalStreak++
        } else {
            highSignalStreak = 0
        }

        currentLevel =
            when {
                target.ordinal > currentLevel.ordinal -> {
                    if (target.ordinal >= RiskLevel.HIGH_RISK.ordinal && highSignalStreak < momentumRequired) {
                        RiskLevel.entries[currentLevel.ordinal.coerceAtLeast(RiskLevel.SUSPICIOUS.ordinal)]
                    } else {
                        target
                    }
                }
                target.ordinal < currentLevel.ordinal -> {
                    RiskLevel.entries[(currentLevel.ordinal - 1).coerceAtLeast(target.ordinal)]
                }
                else -> currentLevel
            }

        _riskLevel.value = currentLevel
        return currentLevel
    }

    private fun levelForScore(score: Float): RiskLevel =
        when {
            score >= thresholds.critical -> RiskLevel.CRITICAL
            score >= thresholds.highRisk -> RiskLevel.HIGH_RISK
            score >= thresholds.suspicious -> RiskLevel.SUSPICIOUS
            score >= thresholds.monitoring -> RiskLevel.MONITORING
            else -> RiskLevel.SAFE
        }
}
