# Client Deployment Guide

This guide shows how to use PNG-encoded models to bypass CDN restrictions that only allow image formats, then decode them on Android/iOS clients.

## Workflow Overview

1. **Encode models** → PNG files using `model_encoder.py`
2. **Upload PNGs** → CDN (passes image format validation)
3. **Download PNGs** → Android/iOS apps fetch images from CDN
4. **Decode models** → Extract original model files on device
5. **Load models** → Use with MLKit/CoreML as normal

## 1. Encoding Models for CDN

```bash
# Encode your models to PNG (use chunk method for efficiency)
python model_encoder.py encode model_coreml.mlmodel bin/ios_model.png --method chunk
python model_encoder.py encode odt-original.tflite bin/android_model.png --method chunk

# Upload these PNG files to your CDN
# They will pass image format validation
```

## 2. Android Implementation (Java/Kotlin)

### Add Dependencies
```gradle
// app/build.gradle
dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
}
```

### Kotlin Decoder
```kotlin
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

class ModelDecoder {

    /**
     * Download PNG from CDN and decode embedded model
     */
    suspend fun downloadAndDecodeModel(cdnUrl: String): ByteArray? {
        return try {
            // Download PNG from CDN
            val pngBytes = downloadPngFromCdn(cdnUrl)

            // Decode model from PNG chunk
            decodeModelFromChunk(pngBytes)
        } catch (e: Exception) {
            Log.e("ModelDecoder", "Failed to decode model", e)
            null
        }
    }

    /**
     * Extract model data from PNG tEXt chunk
     */
    private fun decodeModelFromChunk(pngBytes: ByteArray): ByteArray? {
        try {
            // Parse PNG to find tEXt chunk with key "mOdL"
            val modelChunk = extractPngTextChunk(pngBytes, "mOdL") ?: return null

            // Base64 decode the compressed model data
            val compressedData = Base64.decode(modelChunk, Base64.DEFAULT)

            // Decompress using zlib/deflate
            return decompressData(compressedData)
        } catch (e: Exception) {
            Log.e("ModelDecoder", "Chunk decode failed", e)
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
     * Simplified implementation - you may need a more robust PNG parser
     */
    private fun extractPngTextChunk(pngBytes: ByteArray, key: String): String? {
        // This is a simplified approach - for production, consider using
        // a proper PNG parsing library or implement full PNG chunk parsing

        try {
            val pngString = String(pngBytes, Charsets.ISO_8859_1)
            val keyPattern = "$key\u0000"
            val keyIndex = pngString.indexOf(keyPattern)

            if (keyIndex == -1) return null

            val dataStart = keyIndex + keyPattern.length
            val dataEnd = pngString.indexOf('\u0000', dataStart)

            return if (dataEnd == -1) {
                pngString.substring(dataStart)
            } else {
                pngString.substring(dataStart, dataEnd)
            }
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Download PNG from CDN
     */
    private suspend fun downloadPngFromCdn(url: String): ByteArray {
        // Use your preferred HTTP client (OkHttp, Retrofit, etc.)
        // This is a placeholder implementation
        return withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection()
            connection.inputStream.readBytes()
        }
    }
}
```

### Usage in Android
```kotlin
class ModelManager {
    private val decoder = ModelDecoder()

    suspend fun loadModelFromCdn() {
        try {
            // Download and decode model from CDN
            val modelBytes = decoder.downloadAndDecodeModel(
                "https://your-cdn.com/android_model.png"
            )

            if (modelBytes != null) {
                // Save to local file for MLKit
                val modelFile = File(context.filesDir, "model.tflite")
                modelFile.writeBytes(modelBytes)

                // Load with MLKit
                val interpreter = Interpreter(modelFile)
                // Use interpreter for inference...
            }
        } catch (e: Exception) {
            Log.e("ModelManager", "Failed to load model", e)
        }
    }
}
```

## 3. iOS Implementation (Swift)

