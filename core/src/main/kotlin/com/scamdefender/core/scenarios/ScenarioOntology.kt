package com.scamdefender.core.scenarios

import com.scamdefender.core.domain.AttackStage
import com.scamdefender.core.domain.DetectedPatternType
import com.scamdefender.core.domain.ScamScenario
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StageDefinition(
    val id: String,
    val name: String,
    val description: String,
)

@Serializable
data class ScenarioDefinition(
    val id: String,
    val name: String,
    val typicalPath: List<String>,
)

@Serializable
data class OntologyDocument(
    val stages: List<StageDefinition>,
    val scenarios: List<ScenarioDefinition>,
)

@Serializable
data class PhrasePattern(
    val phrase: String,
    val pattern: String,
    val weight: Float = 1f,
)

@Serializable
data class SemanticAnchor(
    val anchor: String,
    val pattern: String,
    val similarPhrases: List<String> = emptyList(),
)

@Serializable
data class PatternsBankDocument(
    val phrases: List<PhrasePattern>,
    val semanticAnchors: List<SemanticAnchor> = emptyList(),
    val fsmTransitions: List<String> = emptyList(),
)

class ScenarioOntology private constructor(
    val ontology: OntologyDocument,
    val patternsBank: PatternsBankDocument,
) {
    val stageIds: Set<String> = ontology.stages.map { it.id }.toSet()
    val scenarioIds: Set<String> = ontology.scenarios.map { it.id }.toSet()

    fun scenarioForId(id: String): ScamScenario? =
        when (id) {
            "bank_fraud_impersonation" -> ScamScenario.BANK_FRAUD_IMPERSONATION
            "police_authority_scam" -> ScamScenario.POLICE_AUTHORITY_SCAM
            "child_in_trouble" -> ScamScenario.CHILD_IN_TROUBLE
            "investment_fraud" -> ScamScenario.INVESTMENT_FRAUD
            "tech_support_scam" -> ScamScenario.TECH_SUPPORT_SCAM
            "delivery_marketplace_scam" -> ScamScenario.DELIVERY_MARKETPLACE_SCAM
            "financial_transfer_scam" -> ScamScenario.FINANCIAL_TRANSFER_SCAM
            else -> null
        }

    fun stageForId(id: String): AttackStage? =
        AttackStage.entries.find { it.name == id }

    fun patternTypeForName(name: String): DetectedPatternType? =
        DetectedPatternType.entries.find { it.name == name }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(): ScenarioOntology {
            val ontologyStream =
                requireNotNull(
                    ScenarioOntology::class.java.classLoader.getResourceAsStream("scenarios/ontology.json"),
                ) { "scenarios/ontology.json not found" }
            val patternsStream =
                requireNotNull(
                    ScenarioOntology::class.java.classLoader.getResourceAsStream("scenarios/patterns_bank.json"),
                ) { "scenarios/patterns_bank.json not found" }

            return ScenarioOntology(
                ontology = json.decodeFromString(ontologyStream.bufferedReader().readText()),
                patternsBank = json.decodeFromString(patternsStream.bufferedReader().readText()),
            )
        }
    }
}
