package com.scamdefender.core.eval

import com.scamdefender.core.domain.RiskLevel
import com.scamdefender.core.domain.ScamScenario
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
    val isScam: Boolean = true,
    val expectedStageAtEnd: String? = null,
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
    val notes: List<String>,
)

@Serializable
data class EvalReport(
    val timestamp: String,
    val totalCases: Int,
    val passedCases: Int,
    val passRate: Float,
    val falsePositiveRate: Float,
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
        val passed = results.count { it.passed }
        val legitCases = results.filter { !cases.find { c -> c.id == it.id }!!.isScam }
        val falsePositives = legitCases.count { it.falsePositive }

        return EvalReport(
            timestamp = java.time.Instant.now().toString(),
            totalCases = cases.size,
            passedCases = passed,
            passRate = passed.toFloat() / cases.size.coerceAtLeast(1),
            falsePositiveRate =
                if (legitCases.isEmpty()) 0f
                else falsePositives.toFloat() / legitCases.size,
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
        val detectedScenario = report.segments.lastOrNull()?.detectionEvent?.scenario?.name
        val finalStage = report.segments.lastOrNull()?.stageUpdate?.dominantStage?.name ?: "STAGE_0"

        val timeToHigh =
            report.events
                .firstOrNull { it.riskLevel.ordinal >= RiskLevel.HIGH_RISK.ordinal }
                ?.timestampMs

        val falsePositive = !evalCase.isScam && finalRisk.ordinal >= RiskLevel.SUSPICIOUS.ordinal

        val notes = mutableListOf<String>()
        if (evalCase.isScam && finalRisk.ordinal < minRisk.ordinal) {
            notes.add("Risk ${finalRisk.name} below expected ${minRisk.name}")
        }
        evalCase.expectedScenario?.let { expected ->
            if (detectedScenario != null && !detectedScenario.contains(expected, ignoreCase = true)) {
                notes.add("Scenario mismatch: expected $expected, got $detectedScenario")
            }
        }

        val passed =
            when {
                falsePositive -> false
                evalCase.isScam -> finalRisk.ordinal >= minRisk.ordinal
                else -> finalRisk.ordinal <= RiskLevel.MONITORING.ordinal
            }

        return EvalCaseResult(
            id = evalCase.id,
            passed = passed,
            finalRiskLevel = finalRisk.name,
            detectedScenario = detectedScenario,
            finalStage = finalStage,
            timeToHighRiskMs = timeToHigh,
            falsePositive = falsePositive,
            notes = notes,
        )
    }

    fun writeReport(report: EvalReport, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(json.encodeToString(report))
    }
}
