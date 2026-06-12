package com.scamdefender.core.features

import com.scamdefender.core.domain.FeatureVector
import com.scamdefender.core.domain.SentenceType
import com.scamdefender.core.domain.SpeechSegment

class FeatureExtractor(
    private val urgencyWords: Set<String> = URGENCY_WORDS,
    private val authorityWords: Set<String> = AUTHORITY_WORDS,
    private val actionWords: Set<String> = ACTION_WORDS,
    private val fearWords: Set<String> = FEAR_WORDS,
    private val isolationWords: Set<String> = ISOLATION_WORDS,
) {
    fun extract(segment: SpeechSegment): FeatureVector {
        val normalized = segment.text.lowercase()
        val tokens = tokenize(normalized)

        return FeatureVector(
            urgency = scoreTokens(tokens, normalized, urgencyWords),
            authority = scoreTokens(tokens, normalized, authorityWords),
            actionRequest = scoreTokens(tokens, normalized, actionWords),
            fearSignal = scoreTokens(tokens, normalized, fearWords),
            isolation = scoreTokens(tokens, normalized, isolationWords),
            sentenceType = detectSentenceType(segment.text),
        )
    }

    private fun tokenize(text: String): Set<String> =
        text.split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.isNotBlank() }
            .toSet()

    private fun scoreTokens(tokens: Set<String>, text: String, dictionary: Set<String>): Float {
        if (dictionary.isEmpty()) return 0f
        var hits = tokens.count { it in dictionary }.toFloat()
        dictionary.forEach { phrase ->
            if (phrase.contains(' ') && text.contains(phrase)) hits += 1f
        }
        return (hits / dictionary.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    private fun detectSentenceType(text: String): SentenceType =
        when {
            text.trim().endsWith('?') -> SentenceType.QUESTION
            text.contains(Regex("""\b(срочно|немедленно|переведите|скажите|продиктуйте|подтвердите)\b""", RegexOption.IGNORE_CASE)) ->
                SentenceType.COMMAND
            else -> SentenceType.STATEMENT
        }

    companion object {
        val URGENCY_WORDS =
            setOf(
                "срочно", "немедленно", "сейчас", "быстро", "сегодня",
                "до", "конца", "дня", "минут", "час",
            )
        val AUTHORITY_WORDS =
            setOf(
                "банк", "банка", "полиция", "следователь", "фсб", "служба",
                "безопасности", "государство", "прокуратура", "суд",
            )
        val ACTION_WORDS =
            setOf(
                "переведите", "перевод", "скажите", "продиктуйте",
                "подтвердите", "установите", "откройте", "переводите",
            )
        val FEAR_WORDS =
            setOf(
                "уголовное", "дело", "арест", "опасность", "угроза",
                "взлом", "мошенники", "подозрительная", "операция",
            )
        val ISOLATION_WORDS =
            setOf(
                "никому", "не", "сообщайте", "говорите", "трубку",
                "секретно", "конфиденциально",
            )
    }
}
