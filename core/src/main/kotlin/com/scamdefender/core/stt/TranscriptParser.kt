package com.scamdefender.core.stt

import com.scamdefender.core.domain.SegmentMeta
import com.scamdefender.core.domain.SpeechSegment
import java.io.File

object TranscriptParser {
    private val lineRegex = Regex("""^t\+(\d+)s:\s*"(.*)"\s*$""")

    fun parse(file: File): List<SpeechSegment> =
        file.readLines()
            .mapNotNull { line -> parseLine(line.trim()) }

    fun parseText(content: String): List<SpeechSegment> =
        content.lines().mapNotNull { line -> parseLine(line.trim()) }

    private fun parseLine(line: String): SpeechSegment? {
        if (line.isBlank() || line.startsWith("#")) return null
        val match = lineRegex.matchEntire(line)
        if (match != null) {
            val seconds = match.groupValues[1].toLong()
            val text = match.groupValues[2]
            return SpeechSegment(
                text = text,
                timestampMs = seconds * 1000,
                metadata = SegmentMeta(durationMs = 2500),
            )
        }
        val parts = line.split("|", limit = 2)
        if (parts.size == 2) {
            return SpeechSegment(
                text = parts[1].trim(),
                timestampMs = parts[0].trim().toLongOrNull()?.times(1000) ?: 0,
            )
        }
        return null
    }
}
