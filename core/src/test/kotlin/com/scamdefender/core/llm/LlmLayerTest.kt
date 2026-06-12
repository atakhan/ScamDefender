package com.scamdefender.core.llm

import com.scamdefender.core.domain.DetectedPatternType
import com.scamdefender.core.domain.ScamScenario
import com.scamdefender.core.domain.SpeechSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LlmLayerTest {
  private val model = HeuristicScenarioModel()
  private val parser = ResponseParser()

  @Test
  fun `prompt builder includes patterns and transcript`() {
    val prompt =
        PromptBuilder().build(
            listOf(SpeechSegment("это банк", 0)),
            listOf(DetectedPatternType.AUTHORITY_PLAY),
        )
    assert(prompt.contains("AUTHORITY_PLAY"))
    assert(prompt.contains("банк"))
  }

  @Test
  fun `response parser decodes json`() {
    val result =
        parser.parse(
            """{"scenario":"bank_fraud_impersonation","stage":"STAGE_3","confidence":0.82}""",
        )
    assertNotNull(result)
    assertEquals(ScamScenario.BANK_FRAUD_IMPERSONATION, result.scenario)
    assertEquals(0.82f, result.confidence)
  }

  @Test
  fun `heuristic model classifies bank scam`() {
    val transcript =
        listOf(
            SpeechSegment("служба безопасности банка", 0),
            SpeechSegment("срочно подтвердите", 10_000),
        )
    val result =
        model.classify(
            transcript,
            listOf(
                DetectedPatternType.AUTHORITY_PLAY,
                DetectedPatternType.URGENCY_ESCALATION,
            ),
        )
    assertNotNull(result)
    assertEquals(ScamScenario.BANK_FRAUD_IMPERSONATION, result.scenario)
  }
}
