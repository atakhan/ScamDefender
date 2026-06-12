package com.scamdefender.core.stt

import com.scamdefender.core.audio.WavReader
import com.scamdefender.core.ml.ModelPaths
import com.scamdefender.core.ml.SherpaJarPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SherpaSttEngineTest {
    @Test
    fun `gigaam model is detected and available`() {
        val paths = ModelPaths.resolve()
        if (!paths.sherpaEncoder.isFile) return
        assertTrue(paths.sherpaAvailable, "Expected GigaAM files in ${paths.sherpaDir}")
        if (SherpaJarPaths.isOnClasspath()) {
            assertTrue(SherpaSttEngine.isAvailable(paths))
        }
    }

    @Test
    fun `gigaam transcribes bundled example wav`() {
        val paths = ModelPaths.resolve()
        val example = paths.sherpaExampleWav
        if (!SherpaSttEngine.isAvailable(paths) || example == null || !example.isFile) return

        val wav = WavReader.read(example)
        val chunk =
            com.scamdefender.core.domain.AudioChunk(
                pcmSamples = wav.samples,
                sampleRateHz = wav.sampleRateHz,
                timestampMs = 0,
            )

        SherpaSttEngine(paths).use { engine ->
            val segments = engine.transcribe(listOf(chunk))
            assertTrue(segments.isNotEmpty(), "Expected non-empty transcription")
            assertTrue(segments.first().text.isNotBlank())
        }
    }
}
