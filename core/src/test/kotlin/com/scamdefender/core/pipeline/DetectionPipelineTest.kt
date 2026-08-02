package com.scamdefender.core.pipeline

import com.scamdefender.core.domain.RiskLevel
import com.scamdefender.core.stt.MockSttEngine
import com.scamdefender.core.stt.TranscriptParser
import kotlin.test.Test
import kotlin.test.assertTrue
import java.io.File

class DetectionPipelineTest {
  @Test
  fun `bank scam reaches suspicious or higher risk`() {
    val segments = loadTranscript("bank_scam_01.txt")
    val report = DetectionPipeline(MockSttEngine(segments)).processSegments(segments)
    assertTrue(report.finalRiskLevel.ordinal >= RiskLevel.SUSPICIOUS.ordinal)
    assertTrue(report.events.isNotEmpty())
  }

  @Test
  fun `legit friend stays safe or monitoring`() {
    val segments = loadTranscript("legit_friend_01.txt")
    val report = DetectionPipeline(MockSttEngine(segments)).processSegments(segments)
    assertTrue(report.finalRiskLevel.ordinal <= RiskLevel.MONITORING.ordinal)
  }

  @Test
  fun `police scam escalates risk over time`() {
    val segments = loadTranscript("police_scam_01.txt")
    val report = DetectionPipeline(MockSttEngine(segments)).processSegments(segments)
    assertTrue(report.finalRiskScore > 0.2f)
  }

  @Test
  fun `bank scam paraphrase reaches suspicious`() {
    val segments = loadTranscript("bank_scam_paraphrase_01.txt")
    val report = DetectionPipeline(MockSttEngine(segments)).processSegments(segments)
    assertTrue(
      report.finalRiskLevel.ordinal >= RiskLevel.SUSPICIOUS.ordinal,
      "risk=${report.finalRiskLevel} score=${report.finalRiskScore}",
    )
  }

  @Test
  fun `hard negative bank fraud alert stays at or below monitoring`() {
    val segments = loadTranscript("legit_bank_fraud_alert_01.txt")
    val report = DetectionPipeline(MockSttEngine(segments)).processSegments(segments)
    assertTrue(
      report.finalRiskLevel.ordinal <= RiskLevel.MONITORING.ordinal,
      "risk=${report.finalRiskLevel} score=${report.finalRiskScore}",
    )
  }

  private fun loadTranscript(name: String): List<com.scamdefender.core.domain.SpeechSegment> {
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
