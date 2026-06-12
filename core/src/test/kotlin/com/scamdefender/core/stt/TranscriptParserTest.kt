package com.scamdefender.core.stt

import kotlin.test.Test
import kotlin.test.assertEquals

class TranscriptParserTest {
  @Test
  fun `parses t+N format`() {
    val segments =
        TranscriptParser.parseText(
            """
            t+0s: "Здравствуйте"
            t+10s: "срочно"
            """.trimIndent(),
        )
    assertEquals(2, segments.size)
    assertEquals(0, segments[0].timestampMs)
    assertEquals(10_000, segments[1].timestampMs)
  }
}
