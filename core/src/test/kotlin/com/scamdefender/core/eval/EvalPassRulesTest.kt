package com.scamdefender.core.eval

import com.scamdefender.core.domain.RiskLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvalPassRulesTest {
    @Test
    fun scamPassesWhenWithinMinAndMax() {
        assertTrue(pass(isScam = true, final = RiskLevel.SUSPICIOUS, min = RiskLevel.SUSPICIOUS, max = null))
        assertTrue(pass(isScam = true, final = RiskLevel.HIGH_RISK, min = RiskLevel.MONITORING, max = RiskLevel.HIGH_RISK))
        assertFalse(pass(isScam = true, final = RiskLevel.CRITICAL, min = RiskLevel.MONITORING, max = RiskLevel.HIGH_RISK))
        assertFalse(pass(isScam = true, final = RiskLevel.SAFE, min = RiskLevel.SUSPICIOUS, max = null))
    }

    @Test
    fun nonScamFailsAboveMax() {
        assertTrue(pass(isScam = false, final = RiskLevel.SAFE, min = RiskLevel.SUSPICIOUS, max = RiskLevel.MONITORING))
        assertTrue(pass(isScam = false, final = RiskLevel.MONITORING, min = RiskLevel.SUSPICIOUS, max = RiskLevel.MONITORING))
        assertFalse(pass(isScam = false, final = RiskLevel.SUSPICIOUS, min = RiskLevel.SUSPICIOUS, max = RiskLevel.MONITORING))
    }

    private fun pass(
        isScam: Boolean,
        final: RiskLevel,
        min: RiskLevel,
        max: RiskLevel?,
    ): Boolean {
        val effectiveMax = max ?: if (!isScam) RiskLevel.MONITORING else null
        return if (isScam) {
            final.ordinal >= min.ordinal && (effectiveMax == null || final.ordinal <= effectiveMax.ordinal)
        } else {
            effectiveMax != null && final.ordinal <= effectiveMax.ordinal
        }
    }
}
