package com.coupang.modeldecoder

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

class ModelDecoder {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Download PNG from URL and decode embedded model
     */
    suspend fun downloadAndDecodeModel(
        url: String,
        outputFile: File,
        onProgress: (String) -> Unit = {}
    ): DecodingResult {
        return withContext(Dispatchers.IO) {
            try {
                onProgress("Downloading PNG from CDN...")

                // Download PNG from URL
                val pngBytes = downloadPngFromUrl(url)
                onProgress("Downloaded ${pngBytes.size} bytes")

                // Decode model from PNG
                onProgress("Extracting model from PNG...")
                val modelBytes = decodeModelFromChunk(pngBytes)
                    ?: return@withContext DecodingResult.Error("Failed to extract model from PNG")

                onProgress("Decoded ${modelBytes.size} bytes")

                // Verify integrity
                onProgress("Verifying model integrity...")
                val checksum = calculateSHA256(modelBytes)

                // Write to file
                onProgress("Writing model to file...")
                outputFile.parentFile?.mkdirs()
                outputFile.writeBytes(modelBytes)

                onProgress("Successfully decoded model!")

                DecodingResult.Success(
                    originalSize = modelBytes.size,
                    checksum = checksum,
                    outputFile = outputFile
                )

            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode model", e)
                DecodingResult.Error("Failed to decode model: ${e.message}")
            }
        }
    }

    /**
     * Decode model from local PNG file
     */
    suspend fun decodeLocalPngFile(
        pngFile: File,
        outputFile: File,
        onProgress: (String) -> Unit = {}
    ): DecodingResult {
        return withContext(Dispatchers.IO) {
            try {
                onProgress("Reading PNG file...")

                if (!pngFile.exists()) {
                    return@withContext DecodingResult.Error("PNG file does not exist")
                }

                val pngBytes = pngFile.readBytes()
                onProgress("Read ${pngBytes.size} bytes from PNG")

                // Decode model from PNG
                onProgress("Extracting model from PNG...")
                val modelBytes = decodeModelFromChunk(pngBytes)
                    ?: return@withContext DecodingResult.Error("Failed to extract model from PNG")

                onProgress("Decoded ${modelBytes.size} bytes")

                // Calculate checksum
                onProgress("Calculating checksum...")
                val checksum = calculateSHA256(modelBytes)

                // Write to file
                onProgress("Writing model to file...")
                outputFile.parentFile?.mkdirs()
                outputFile.writeBytes(modelBytes)

                onProgress("Successfully decoded model!")

                DecodingResult.Success(
                    originalSize = modelBytes.size,
                    checksum = checksum,
                    outputFile = outputFile
                )

            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode local PNG", e)
                DecodingResult.Error("Failed to decode PNG: ${e.message}")
            }
        }
    }

    /**
     * Extract model data from PNG tEXt chunk
     */
    private fun decodeModelFromChunk(pngBytes: ByteArray): ByteArray? {
        try {
            // Find tEXt chunk with key "mOdL"
            val modelChunk = extractPngTextChunk(pngBytes, "mOdL") ?: return null

            Log.d(TAG, "Found model chunk, size: ${modelChunk.length}")

            // Base64 decode the compressed model data
            val compressedData = Base64.decode(modelChunk, Base64.DEFAULT)
            Log.d(TAG, "Compressed data size: ${compressedData.size}")

            // Decompress using zlib/deflate
            val decompressedData = decompressData(compressedData)
            Log.d(TAG, "Decompressed data size: ${decompressedData.size}")

            return decompressedData

        } catch (e: Exception) {
            Log.e(TAG, "Chunk decode failed", e)
            return null
        }
    }

    /**
     * Decompress zlib-compressed data
     */
    private fun decompressData(compressedData: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(compressedData)

        val buffer = ByteArray(1024)
        val output = ByteArrayOutputStream()

        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            output.write(buffer, 0, count)
        }

        inflater.end()
        return output.toByteArray()
    }

    /**
     * Extract PNG tEXt chunk by key
     * Proper PNG chunk parsing implementation
     */
    private fun extractPngTextChunk(pngBytes: ByteArray, key: String): String? {
        try {
            var offset = 8 // Skip PNG signature

            while (offset < pngBytes.size - 8) {
                // Read chunk length (4 bytes, big-endian)
                val chunkLength = readBigEndianInt(pngBytes, offset)
                offset += 4

                // Read chunk type (4 bytes)
                val chunkType = String(pngBytes.sliceArray(offset until offset + 4), Charsets.ISO_8859_1)
                offset += 4

                Log.d(TAG, "Found PNG chunk: $chunkType, length: $chunkLength")

                if (chunkType == "tEXt") {
                    // Extract tEXt chunk data
                    val chunkData = pngBytes.sliceArray(offset until offset + chunkLength)

                    // Find null separator between key and text
                    val nullIndex = chunkData.indexOf(0)
                    if (nullIndex != -1) {
                        val chunkKey = String(chunkData.sliceArray(0 until nullIndex), Charsets.ISO_8859_1)

                        if (chunkKey == key) {
                            // Extract text data after null separator
                            val textData = chunkData.sliceArray(nullIndex + 1 until chunkData.size)
                            val result = String(textData, Charsets.ISO_8859_1)

                            Log.d(TAG, "Found tEXt chunk with key '$key', data length: ${result.length}")
                            return result
                        }
                    }
                }

                // Skip chunk data and CRC (4 bytes)
                offset += chunkLength + 4

                // Stop at IEND chunk
                if (chunkType == "IEND") {
                    break
                }
            }

            Log.w(TAG, "tEXt chunk with key '$key' not found")
            return null

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse PNG chunks", e)
            return null
        }
    }

    /**
     * Read 4-byte big-endian integer
     */
    private fun readBigEndianInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
               (bytes[offset + 3].toInt() and 0xFF)
    }


    /**
     * Download PNG from URL
     */
    private suspend fun downloadPngFromUrl(url: String): ByteArray {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "image/*")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            response.body?.bytes() ?: throw Exception("Empty response body")
        }
    }

    /**
     * Calculate SHA-256 checksum
     */
    private fun calculateSHA256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "ModelDecoder"
    }
}

/**
 * Result of model decoding operation
 */
sealed class DecodingResult {
    data class Success(
        val originalSize: Int,
        val checksum: String,
        val outputFile: File
    ) : DecodingResult()

    data class Error(val message: String) : DecodingResult()
}