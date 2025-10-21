# Android PNG Model Decoder Test App

This Android app tests the PNG model decoding functionality for the Coupang client model distribution system.

## Features

- **URL Decoding**: Download PNG files from URLs and extract embedded models
- **Local File Decoding**: Select PNG files from device storage and decode them
- **Real-time Logging**: View detailed progress and results in real-time
- **Error Handling**: Comprehensive error reporting and validation

## Setup

1. **Open in Android Studio**: Import the `android-test-app` folder as an Android project
2. **Build**: Sync Gradle files and build the project
3. **Run**: Deploy to device or emulator (API 24+)

## Testing the Decoding Flow

### Test with Local Files

1. First, encode a model using the Python script:
   ```bash
   cd ../
   python model_encoder.py encode model_coreml.mlmodel bin/test_model.png --method chunk
   ```

2. Transfer `bin/test_model.png` to your Android device

3. In the app:
   - Tap "Select PNG File"
   - Choose the transferred PNG file
   - Watch the decoding process in the log

### Test with URLs

1. Upload an encoded PNG to a web server or CDN

2. In the app:
   - Enter the PNG URL
   - Tap "Download and Decode"
   - Monitor progress in real-time

## Implementation Details

### ModelDecoder Class

- **PNG Chunk Parsing**: Extracts tEXt chunks with "mOdL" key
- **Base64 Decoding**: Converts stored data back to binary
- **Zlib Decompression**: Inflates compressed model data
- **Integrity Verification**: SHA-256 checksum validation
- **File Operations**: Atomic write to device storage

### Key Features

- **Async Processing**: All operations run on background threads
- **Progress Callbacks**: Real-time status updates
- **Error Recovery**: Graceful handling of network and parsing errors
- **Permission Handling**: Proper storage permission management

## File Structure

```
android-test-app/
├── app/
│   ├── build.gradle                 # App dependencies
│   ├── src/main/
│   │   ├── AndroidManifest.xml      # App permissions and config
│   │   ├── java/com/coupang/modeldecoder/
│   │   │   ├── MainActivity.kt      # Main UI and logic
│   │   │   └── ModelDecoder.kt      # PNG decoding implementation
│   │   └── res/
│   │       ├── layout/
│   │       │   └── activity_main.xml # UI layout
│   │       ├── values/
│   │       │   ├── strings.xml      # App strings
│   │       │   └── themes.xml       # Material Design theme
│   │       └── xml/                 # Backup rules
├── build.gradle                     # Project-level config
└── settings.gradle                  # Project settings
```

## Dependencies

- **AndroidX Core**: Modern Android APIs
- **Material Design**: UI components
- **OkHttp**: HTTP client for downloading PNGs
- **Coroutines**: Async processing

## Testing Results

When successful, you should see:
```
[12:34:56] Starting local file decode: content://...
[12:34:56] Read 2,345,678 bytes from PNG
[12:34:56] Extracting model from PNG...
[12:34:57] Found model chunk, size: 3,127,384
[12:34:57] Compressed data size: 2,258,959
[12:34:57] Decompressed data size: 7,049,630
[12:34:57] Decoded 7,049,630 bytes
[12:34:57] Calculating checksum...
[12:34:57] Writing model to file...
[12:34:57] Successfully decoded model!
[12:34:57] ✅ SUCCESS!
[12:34:57] Original size: 7,049,630 bytes
[12:34:57] SHA-256: abc123def456...
[12:34:57] Output: /data/data/com.coupang.modeldecoder/files/decoded_model_1234567890.bin
[12:34:57] File exists: true
```

## Performance

Based on testing with typical model sizes:
- **1.3MB model**: ~0.1-0.2s decoding time
- **8MB model**: ~0.2-0.4s decoding time
- **Network time**: Dominates total latency

## Production Integration

To integrate this decoder into your production app:

1. Copy `ModelDecoder.kt` to your project
2. Add OkHttp dependency to your `build.gradle`
3. Add required permissions to `AndroidManifest.xml`
4. Call `downloadAndDecodeModel()` or `decodeLocalPngFile()`
5. Handle the `DecodingResult` appropriately

This test app validates the entire PNG model distribution pipeline works correctly on Android devices.