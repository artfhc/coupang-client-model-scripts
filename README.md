# ML Model Image Encoder

A Python tool to encode machine learning models into PNG images using two different methods: PNG metadata chunks or pixel-based steganography.

## Installation

```bash
# Create virtual environment (recommended)
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt
```

## Usage

### Basic Syntax

```bash
python model_encoder.py <action> <input> <output> [--method <chunk|pixel>]
```

### Encoding Examples

```bash
# Encode using chunk method (recommended - smaller files)
python model_encoder.py encode model_coreml.mlmodel bin/encoded_model_chunk.png --method chunk

# Encode using pixel method (steganographic - harder to detect)
python model_encoder.py encode odt-original.tflite bin/encoded_model_pixel.png --method pixel

# Default method is chunk if not specified
python model_encoder.py encode model_coreml.mlmodel bin/encoded_model.png
```

### Decoding Examples

```bash
# Decode from chunk method
python model_encoder.py decode bin/encoded_model_chunk.png bin/decoded_model.mlmodel --method chunk

# Decode from pixel method
python model_encoder.py decode bin/encoded_model_pixel.png bin/decoded_model.tflite --method pixel

# Verify the decoded file matches original
shasum model_coreml.mlmodel bin/decoded_model.mlmodel
```

## Encoding Methods

### Chunk Method (`--method chunk`)
- **How it works**: Stores compressed model data in PNG metadata chunks
- **Pros**: More efficient, smaller file sizes, faster encoding/decoding
- **Cons**: Easily detectable with metadata viewers
- **Best for**: File sharing, storage optimization, when detection isn't a concern
- **File size**: ~40-50% of original compressed size

### Pixel Method (`--method pixel`)
- **How it works**: Encodes compressed model data directly into RGB pixel values
- **Pros**: Steganographic (looks like a regular image), harder to detect
- **Cons**: Larger file sizes, slower processing
- **Best for**: Hiding model data, bypassing content filters
- **File size**: Similar to original model size

## Example Output

```bash
$ python model_encoder.py encode model_coreml.mlmodel bin/encoded_chunk.png --method chunk
Model encoded in PNG chunk: bin/encoded_chunk.png
Original size: 7,049,630 bytes
Compressed size: 2,258,959 bytes
Compression ratio: 3.12x

$ python model_encoder.py decode bin/encoded_chunk.png bin/decoded.mlmodel --method chunk
Original filename: model_coreml.mlmodel
Original size: 7,049,630 bytes
Compressed size: 2,258,959 bytes
Model decoded from PNG chunk: bin/decoded.mlmodel
```

## File Structure

```
coupang-client-model-scripts/
├── model_encoder.py          # Main encoding/decoding script
├── requirements.txt          # Python dependencies
├── model_coreml.mlmodel     # Original CoreML model
├── odt-original.tflite      # Original TensorFlow Lite model
├── bin/                     # Output directory for encoded/decoded files
│   ├── encoded_model_chunk.png
│   ├── encoded_model_pixel.png
│   └── decoded_model.mlmodel
└── README.md               # This file
```

## Features

- **Compression**: Automatic zlib compression (typically 2-4x reduction)
- **Metadata preservation**: Stores original filename and size information
- **Data integrity**: Verifies decoded data matches original
- **Directory creation**: Automatically creates output directories
- **Error handling**: Comprehensive validation and error reporting

## Technical Details

- **Compression**: Uses zlib level 9 compression before encoding
- **Chunk storage**: Uses PNG tEXt chunks with base64 encoding
- **Pixel encoding**: RGB channels store base64 data with header information
- **Image dimensions**: Automatically calculated based on data size for pixel method
- **Supported formats**: Any binary file (optimized for ML models)

## Troubleshooting

**ImportError for base64/zlib**: These are built-in Python modules, not pip packages. Only install Pillow and numpy.

**File not found errors**: Ensure input files exist and output directories are writable.

**Decode size mismatch**: File may be corrupted or wrong encoding method specified.

**Large pixel images**: Normal for pixel method - image size depends on model size.