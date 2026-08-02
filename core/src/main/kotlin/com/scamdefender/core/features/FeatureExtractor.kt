package com.scamdefender.core.features

import com.scamdefender.core.domain.FeatureVector
import com.scamdefender.core.domain.SentenceType
import com.scamdefender.core.domain.SpeechSegment

/**
 * Lexical feature extractor. Scores saturate on hits (not diluted by dictionary size).
 * Multi-word phrases outweigh single tokens; negation phrases damp action/isolation.
 */
class FeatureExtractor(
    private val urgencyPhrases: List<String> = URGENCY_PHRASES,
    private val urgencyTokens: Set<String> = URGENCY_TOKENS,
    private val authorityPhrases: List<String> = AUTHORITY_PHRASES,
    private val authorityTokens: Set<String> = AUTHORITY_TOKENS,
    private val actionPhrases: List<String> = ACTION_PHRASES,
    private val actionTokens: Set<String> = ACTION_TOKENS,
    private val fearPhrases: List<String> = FEAR_PHRASES,
    private val fearTokens: Set<String> = FEAR_TOKENS,
    private val isolationPhrases: List<String> = ISOLATION_PHRASES,
    private val isolationTokens: Set<String> = ISOLATION_TOKENS,
    private val actionNegations: List<String> = ACTION_NEGATIONS,
) {
    fun extract(segment: SpeechSegment): FeatureVector {
        val normalized = segment.text.lowercase()
        val tokens = tokenize(normalized)

        var action = score(normalized, tokens, actionPhrases, actionTokens)
        if (actionNegations.any { it in normalized }) {
            action *= 0.12f
        }

        var isolation = score(normalized, tokens, isolationPhrases, isolationTokens)
        if (actionNegations.any { it in normalized }) {
            isolation *= 0.5f
        }

        return FeatureVector(
            urgency = score(normalized, tokens, urgencyPhrases, urgencyTokens),
            authority = score(normalized, tokens, authorityPhrases, authorityTokens),
            actionRequest = action,
            fearSignal = score(normalized, tokens, fearPhrases, fearTokens),
            isolation = isolation,
            sentenceType = detectSentenceType(segment.text),
        )
    }

    private fun tokenize(text: String): Set<String> =
        text.split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.isNotBlank() }
            .toSet()

    private fun score(
        text: String,
        tokens: Set<String>,
        phrases: List<String>,
        tokenDict: Set<String>,
    ): Float {
        var raw = 0f
        phrases.forEach { phrase ->
            if (phrase in text) raw += 0.55f
        }
        tokenDict.forEach { token ->
            if (token in tokens) raw += 0.32f
        }
        return raw.coerceIn(0f, 1f)
    }

    private fun detectSentenceType(text: String): SentenceType =
        when {
            text.trim().endsWith('?') -> SentenceType.QUESTION
            text.contains(
                Regex(
                    """\b(срочно|немедленно|переведите|продиктуйте|назовите|передайте)\b""",
                    RegexOption.IGNORE_CASE,
                ),
            ) -> SentenceType.COMMAND
            else -> SentenceType.STATEMENT
        }

    companion object {
        val URGENCY_PHRASES =
            listOf(
                "нельзя откладывать", "прямо сейчас", "ближайшие минуты",
                "время критичн", "без быстрого", "до конца дня",
                "без оплаты сегодня", "уйдёт обратно",
            )
        val URGENCY_TOKENS =
            setOf(
                "срочно", "немедленно", "критичное", "критично", "быстрее",
                "минуты", "незамедлительно",
            )

        val AUTHORITY_PHRASES =
            listOf(
                "службы безопасности", "служба безопасности", "из банка",
                "уголовного розыска", "борьбы с мошенничеством",
                "центр защиты", "служба поддержки", "это полиция",
            )
        val AUTHORITY_TOKENS =
            setOf(
                "банк", "банка", "банком", "полиция", "следователь", "фсб",
                "прокуратура", "розыска", "юрист", "участковый",
                "безопасность", "оператора",
            )

        val ACTION_PHRASES =
            listOf(
                "код из смс", "цифры из сообщения", "продиктуйте код",
                "скажите код", "назовите цифры", "защищённый счёт",
                "безопасный счёт", "переведите деньги", "передайте сумму",
                "скачайте утилиту", "пароль из письма", "реквизиты для",
            )
        val ACTION_TOKENS =
            setOf(
                "переведите", "переводите", "продиктуйте", "реквизиты",
                "наличными", "утилиту", "anydesk",
            )

        val FEAR_PHRASES =
            listOf(
                "уголовное дело", "материалы по статье", "подозрительная операция",
                "странный платёж", "ваш сын", "ребёнок задержан", "в опасности",
            )
        val FEAR_TOKENS =
            setOf(
                "арест", "взлом", "взломать", "задержан", "угроза",
                "мошенники", "опасность", "статья",
            )

        val ISOLATION_PHRASES =
            listOf(
                "не сообщайте никому", "никому не говорите", "не кладите трубку",
                "в тайне", "не рассказывать", "лучше не звонить",
                "не звоните ему", "тайне от родственников",
            )
        val ISOLATION_TOKENS =
            setOf(
                "секретно", "конфиденциально", "тихо",
            )

        val ACTION_NEGATIONS =
            listOf(
                "не запрашиваем", "не просим", "не требуется", "не дикту",
                "никаких оплат", "никаких переводов", "не нужно",
                "ставить не нужно",
            )

        // Back-compat aliases for older call sites / tests
        val URGENCY_WORDS: Set<String> = URGENCY_TOKENS + URGENCY_PHRASES.toSet()
        val AUTHORITY_WORDS: Set<String> = AUTHORITY_TOKENS + AUTHORITY_PHRASES.toSet()
        val ACTION_WORDS: Set<String> = ACTION_TOKENS + ACTION_PHRASES.toSet()
        val FEAR_WORDS: Set<String> = FEAR_TOKENS + FEAR_PHRASES.toSet()
        val ISOLATION_WORDS: Set<String> = ISOLATION_TOKENS + ISOLATION_PHRASES.toSet()
    }
}
