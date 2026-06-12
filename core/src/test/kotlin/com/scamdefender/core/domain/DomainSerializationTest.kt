package com.scamdefender.core.domain

import com.scamdefender.core.scenarios.ScenarioOntology
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DomainSerializationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `AttackStage serializes with STAGE prefix`() {
        val encoded = json.encodeToString(AttackStage.STAGE_3)
        assertEquals("\"STAGE_3\"", encoded)
    }

    @Test
    fun `ScamScenario round-trips through JSON`() {
        val scenario = ScamScenario.BANK_FRAUD_IMPERSONATION
        val decoded = json.decodeFromString<ScamScenario>(json.encodeToString(scenario))
        assertEquals(scenario, decoded)
    }

    @Test
    fun `RiskLevel ordering is consistent`() {
        val levels = RiskLevel.entries
        assertEquals(RiskLevel.SAFE, levels.first())
        assertEquals(RiskLevel.CRITICAL, levels.last())
        assertEquals(5, levels.size)
    }

    @Test
    fun `FeatureVector aggregate is bounded`() {
        val vector = FeatureVector(1f, 1f, 1f, 1f, 1f)
        assertEquals(1f, vector.aggregateScore())
    }

    @Test
    fun `StageDistribution dominant stage picks highest probability`() {
        val dist = StageDistribution.focused(AttackStage.STAGE_4, 0.9f)
        assertEquals(AttackStage.STAGE_4, dist.dominantStage)
        assertTrue(dist.dominantProbability >= 0.9f)
    }

    @Test
    fun `SpeechSegment serializes with metadata`() {
        val segment = SpeechSegment(
            text = "тест",
            timestampMs = 1000,
            metadata = SegmentMeta(durationMs = 2500),
        )
        val decoded = json.decodeFromString<SpeechSegment>(json.encodeToString(segment))
        assertEquals(segment, decoded)
    }

    @Test
    fun `ontology loads from resources`() {
        val ontology = ScenarioOntology.load()
        assertEquals(7, ontology.ontology.stages.size)
        assertEquals(7, ontology.ontology.scenarios.size)
        assertTrue(ontology.patternsBank.phrases.isNotEmpty())
        assertNotNull(ontology.scenarioForId("bank_fraud_impersonation"))
        assertNotNull(ontology.stageForId("STAGE_3"))
    }
}
