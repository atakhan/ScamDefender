package com.scamdefender.core.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WavData(
    val samples: ShortArray,
    val sampleRateHz: Int,
)

object WavReader {
    fun read(file: File): WavData {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        require(readString(buffer, 4) == "RIFF") { "Not a WAV file: $file" }
        buffer.int
        require(readString(buffer, 4) == "WAVE") { "Not a WAVE file: $file" }

        var sampleRate = 16_000
        var channels = 1
        var bitsPerSample = 16
        var audioFormat = 1
        var dataOffset = -1
        var dataSize = 0

        while (buffer.position() + 8 <= buffer.limit()) {
            val chunkId = readString(buffer, 4)
            val chunkSize = buffer.int
            when (chunkId) {
                "fmt " -> {
                    audioFormat = buffer.short.toInt() and 0xFFFF
                    channels = buffer.short.toInt() and 0xFFFF
                    sampleRate = buffer.int
                    buffer.int
                    buffer.short
                    bitsPerSample = buffer.short.toInt() and 0xFFFF
                    val consumed = 16
                    if (chunkSize > consumed) buffer.position(buffer.position() + chunkSize - consumed)
                }
                "data" -> {
                    dataOffset = buffer.position()
                    dataSize = chunkSize
                    buffer.position(buffer.position() + chunkSize)
                }
                else -> buffer.position(buffer.position() + chunkSize)
            }
        }

        require(dataOffset >= 0 && dataSize > 0) { "WAV data chunk not found: $file" }

        val dataBytes = bytes.copyOfRange(dataOffset, dataOffset + dataSize)
        val samples =
            when {
                audioFormat == 1 && bitsPerSample == 16 -> decodePcm16(dataBytes)
                audioFormat == 3 && bitsPerSample == 32 -> decodeFloat32(dataBytes)
                else -> error("Unsupported WAV format $audioFormat / ${bitsPerSample}bit: $file")
            }

        val mono =
            if (channels == 1) {
                samples
            } else {
                ShortArray(samples.size / channels) { i -> samples[i * channels] }
            }

        val resampled =
            if (sampleRate != 16_000) {
                resample(mono, sampleRate, 16_000)
            } else {
                mono
            }

        return WavData(resampled, 16_000)
    }

    private fun decodePcm16(bytes: ByteArray): ShortArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = ShortArray(buffer.remaining())
        buffer.get(samples)
        return samples
    }

    private fun decodeFloat32(bytes: ByteArray): ShortArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val sampleCount = bytes.size / 4
        return ShortArray(sampleCount) {
            val value = buffer.float.coerceIn(-1f, 1f)
            (value * 32_767f).toInt().toShort()
        }
    }

    private fun resample(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate) return input
        val ratio = fromRate.toDouble() / toRate
        val outputSize = (input.size / ratio).toInt()
        return ShortArray(outputSize) { i ->
            val srcIndex = (i * ratio).toInt().coerceIn(0, input.lastIndex)
            input[srcIndex]
        }
    }

    private fun readString(buffer: ByteBuffer, length: Int): String {
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return String(bytes, Charsets.US_ASCII)
    }
}
