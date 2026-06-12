package com.scamdefender.core.audio

import com.scamdefender.core.domain.AudioChunk
import com.scamdefender.core.ml.ModelPaths
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.math.sin

class SileroVadEngineTest {
    @Test
    fun `silero inference runs when model available`() {
        val paths = ModelPaths.resolve()
        if (!paths.sileroAvailable) return

        val vad = SileroVadEngine(paths)
        val speech = generateTonePcm(frequencyHz = 300, durationMs = 600, amplitude = 20_000)
        val result = vad.isSpeech(AudioChunk(speech, 16_000, 0))
        assertNotNull(result)
    }

    private fun generateTonePcm(
        frequencyHz: Int,
        durationMs: Int,
        amplitude: Int,
        sampleRate: Int = 16_000,
    ): ShortArray {
        val samples = sampleRate * durationMs / 1000
        return ShortArray(samples) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2 * kotlin.math.PI * frequencyHz * t) * amplitude).toInt().toShort()
        }
    }
}
