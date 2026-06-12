package com.scamdefender.core.ml

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class ModelPathsTest {
    @Test
    fun `availability flags reflect filesystem`() {
        val root = File(System.getProperty("java.io.tmpdir"), "scamdefender-models-test")
        root.mkdirs()
        val sileroDir = File(root, "silero").apply { mkdirs() }
        File(sileroDir, "silero_vad.onnx").writeText("stub")

        val paths = ModelPaths(root)
        assertTrue(paths.sileroAvailable)
        assertFalse(paths.minilmAvailable)
        assertFalse(paths.sherpaAvailable)
    }

    @Test
    fun `finds gigaam files in nested directory`() {
        val paths = ModelPaths.resolve()
        if (!File(paths.sherpaDir, "encoder.int8.onnx").exists() &&
            paths.sherpaEncoder.name != "encoder.int8.onnx"
        ) {
            return
        }
        if (paths.sherpaEncoder.isFile) {
            assertTrue(paths.sherpaEncoder.name.startsWith("encoder"))
            assertTrue(paths.sherpaAvailable || paths.sherpaTokens.isFile)
        }
    }
}
