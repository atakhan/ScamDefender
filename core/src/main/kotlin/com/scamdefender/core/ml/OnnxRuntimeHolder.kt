package com.scamdefender.core.ml

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object OnnxRuntimeHolder {
    val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val sessions = ConcurrentHashMap<String, OrtSession>()

    fun sessionFor(modelFile: File): OrtSession? {
        if (!modelFile.isFile) return null
        return sessions.getOrPut(modelFile.absolutePath) {
            environment.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
        }
    }

    fun closeAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
    }
}

class ManagedOnnxSession(
    private val modelFile: File,
) : Closeable {
    val session: OrtSession? = OnnxRuntimeHolder.sessionFor(modelFile)

    val isAvailable: Boolean get() = session != null

    override fun close() {
        // Sessions are cached in OnnxRuntimeHolder for reuse.
    }
}