### Swift Decoder
```swift
import UIKit
import Compression

class ModelDecoder {

    /**
     * Download PNG from CDN and decode embedded model
     */
    func downloadAndDecodeModel(from cdnUrl: String) async -> Data? {
        do {
            // Download PNG from CDN
            guard let url = URL(string: cdnUrl),
                  let pngData = try await downloadPngFromCdn(url: url) else {
                return nil
            }

            // Decode model from PNG chunk
            return decodeModelFromChunk(pngData: pngData)
        } catch {
            print("Failed to decode model: \(error)")
            return nil
        }
    }

    /**
     * Extract model data from PNG tEXt chunk
     */
    private func decodeModelFromChunk(pngData: Data) -> Data? {
        do {
            // Extract tEXt chunk with key "mOdL"
            guard let modelChunk = extractPngTextChunk(pngData: pngData, key: "mOdL") else {
                return nil
            }

            // Base64 decode
            guard let compressedData = Data(base64Encoded: modelChunk) else {
                return nil
            }

            // Decompress using Apple's Compression framework
            return decompressData(compressedData)
        } catch {
            print("Chunk decode failed: \(error)")
            return nil
        }
    }

    /**
     * Decompress zlib-compressed data
     */
    private func decompressData(_ compressedData: Data) -> Data? {
        return compressedData.withUnsafeBytes { bytes in
            let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bytes.count * 4)
            defer { buffer.deallocate() }

            let decompressedSize = compression_decode_buffer(
                buffer, bytes.count * 4,
                bytes.bindMemory(to: UInt8.self).baseAddress!, bytes.count,
                nil, COMPRESSION_ZLIB
            )

            guard decompressedSize > 0 else { return nil }
            return Data(bytes: buffer, count: decompressedSize)
        }
    }

    /**
     * Extract PNG tEXt chunk by key
     */
    private func extractPngTextChunk(pngData: Data, key: String) -> String? {
        // Simplified PNG tEXt chunk extraction
        // For production, consider using a proper PNG parsing library

        let keyData = key.data(using: .utf8)! + Data([0]) // null-terminated key

        guard let keyRange = pngData.range(of: keyData) else {
            return nil
        }

        let dataStart = keyRange.upperBound
        let remainingData = pngData[dataStart...]

        // Find next null byte or end of data
        if let nullIndex = remainingData.firstIndex(of: 0) {
            let textData = remainingData[..<nullIndex]
            return String(data: textData, encoding: .utf8)
        } else {
            return String(data: remainingData, encoding: .utf8)
        }
    }

    /**
     * Download PNG from CDN
     */
    private func downloadPngFromCdn(url: URL) async throws -> Data? {
        let (data, _) = try await URLSession.shared.data(from: url)
        return data
    }
}
```

### Usage in iOS
```swift
class ModelManager {
    private let decoder = ModelDecoder()

    func loadModelFromCdn() async {
        do {
            // Download and decode model from CDN
            guard let modelData = await decoder.downloadAndDecodeModel(
                from: "https://your-cdn.com/ios_model.png"
            ) else {
                print("Failed to decode model")
                return
            }

            // Save to local file for CoreML
            let documentsPath = FileManager.default.urls(for: .documentDirectory,
                                                       in: .userDomainMask)[0]
            let modelURL = documentsPath.appendingPathComponent("model.mlmodel")

            try modelData.write(to: modelURL)

            // Load with CoreML
            let model = try MLModel(contentsOf: modelURL)
            // Use model for inference...

        } catch {
            print("Failed to load model: \(error)")
        }
    }
}
```

## 4. CDN Upload Process

```bash
# 1. Encode models for each platform
python model_encoder.py encode ios_model.mlmodel bin/ios_model.png --method chunk
python model_encoder.py encode android_model.tflite bin/android_model.png --method chunk

# 2. Upload to CDN (example with AWS S3)
aws s3 cp bin/ios_model.png s3://your-cdn-bucket/models/ios_model.png --content-type image/png
aws s3 cp bin/android_model.png s3://your-cdn-bucket/models/android_model.png --content-type image/png

# 3. Verify CDN accepts them as images
curl -I https://your-cdn.com/models/ios_model.png
# Should return: Content-Type: image/png
```

## 5. Benefits of This Approach

✅ **CDN Compatible**: PNG files pass image format validation
✅ **Compressed**: ~3x compression ratio reduces bandwidth
✅ **Secure**: Models are encoded, not immediately recognizable
✅ **Cacheable**: CDN can cache images normally
✅ **Version Control**: Easy to manage different model versions

## 6. Production Considerations

- **Error Handling**: Implement robust error handling for network and decode failures
- **Caching**: Cache decoded models locally to avoid re-downloading
- **Verification**: Validate model integrity after decoding (checksum)
- **Fallback**: Have fallback models bundled in app for offline scenarios
- **PNG Parser**: Consider using proper PNG parsing libraries for production

## 7. Testing

```bash
# Test the full pipeline
python model_encoder.py encode test_model.tflite bin/test.png --method chunk
python model_encoder.py decode bin/test.png bin/decoded_test.tflite --method chunk

# Verify integrity
shasum test_model.tflite bin/decoded_test.tflite
# Should show identical checksums
```

This approach successfully bypasses CDN image-only restrictions while maintaining model functionality on mobile clients.