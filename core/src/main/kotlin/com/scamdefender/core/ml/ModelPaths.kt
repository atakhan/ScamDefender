package com.scamdefender.core.ml

import java.io.File

enum class SherpaModelType {
    NEMO_TRANSDUCER_OFFLINE,
    UNKNOWN,
}

data class ModelPaths(
    val root: File,
    val sileroVad: File = File(root, "silero/silero_vad.onnx"),
    val minilmOnnx: File = File(root, "minilm/model.onnx"),
    val minilmTokenizer: File = File(root, "minilm/tokenizer.json"),
    val sherpaDir: File = File(root, "sherpa-ru"),
    val sherpaEncoder: File = findOnnxFile(File(root, "sherpa-ru"), "encoder"),
    val sherpaDecoder: File = findOnnxFile(File(root, "sherpa-ru"), "decoder"),
    val sherpaJoiner: File = findOnnxFile(File(root, "sherpa-ru"), "joiner"),
    val sherpaTokens: File = findRequiredFile(File(root, "sherpa-ru"), "tokens.txt"),
    val sherpaModelType: SherpaModelType = detectSherpaModelType(File(root, "sherpa-ru")),
    val sherpaExampleWav: File? = findOptionalFile(File(root, "sherpa-ru"), "example.wav"),
) {
    val sileroAvailable: Boolean get() = sileroVad.isFile
    val minilmAvailable: Boolean get() = minilmOnnx.isFile && minilmTokenizer.isFile
    val sherpaAvailable: Boolean
        get() =
            sherpaEncoder.isFile &&
                sherpaDecoder.isFile &&
                sherpaJoiner.isFile &&
                sherpaTokens.isFile &&
                sherpaModelType != SherpaModelType.UNKNOWN

    companion object {
        fun resolve(explicitRoot: File? = null): ModelPaths {
            val root =
                explicitRoot
                    ?: sequenceOf(
                        System.getenv("SCAMDEFENDER_MODELS"),
                        "models",
                    ).mapNotNull { path ->
                        path?.let { candidate ->
                            val dir = File(candidate)
                            if (dir.isDirectory) dir else null
                        }
                    }.firstOrNull()
                    ?: findProjectModelsDir()
                    ?: File("models")

            return ModelPaths(root)
        }

        private fun findProjectModelsDir(): File? {
            var dir = File(System.getProperty("user.dir"))
            repeat(6) {
                val models = File(dir, "models")
                if (models.isDirectory) return models
                dir = dir.parentFile ?: return null
            }
            return null
        }

        private fun findOnnxFile(dir: File, prefix: String): File {
            if (!dir.isDirectory) return File(dir, "$prefix.onnx")
            return dir.walkTopDown()
                .firstOrNull { it.isFile && it.name.startsWith(prefix) && it.extension == "onnx" }
                ?: File(dir, "$prefix.onnx")
        }

        private fun findRequiredFile(dir: File, name: String): File {
            if (!dir.isDirectory) return File(dir, name)
            return dir.walkTopDown().firstOrNull { it.isFile && it.name == name } ?: File(dir, name)
        }

        private fun findOptionalFile(dir: File, name: String): File? {
            if (!dir.isDirectory) return null
            return dir.walkTopDown().firstOrNull { it.isFile && it.name == name }
        }

        private fun detectSherpaModelType(dir: File): SherpaModelType {
            if (!dir.isDirectory) return SherpaModelType.UNKNOWN
            val hasEncoderInt8 =
                dir.walkTopDown().any { it.isFile && it.name.startsWith("encoder") && it.name.contains("int8") }
            val looksLikeGigaAm =
                dir.walkTopDown().any { it.isDirectory && it.name.contains("giga-am", ignoreCase = true) } ||
                    dir.walkTopDown().any { it.isFile && it.name.contains("giga-am", ignoreCase = true) }
            return when {
                hasEncoderInt8 || looksLikeGigaAm -> SherpaModelType.NEMO_TRANSDUCER_OFFLINE
                findOnnxFile(dir, "encoder").isFile -> SherpaModelType.NEMO_TRANSDUCER_OFFLINE
                else -> SherpaModelType.UNKNOWN
            }
        }
    }
}

data class SherpaJarPaths(
    val apiJar: File,
    val nativeJar: File,
) {
    val available: Boolean get() = apiJar.isFile && nativeJar.isFile

    companion object {
        fun resolve(libsDir: File = File("core/libs")): SherpaJarPaths {
            val resolvedDir =
                when {
                    libsDir.isDirectory -> libsDir
                    File("core/libs").isDirectory -> File("core/libs")
                    else -> libsDir
                }
            val api =
                resolvedDir.listFiles()
                    ?.filter { it.name.startsWith("sherpa-onnx-v") && it.name.endsWith(".jar") && !it.name.contains("native") }
                    ?.maxByOrNull { it.name }
                    ?: File(resolvedDir, "sherpa-onnx.jar")
            val native =
                resolvedDir.listFiles()
                    ?.firstOrNull { it.name.contains("native-lib") && it.name.endsWith(".jar") }
                    ?: File(resolvedDir, "sherpa-onnx-native.jar")
            return SherpaJarPaths(api, native)
        }

        fun isOnClasspath(): Boolean =
            runCatching {
                Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
                true
            }.getOrDefault(false)
    }
}
