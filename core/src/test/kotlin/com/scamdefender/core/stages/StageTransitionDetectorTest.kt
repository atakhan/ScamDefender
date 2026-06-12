package com.scamdefender.core.stages

import com.scamdefender.core.domain.AttackStage
import com.scamdefender.core.domain.SpeechSegment
import com.scamdefender.core.features.FeatureExtractor
import com.scamdefender.core.patterns.PatternDetector
import com.scamdefender.core.stt.TranscriptParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class StageTransitionDetectorTest {
  private val stageDetector = StageTransitionDetector()
  private val featureExtractor = FeatureExtractor()
  private val patternDetector = PatternDetector()

  @Test
  fun `bank scam timeline progresses through stages`() {
    val segments = loadTranscript("bank_scam_01.txt")
    val updates =
        segments.map { segment ->
          val features = featureExtractor.extract(segment)
          val patterns = patternDetector.detect(segment)
          stageDetector.update(segment, features, patterns.patterns)
        }

    assertEquals(AttackStage.STAGE_1, updates[0].dominantStage)
    assertTrue(updates[2].dominantStage.ordinal >= AttackStage.STAGE_2.ordinal)
    assertTrue(updates.any { it.transitionDetected })
    assertTrue(updates.last().dominantStage.ordinal >= AttackStage.STAGE_3.ordinal)
  }

  private fun loadTranscript(name: String): List<SpeechSegment> {
    val root = findProjectRoot()
    return TranscriptParser.parse(File(root, "samples/transcripts/$name"))
  }

  private fun findProjectRoot(): File {
    var dir = File(System.getProperty("user.dir"))
    repeat(5) {
      if (File(dir, "settings.gradle.kts").exists()) return dir
      dir = dir.parentFile ?: return dir
    }
    return File(System.getProperty("user.dir"))
  }
}
