package com.scamdefender.demo

import com.scamdefender.core.CoreEngineFactory
import com.scamdefender.core.audio.WavReader
import com.scamdefender.core.eval.EvalFramework
import com.scamdefender.core.ml.ModelPaths
import com.scamdefender.core.stt.SherpaSttEngine
import com.scamdefender.core.stt.TranscriptParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

fun main(args: Array<String>) {
    val config = parseArgs(args)
    val projectRoot = findProjectRoot()
    val samplesRoot = File(projectRoot, "samples")
    val resolvedConfig = config.copy(output = resolveOutputPath(projectRoot, config.output))

    when (resolvedConfig.mode) {
        Mode.STATUS -> printStatus(projectRoot)
        Mode.EVAL -> runEval(projectRoot, resolvedConfig)
        Mode.TRANSCRIPT, Mode.AUDIO -> runDetection(projectRoot, samplesRoot, resolvedConfig)
    }
}

private fun printStatus(projectRoot: File) {
    val paths = ModelPaths.resolve(File(projectRoot, "models"))
    println("ScamDefender ML components:")
    println("  models root: ${paths.root.absolutePath}")
    println("  Silero VAD:  ${if (paths.sileroAvailable) "OK" else "missing"}")
    println("  MiniLM:      ${if (paths.minilmAvailable) "OK" else "missing (keyword fallback)"}")
    println("  Sherpa STT:  ${if (paths.sherpaAvailable && SherpaSttEngine.isAvailable(paths)) "OK" else "missing"}")
    if (!paths.sileroAvailable || !paths.minilmAvailable || !paths.sherpaAvailable) {
        println()
        println("Run: powershell -ExecutionPolicy Bypass -File scripts\\setup-models.ps1")
    }
}

private fun runDetection(projectRoot: File, samplesRoot: File, config: CliConfig) {
    val report =
        when (config.mode) {
            Mode.TRANSCRIPT -> {
                val transcriptFile = resolveInput(projectRoot, samplesRoot, config.input)
                val segments = TranscriptParser.parse(transcriptFile)
                CoreEngineFactory.forTranscript(segments)
                    .createPipeline()
                    .processSegments(segments, transcriptFile.name)
            }
            Mode.AUDIO -> {
                val audioFile = resolveInput(projectRoot, samplesRoot, config.input)
                val components = CoreEngineFactory.create(File(projectRoot, "models"))
                val wav = WavReader.read(audioFile)
                val chunks = components.segmenter.segment(wav.samples)
                components.createPipeline().process(chunks, audioFile.name)
            }
            Mode.EVAL, Mode.STATUS -> error("unreachable")
        }
    writeOutput(config, report)
}

private fun writeOutput(config: CliConfig, report: com.scamdefender.core.domain.PipelineReport) {
    val json = Json { prettyPrint = true }
    val output = json.encodeToString(report)
    if (config.output != null) {
        val outFile = File(config.output)
        outFile.parentFile?.mkdirs()
        outFile.writeText(output)
        println("Report written to ${outFile.absolutePath}")
    } else {
        println(output)
    }
}

private fun runEval(projectRoot: File, config: CliConfig) {
    val evalDir = File(projectRoot, "eval")
    val samplesRoot = File(projectRoot, "samples")
    val framework = EvalFramework(samplesRoot = samplesRoot)
    val cases = framework.loadCases(evalDir)
    val report = framework.run(cases)

    val outputFile =
        if (config.output != null) {
            File(config.output)
        } else {
            File(projectRoot, "reports/eval_${report.timestamp.replace(":", "-")}.json")
        }
    framework.writeReport(report, outputFile)

    println("Eval: ${report.passedCases}/${report.totalCases} passed (${(report.passRate * 100).toInt()}%)")
    println("Scam recall (min risk): ${(report.scamRecall * 100).toInt()}%")
    println("False positive rate (all non-scam): ${(report.falsePositiveRate * 100).toInt()}%")
    println("Hard-negative FP rate: ${(report.hardNegativeFpRate * 100).toInt()}%")
    report.byTag.forEach { slice ->
        println(
            "  tag=${slice.tag}: ${slice.passedCases}/${slice.totalCases} " +
                "(${(slice.passRate * 100).toInt()}%)",
        )
    }
    val failed = report.results.filter { !it.passed }
    if (failed.isNotEmpty()) {
        println("Failed:")
        failed.forEach { r ->
            println("  - ${r.id}: risk=${r.finalRiskLevel} notes=${r.notes.joinToString("; ")}")
        }
    }
    println("Report: ${outputFile.absolutePath}")
}

private fun resolveInput(projectRoot: File, samplesRoot: File, input: String?): File {
    require(!input.isNullOrBlank()) { "--audio or --transcript path is required" }
    val file = File(input)
    return when {
        file.isAbsolute && file.exists() -> file
        File(projectRoot, input).exists() -> File(projectRoot, input)
        File(samplesRoot, input).exists() -> File(samplesRoot, input)
        else -> error("Input not found: $input")
    }
}

private fun resolveOutputPath(projectRoot: File, output: String?): String? {
    if (output == null) return null
    val file = File(output)
    return if (file.isAbsolute) output else File(projectRoot, output).absolutePath
}

private fun findProjectRoot(): File {
    var dir = File(System.getProperty("user.dir"))
    repeat(5) {
        if (File(dir, "settings.gradle.kts").exists()) return dir
        dir = dir.parentFile ?: return File(System.getProperty("user.dir"))
    }
    return File(System.getProperty("user.dir"))
}

enum class Mode { AUDIO, TRANSCRIPT, EVAL, STATUS }

data class CliConfig(
    val mode: Mode,
    val input: String? = null,
    val output: String? = null,
)

private fun parseArgs(args: Array<String>): CliConfig {
    var mode = Mode.TRANSCRIPT
    var input: String? = null
    var output: String? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--audio" -> {
                mode = Mode.AUDIO
                input = args.getOrNull(++i)
            }
            "--transcript" -> {
                mode = Mode.TRANSCRIPT
                input = args.getOrNull(++i)
            }
            "--eval" -> mode = Mode.EVAL
            "--status" -> mode = Mode.STATUS
            "--output" -> output = args.getOrNull(++i)
            else -> if (input == null && !args[i].startsWith("--")) input = args[i]
        }
        i++
    }
    return CliConfig(mode, input, output)
}
