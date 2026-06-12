package com.scamdefender.core

import com.scamdefender.core.domain.SpeechSegment
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoreEngineFactoryTest {
    @Test
    fun `factory creates pipeline for transcript mode`() {
        val segments = listOf(SpeechSegment("это банк", 0))
        val components = CoreEngineFactory.forTranscript(segments)
        val report = components.createPipeline().processSegments(segments)
        assertNotNull(report)
        assertTrue(report.segments.isNotEmpty())
    }

    @Test
    fun `factory works without downloaded models`() {
        val components = CoreEngineFactory.create()
        assertNotNull(components.vadEngine)
        assertNotNull(components.patternDetector)
    }
}
