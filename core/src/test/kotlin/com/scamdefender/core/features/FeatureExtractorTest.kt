package com.scamdefender.core.features

import com.scamdefender.core.domain.SpeechSegment
import kotlin.test.Test
import kotlin.test.assertTrue

class FeatureExtractorTest {
    private val extractor = FeatureExtractor()

    @Test
    fun paraphraseBankAuthorityAndActionFire() {
        val authority =
            extractor.extract(
                SpeechSegment("я из отдела борьбы с мошенничеством вашего банка", 0),
            )
        assertTrue(authority.authority >= 0.4f, "authority=${authority.authority}")

        val action =
            extractor.extract(
                SpeechSegment("назовите цифры из сообщения, которое сейчас придёт", 0),
            )
        assertTrue(action.actionRequest >= 0.45f, "action=${action.actionRequest}")
    }

    @Test
    fun actionNegationDampensCodeRequest() {
        val negated =
            extractor.extract(
                SpeechSegment("код из смс мы никогда не запрашиваем по телефону", 0),
            )
        assertTrue(negated.actionRequest < 0.2f, "action=${negated.actionRequest}")
    }

    @Test
    fun isolationParaphraseDetected() {
        val isolation =
            extractor.extract(
                SpeechSegment("держите разговор в тайне от родственников", 0),
            )
        assertTrue(isolation.isolation >= 0.4f, "isolation=${isolation.isolation}")
    }
}
