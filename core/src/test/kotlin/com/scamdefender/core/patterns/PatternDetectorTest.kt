package com.scamdefender.core.patterns

import com.scamdefender.core.domain.DetectedPatternType
import com.scamdefender.core.domain.SpeechSegment
import com.scamdefender.core.stt.TranscriptParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class PatternDetectorTest {
    private val detector = PatternDetector()

    @Test
    fun `bank scam detects authority urgency isolation sequence`() {
        val transcript = loadTranscript("bank_scam_01.txt")
        val results = transcript.map { detector.detect(it) }

        val allPatterns = results.flatMap { it.patterns }.map { it.type }.toSet()
        assertTrue(DetectedPatternType.AUTHORITY_PLAY in allPatterns)
        assertTrue(DetectedPatternType.URGENCY_ESCALATION in allPatterns || DetectedPatternType.PROBLEM_INJECTION in allPatterns)
        assertTrue(DetectedPatternType.ISOLATION_SIGNAL in allPatterns || DetectedPatternType.ACTION_REQUEST in allPatterns)

        val typesOverTime = results.map { it.patterns.map { p -> p.type } }
        val authorityIndex = typesOverTime.indexOfFirst { DetectedPatternType.AUTHORITY_PLAY in it }
        val urgencyIndex = typesOverTime.indexOfFirst { DetectedPatternType.URGENCY_ESCALATION in it }
        assertTrue(authorityIndex >= 0)
        if (urgencyIndex >= 0) assertTrue(authorityIndex <= urgencyIndex)
    }

    @Test
    fun `legit friend call has no action request`() {
        val transcript = loadTranscript("legit_friend_01.txt")
        val patterns = transcript.flatMap { detector.detect(it).patterns }
        assertTrue(patterns.none { it.type == DetectedPatternType.ACTION_REQUEST })
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
