package com.scamdefender.core.eval

import com.scamdefender.core.domain.RiskLevel
import com.scamdefender.core.pipeline.DetectionPipeline
import com.scamdefender.core.stt.MockSttEngine
import com.scamdefender.core.stt.TranscriptParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class EvalCase(
    val id: String,
    val transcriptFile: String,
    val expectedScenario: String? = null,
    val expectedMinRisk: String = "SUSPICIOUS",
    /** Upper bound on final risk (partial attacks, non-scam). Non-scam defaults to MONITORING if null. */
    val expectedMaxRisk: String? = null,
    val isScam: Boolean = true,
    val expectedStageAtEnd: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class EvalCaseResult(
    val id: String,
    val passed: Boolean,
    val finalRiskLevel: String,
    val detectedScenario: String?,
    val finalStage: String,
    val timeToHighRiskMs: Long?,
    val falsePositive: Boolean,
    val tags: List<String> = emptyList(),
    val notes: List<String>,
)

@Serializable
data class TagSlice(
    val tag: String,
    val totalCases: Int,
    val passedCases: Int,
    val passRate: Float,
)

@Serializable
data class EvalReport(
    val timestamp: String,
    val totalCases: Int,
    val passedCases: Int,
    val passRate: Float,
    /** Non-scam cases with finalRisk ≥ SUSPICIOUS. */
    val falsePositiveRate: Float,
    /** Scam cases that reached at least their expectedMinRisk. */
    val scamRecall: Float,
    /** hard_negative-tagged cases with finalRisk ≥ SUSPICIOUS. */
    val hardNegativeFpRate: Float,
    val byTag: List<TagSlice>,
    val results: List<EvalCaseResult>,
)

class EvalFramework(
    private val pipeline: DetectionPipeline = DetectionPipeline(MockSttEngine(emptyList())),
    private val samplesRoot: File,
) {
    private val json = Json { prettyPrint = true }

    fun loadCases(evalDir: File): List<EvalCase> {
        val manifest = File(evalDir, "manifest.json")
        require(manifest.exists()) { "Eval manifest not found: $manifest" }
        return json.decodeFromString<List<EvalCase>>(manifest.readText())
    }

    fun run(cases: List<EvalCase>): EvalReport {
        val results = cases.map { runCase(it) }
        val byId = cases.associateBy { it.id }
        val passed = results.count { it.passed }

        val nonScamResults = results.filter { byId.getValue(it.id).isScam.not() }
        val falsePositives = nonScamResults.count { it.falsePositive }

        val scamResults = results.filter { byId.getValue(it.id).isScam }
        val scamHits =
            scamResults.count { result ->
                val min = RiskLevel.valueOf(byId.getValue(result.id).expectedMinRisk)
                RiskLevel.valueOf(result.finalRiskLevel).ordinal >= min.ordinal
            }

        val hardNegatives = results.filter { "hard_negative" in it.tags }
        val hardNegFp = hardNegatives.count { it.falsePositive }

        val allTags = results.flatMap { it.tags }.toSet().sorted()
        val byTag =
            allTags.map { tag ->
                val slice = results.filter { tag in it.tags }
                TagSlice(
                    tag = tag,
                    totalCases = slice.size,
                    passedCases = slice.count { it.passed },
                    passRate = slice.count { it.passed }.toFloat() / slice.size.coerceAtLeast(1),
                )
            }

        return EvalReport(
            timestamp = java.time.Instant.now().toString(),
            totalCases = cases.size,
            passedCases = passed,
            passRate = passed.toFloat() / cases.size.coerceAtLeast(1),
            falsePositiveRate =
                if (nonScamResults.isEmpty()) 0f
                else falsePositives.toFloat() / nonScamResults.size,
            scamRecall =
                if (scamResults.isEmpty()) 0f
                else scamHits.toFloat() / scamResults.size,
            hardNegativeFpRate =
                if (hardNegatives.isEmpty()) 0f
                else hardNegFp.toFloat() / hardNegatives.size,
            byTag = byTag,
            results = results,
        )
    }

    fun runCase(evalCase: EvalCase): EvalCaseResult {
        val transcriptFile = File(samplesRoot, evalCase.transcriptFile)
        val segments = TranscriptParser.parse(transcriptFile)
        val casePipeline = DetectionPipeline(MockSttEngine(segments))
        val report = casePipeline.processSegments(segments, evalCase.id)

        val finalRisk = report.finalRiskLevel
        val minRisk = RiskLevel.valueOf(evalCase.expectedMinRisk)
        val maxRisk =
            evalCase.expectedMaxRisk?.let { RiskLevel.valueOf(it) }
                ?: if (!evalCase.isScam) RiskLevel.MONITORING else null

        val detectedScenario = report.segments.lastOrNull()?.detectionEvent?.scenario?.name
        val finalStage = report.segments.lastOrNull()?.stageUpdate?.dominantStage?.name ?: "STAGE_0"

        val timeToHigh =
            report.events
                .firstOrNull { it.riskLevel.ordinal >= RiskLevel.HIGH_RISK.ordinal }
                ?.timestampMs

        val falsePositive = !evalCase.isScam && finalRisk.ordinal >= RiskLevel.SUSPICIOUS.ordinal

        val notes = mutableListOf<String>()
        if (evalCase.isScam && finalRisk.ordinal < minRisk.ordinal) {
            notes.add("Risk ${finalRisk.name} below expected min ${minRisk.name}")
        }
        if (maxRisk != null && finalRisk.ordinal > maxRisk.ordinal) {
            notes.add("Risk ${finalRisk.name} above expected max ${maxRisk.name}")
        }
        evalCase.expectedScenario?.let { expected ->
            if (detectedScenario != null && !detectedScenario.contains(expected, ignoreCase = true)) {
                notes.add("Scenario mismatch: expected $expected, got $detectedScenario")
            }
        }

        val passed =
            if (evalCase.isScam) {
                finalRisk.ordinal >= minRisk.ordinal &&
                    (maxRisk == null || finalRisk.ordinal <= maxRisk.ordinal)
            } else {
                maxRisk != null && finalRisk.ordinal <= maxRisk.ordinal
            }

        return EvalCaseResult(
            id = evalCase.id,
            passed = passed,
            finalRiskLevel = finalRisk.name,
            detectedScenario = detectedScenario,
            finalStage = finalStage,
            timeToHighRiskMs = timeToHigh,
            falsePositive = falsePositive,
            tags = evalCase.tags,
            notes = notes,
        )
    }

    fun writeReport(report: EvalReport, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(json.encodeToString(report))
    }
}
