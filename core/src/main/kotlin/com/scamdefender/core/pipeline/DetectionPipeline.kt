package com.scamdefender.core.pipeline

import com.scamdefender.core.domain.DetectionEvent
import com.scamdefender.core.domain.PipelineReport
import com.scamdefender.core.domain.RiskLevel
import com.scamdefender.core.domain.ScenarioClassification
import com.scamdefender.core.domain.SegmentReport
import com.scamdefender.core.domain.SpeechSegment
import com.scamdefender.core.features.FeatureExtractor
import com.scamdefender.core.llm.ScenarioModel
import com.scamdefender.core.llm.TriggeredScenarioModel
import com.scamdefender.core.patterns.PatternDetector
import com.scamdefender.core.risk.RiskAggregator
import com.scamdefender.core.risk.RiskStateMachine
import com.scamdefender.core.stages.StageTransitionDetector
import com.scamdefender.core.stt.SttEngine
import com.scamdefender.core.domain.AudioChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DetectionPipeline(
    private val stt: SttEngine,
    private val featureExtractor: FeatureExtractor = FeatureExtractor(),
    private val patternDetector: PatternDetector = PatternDetector(),
    private val stageDetector: StageTransitionDetector = StageTransitionDetector(),
    private val scenarioModel: TriggeredScenarioModel = TriggeredScenarioModel(),
    private val riskAggregator: RiskAggregator = RiskAggregator(),
    private val stateMachine: RiskStateMachine = RiskStateMachine(),
    private val transcriptWindowSec: Int = 60,
) {
    private val transcriptBuffer = mutableListOf<SpeechSegment>()

    fun reset() {
        patternDetector.reset()
        stageDetector.reset()
        scenarioModel.reset()
        riskAggregator.reset()
        stateMachine.reset()
        transcriptBuffer.clear()
    }

    fun processSegments(segments: List<SpeechSegment>, source: String = "mock"): PipelineReport {
        reset()
        val reports = segments.map { processSegment(it) }
        return PipelineReport(
            source = source,
            segments = reports,
            events = reports.mapNotNull { it.detectionEvent },
            finalRiskLevel = stateMachine.riskLevel.value,
            finalRiskScore = reports.lastOrNull()?.detectionEvent?.riskScore ?: 0f,
        )
    }

    fun process(audioChunks: List<AudioChunk>, source: String = "audio"): PipelineReport {
        val segments = stt.transcribe(audioChunks)
        return processSegments(segments, source)
    }

    fun processStream(audioStream: Flow<AudioChunk>): Flow<DetectionEvent> = flow {
        reset()
        audioStream.collect { chunk ->
            stt.transcribe(listOf(chunk)).forEach { segment ->
                val report = processSegment(segment)
                report.detectionEvent?.let { emit(it) }
            }
        }
    }

    private fun processSegment(segment: SpeechSegment): SegmentReport {
        appendTranscript(segment)

        val features = featureExtractor.extract(segment)
        val patternResult = patternDetector.detect(segment, features)
        val stageUpdate = stageDetector.update(segment, features, patternResult.patterns)

        var scenario: ScenarioClassification? = null
        if (scenarioModel.shouldInvoke(patternResult.llmTrigger, segment.timestampMs)) {
            scenario =
                scenarioModel.classify(
                    transcriptBuffer.toList(),
                    patternResult.patterns.map { it.type },
                )
        }

        val sequenceProgression = patternDetector.sequenceProgression()
        val riskScore =
            riskAggregator.compute(
                timestampMs = segment.timestampMs,
                features = features,
                patterns = patternResult.patterns,
                stageUpdate = stageUpdate,
                scenario = scenario,
                sequenceProgression = sequenceProgression,
            )

        val riskLevel = stateMachine.update(riskScore)
        val event = buildEvent(segment, features, patternResult.patterns, stageUpdate, scenario, riskLevel, riskScore)

        return SegmentReport(
            segment = segment,
            features = features,
            patterns = patternResult,
            stageUpdate = stageUpdate,
            detectionEvent = event,
        )
    }

    private fun appendTranscript(segment: SpeechSegment) {
        transcriptBuffer.add(segment)
        val cutoff = segment.timestampMs - transcriptWindowSec * 1000L
        transcriptBuffer.removeAll { it.timestampMs < cutoff }
    }

    private fun buildEvent(
        segment: SpeechSegment,
        features: com.scamdefender.core.domain.FeatureVector,
        patterns: List<com.scamdefender.core.domain.DetectedPattern>,
        stageUpdate: com.scamdefender.core.domain.StageUpdate,
        scenario: ScenarioClassification?,
        riskLevel: RiskLevel,
        riskScore: Float,
    ): DetectionEvent? {
        if (riskLevel == RiskLevel.SAFE && !stageUpdate.transitionDetected) return null

        val explanations = mutableListOf<String>()
        patterns.forEach { pattern ->
            explanations.add("${pattern.type.name}: ${pattern.matchedPhrase ?: "detected"}")
        }
        if (features.urgency > 0.5f) explanations.add("Высокая срочность в речи")
        if (features.authority > 0.5f) explanations.add("Присутствует заявленный авторитет")
        if (stageUpdate.transitionDetected) explanations.add("Обнаружен переход стадии: ${stageUpdate.dominantStage.name}")
        scenario?.let { explanations.add("Сценарий: ${it.scenario.name} (${(it.confidence * 100).toInt()}%)") }

        return DetectionEvent(
            timestampMs = segment.timestampMs,
            riskLevel = riskLevel,
            riskScore = riskScore,
            stage = scenario?.stage ?: stageUpdate.dominantStage,
            scenario = scenario?.scenario,
            explanation = explanations,
            patterns = patterns,
            stageTransition = stageUpdate.transitionDetected,
        )
    }
}
