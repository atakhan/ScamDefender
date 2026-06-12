package com.scamdefender.core.patterns

import com.scamdefender.core.domain.DetectedPatternType
import com.scamdefender.core.ml.ModelPaths
import com.scamdefender.core.scenarios.ScenarioOntology
import kotlin.test.Test
import kotlin.test.assertTrue

class OnnxSemanticMatcherTest {
    @Test
    fun `minilm matches paraphrased action request`() {
        val paths = ModelPaths.resolve()
        if (!paths.minilmAvailable) return

        val ontology = ScenarioOntology.load()
        val matcher =
            OnnxSemanticMatcher.createOrFallback(
                ontology.patternsBank.semanticAnchors,
                ontology::patternTypeForName,
                paths,
            ) as? OnnxSemanticMatcher ?: return

        val matches = matcher.match("нужно срочно подтвердить операцию по счёту")
        assertTrue(matches.any { it.type == DetectedPatternType.ACTION_REQUEST })
    }
}
