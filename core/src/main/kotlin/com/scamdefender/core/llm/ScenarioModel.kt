package com.scamdefender.core.llm

import com.scamdefender.core.domain.AttackStage
import com.scamdefender.core.domain.DetectedPatternType
import com.scamdefender.core.domain.ScamScenario
import com.scamdefender.core.domain.ScenarioClassification
import com.scamdefender.core.domain.SpeechSegment

interface ScenarioModel {
    fun classify(
        transcript: List<SpeechSegment>,
        patterns: List<DetectedPatternType>,
    ): ScenarioClassification?
}

class PromptBuilder {
    fun build(transcript: List<SpeechSegment>, patterns: List<DetectedPatternType>): String {
        val context =
            transcript.joinToString("\n") { segment ->
                "t+${segment.timestampMs / 1000}s: \"${segment.text}\""
            }
        val patternList = patterns.joinToString(", ") { it.name }
        return """
            |Контекст последних ${transcript.size} сегментов разговора:
            |$context
            |
            |Обнаруженные паттерны: [$patternList]
            |
            |Задача: классифицируй сценарий и стадию атаки. Ответ строго JSON:
            |{"scenario":"...", "stage":"STAGE_N", "confidence":0.0-1.0}
            |
            |Сценарии: bank_fraud_impersonation | police_authority_scam | child_in_trouble |
            |investment_fraud | tech_support_scam | delivery_marketplace_scam | financial_transfer_scam
        """.trimMargin()
    }
}

class ResponseParser {
    private val jsonRegex =
        Regex("""\{\s*"scenario"\s*:\s*"([^"]+)"\s*,\s*"stage"\s*:\s*"([^"]+)"\s*,\s*"confidence"\s*:\s*([\d.]+)\s*}""")

    fun parse(response: String): ScenarioClassification? {
        val match = jsonRegex.find(response) ?: return null
        val scenarioId = match.groupValues[1]
        val stageId = match.groupValues[2]
        val confidence = match.groupValues[3].toFloatOrNull() ?: return null

        val scenario =
            when (scenarioId) {
                "bank_fraud_impersonation" -> ScamScenario.BANK_FRAUD_IMPERSONATION
                "police_authority_scam" -> ScamScenario.POLICE_AUTHORITY_SCAM
                "child_in_trouble" -> ScamScenario.CHILD_IN_TROUBLE
                "investment_fraud" -> ScamScenario.INVESTMENT_FRAUD
                "tech_support_scam" -> ScamScenario.TECH_SUPPORT_SCAM
                "delivery_marketplace_scam" -> ScamScenario.DELIVERY_MARKETPLACE_SCAM
                "financial_transfer_scam" -> ScamScenario.FINANCIAL_TRANSFER_SCAM
                else -> return null
            }
        val stage = AttackStage.entries.find { it.name == stageId } ?: return null
        return ScenarioClassification(scenario, stage, confidence.coerceIn(0f, 1f))
    }
}

/**
 * Rule-based scenario classifier for JVM demo.
 * MediaPipe + Gemma integration replaces this on Android.
 */
class HeuristicScenarioModel : ScenarioModel {
    override fun classify(
        transcript: List<SpeechSegment>,
        patterns: List<DetectedPatternType>,
    ): ScenarioClassification? {
        if (patterns.isEmpty()) return null
        val text = transcript.joinToString(" ") { it.text }.lowercase()
        val stage = inferStage(patterns)
        val scenario = inferScenario(text, patterns) ?: return null
        val confidence = (0.6f + patterns.size * 0.05f).coerceAtMost(0.92f)
        return ScenarioClassification(scenario, stage, confidence)
    }

    private fun inferStage(patterns: List<DetectedPatternType>): AttackStage =
        when {
            DetectedPatternType.ACTION_REQUEST in patterns -> AttackStage.STAGE_5
            DetectedPatternType.ISOLATION_SIGNAL in patterns -> AttackStage.STAGE_4
            DetectedPatternType.URGENCY_ESCALATION in patterns -> AttackStage.STAGE_3
            DetectedPatternType.PROBLEM_INJECTION in patterns -> AttackStage.STAGE_2
            DetectedPatternType.AUTHORITY_PLAY in patterns -> AttackStage.STAGE_1
            else -> AttackStage.STAGE_0
        }

    private fun inferScenario(text: String, patterns: List<DetectedPatternType>): ScamScenario? =
        when {
            "банк" in text || "служба безопасности" in text -> ScamScenario.BANK_FRAUD_IMPERSONATION
            "полици" in text || "следователь" in text || "уголовн" in text -> ScamScenario.POLICE_AUTHORITY_SCAM
            "сын" in text || "дочь" in text || "родственник" in text -> ScamScenario.CHILD_IN_TROUBLE
            "доставк" in text || "посылк" in text || "маркетплейс" in text -> ScamScenario.DELIVERY_MARKETPLACE_SCAM
            "поддержк" in text || "приложени" in text -> ScamScenario.TECH_SUPPORT_SCAM
            "инвест" in text || "доход" in text -> ScamScenario.INVESTMENT_FRAUD
            DetectedPatternType.ACTION_REQUEST in patterns -> ScamScenario.FINANCIAL_TRANSFER_SCAM
            else -> null
        }
}

class TriggeredScenarioModel(
    private val delegate: ScenarioModel = HeuristicScenarioModel(),
    private val debounceMs: Long = 10_000,
) : ScenarioModel {
    private var lastInvocationMs = 0L

    fun shouldInvoke(llmTrigger: Boolean, nowMs: Long): Boolean {
        if (!llmTrigger) return false
        if (nowMs - lastInvocationMs < debounceMs) return false
        lastInvocationMs = nowMs
        return true
    }

    override fun classify(
        transcript: List<SpeechSegment>,
        patterns: List<DetectedPatternType>,
    ): ScenarioClassification? = delegate.classify(transcript, patterns)

    fun reset() {
        lastInvocationMs = 0L
    }
}
