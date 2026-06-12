package com.scamdefender.core

import com.scamdefender.core.audio.AudioSegmenter
import com.scamdefender.core.audio.SileroVadEngine
import com.scamdefender.core.audio.VadEngine
import com.scamdefender.core.domain.SpeechSegment
import com.scamdefender.core.ml.ModelPaths
import com.scamdefender.core.patterns.OnnxSemanticMatcher
import com.scamdefender.core.patterns.PatternDetector
import com.scamdefender.core.pipeline.DetectionPipeline
import com.scamdefender.core.scenarios.ScenarioOntology
import com.scamdefender.core.stt.MockSttEngine
import com.scamdefender.core.stt.SherpaSttEngine
import com.scamdefender.core.stt.SttEngine
import java.io.File

data class CoreComponents(
    val modelPaths: ModelPaths,
    val vadEngine: VadEngine,
    val sttEngine: SttEngine,
    val patternDetector: PatternDetector,
    val segmenter: AudioSegmenter,
) {
    fun createPipeline(): DetectionPipeline =
        DetectionPipeline(stt = sttEngine, patternDetector = patternDetector)
}

object CoreEngineFactory {
    fun create(modelRoot: File? = null): CoreComponents {
        val modelPaths = ModelPaths.resolve(modelRoot)
        val ontology = ScenarioOntology.load()
        val vad = SileroVadEngine.createOrFallback(modelPaths)
        val semanticMatcher =
            OnnxSemanticMatcher.createOrFallback(
                ontology.patternsBank.semanticAnchors,
                ontology::patternTypeForName,
                modelPaths,
            )
        val patternDetector = PatternDetector(ontology, semanticMatcher)
        val stt = SherpaSttEngine(modelPaths)
        val segmenter = AudioSegmenter(vadEngine = vad)
        return CoreComponents(modelPaths, vad, stt, patternDetector, segmenter)
    }

    fun forTranscript(segments: List<SpeechSegment>): CoreComponents {
        val base = create()
        return base.copy(sttEngine = MockSttEngine(segments))
    }
}
