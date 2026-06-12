package com.scamdefender.core.patterns

import com.scamdefender.core.domain.DetectedPattern
import com.scamdefender.core.domain.DetectedPatternType
import com.scamdefender.core.domain.PatternSource
import com.scamdefender.core.scenarios.PhrasePattern
import org.ahocorasick.trie.Emit
import org.ahocorasick.trie.Trie

class AhoCorasickMatcher(
    phrases: List<PhrasePattern>,
    private val patternResolver: (String) -> DetectedPatternType?,
) {
    private data class Payload(val pattern: DetectedPatternType, val weight: Float)

    private val payloads = mutableMapOf<String, Payload>()
    private val trie: Trie

    init {
        val builder = Trie.builder().ignoreOverlaps().ignoreCase()
        phrases.forEach { entry ->
            val type = patternResolver(entry.pattern) ?: return@forEach
            payloads[entry.phrase.lowercase()] = Payload(type, entry.weight)
            builder.addKeyword(entry.phrase.lowercase())
        }
        trie = builder.build()
    }

    fun match(text: String): List<DetectedPattern> {
        val normalized = text.lowercase()
        return trie.parseText(normalized)
            .mapNotNull { emit -> toPattern(emit) }
            .distinctBy { it.type to it.matchedPhrase }
    }

    private fun toPattern(emit: Emit): DetectedPattern? {
        val payload = payloads[emit.keyword] ?: return null
        return DetectedPattern(
            type = payload.pattern,
            confidence = payload.weight,
            matchedPhrase = emit.keyword,
            source = PatternSource.KEYWORD,
        )
    }
}
