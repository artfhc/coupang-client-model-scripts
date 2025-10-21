#!/usr/bin/env python3
"""
ML Model Image Encoder/Decoder

Encodes machine learning models into PNG images using two methods:
1. PNG ancillary chunks (tEXt/zTXt chunks) - more efficient
2. Base64 encoded in pixel data - more robust but heavier

Usage:
    python model_encoder.py encode <model_file> <output.png> [--method chunk|pixel]
    python model_encoder.py decode <input.png> <output_model> [--method chunk|pixel]
"""

import argparse
import base64
import io
import math
import os
import struct
import sys
import zlib
from typing import Tuple, Optional

import numpy as np
from PIL import Image, PngImagePlugin


class ModelImageEncoder:

    @staticmethod
    def encode_to_chunk(model_path: str, output_png: str, chunk_name: str = "mOdL") -> None:
        """Encode model file into PNG ancillary chunk."""
        with open(model_path, 'rb') as f:
            model_data = f.read()

        # Compress the model data
        compressed_data = zlib.compress(model_data, level=9)

        # Create a minimal PNG image (1x1 pixel)
        img = Image.new('RGB', (1, 1), color='white')

        # Create PNG info object to store metadata
        pnginfo = PngImagePlugin.PngInfo()

        # Encode compressed data as base64 and store in text chunk
        encoded_data = base64.b64encode(compressed_data).decode('ascii')
        pnginfo.add_text(chunk_name, encoded_data)

        # Add metadata about original file
        pnginfo.add_text("mOdL_name", os.path.basename(model_path))
        pnginfo.add_text("mOdL_size", str(len(model_data)))
        pnginfo.add_text("mOdL_compressed_size", str(len(compressed_data)))

        # Ensure output directory exists
        os.makedirs(os.path.dirname(output_png) if os.path.dirname(output_png) else '.', exist_ok=True)

        # Save PNG with embedded model
        img.save(output_png, "PNG", pnginfo=pnginfo)

        print(f"Model encoded in PNG chunk: {output_png}")
        print(f"Original size: {len(model_data):,} bytes")
        print(f"Compressed size: {len(compressed_data):,} bytes")
        print(f"Compression ratio: {len(model_data)/len(compressed_data):.2f}x")

    @staticmethod
    def decode_from_chunk(png_path: str, output_path: str, chunk_name: str = "mOdL") -> None:
        """Decode model file from PNG ancillary chunk."""
        img = Image.open(png_path)

        if chunk_name not in img.text:
            raise ValueError(f"No model data found in chunk '{chunk_name}'")

        # Decode the base64 data
        encoded_data = img.text[chunk_name]
        compressed_data = base64.b64decode(encoded_data.encode('ascii'))

        # Decompress the model data
        model_data = zlib.decompress(compressed_data)

        # Write the model file
        with open(output_path, 'wb') as f:
            f.write(model_data)

        # Print metadata if available
        if "mOdL_name" in img.text:
            print(f"Original filename: {img.text['mOdL_name']}")
        if "mOdL_size" in img.text:
            print(f"Original size: {int(img.text['mOdL_size']):,} bytes")
        if "mOdL_compressed_size" in img.text:
            print(f"Compressed size: {int(img.text['mOdL_compressed_size']):,} bytes")

        print(f"Model decoded from PNG chunk: {output_path}")

    @staticmethod
    def encode_to_pixels(model_path: str, output_png: str) -> None:
        """Encode model file into PNG pixel data using Base64."""
        with open(model_path, 'rb') as f:
            model_data = f.read()

        # Compress the model data
        compressed_data = zlib.compress(model_data, level=9)

        # Encode as base64
        b64_data = base64.b64encode(compressed_data)

        # Add header with original file info
        header = f"MODEL:{os.path.basename(model_path)}:{len(model_data)}:{len(compressed_data)}:"
        header_bytes = header.encode('utf-8')

        # Combine header and data
        full_data = header_bytes + b64_data

        # Calculate image dimensions (3 bytes per pixel for RGB)
        bytes_per_pixel = 3
        total_pixels = math.ceil(len(full_data) / bytes_per_pixel)

        # Calculate square-ish dimensions
        width = math.ceil(math.sqrt(total_pixels))
        height = math.ceil(total_pixels / width)

        # Pad data to fill image
        padded_size = width * height * bytes_per_pixel
        padded_data = full_data + b'\x00' * (padded_size - len(full_data))

        # Reshape into image array
        img_array = np.frombuffer(padded_data, dtype=np.uint8)
        img_array = img_array.reshape((height, width, bytes_per_pixel))

        # Ensure output directory exists
        os.makedirs(os.path.dirname(output_png) if os.path.dirname(output_png) else '.', exist_ok=True)

        # Create and save image
        img = Image.fromarray(img_array, 'RGB')
        img.save(output_png, "PNG")

        print(f"Model encoded in PNG pixels: {output_png}")
        print(f"Original size: {len(model_data):,} bytes")
        print(f"Compressed size: {len(compressed_data):,} bytes")
        print(f"Image size: {width}x{height} pixels")
        print(f"PNG file size: {os.path.getsize(output_png):,} bytes")

    @staticmethod
    def decode_from_pixels(png_path: str, output_path: str) -> None:
        """Decode model file from PNG pixel data."""
        img = Image.open(png_path)
        img_array = np.array(img)

        # Flatten image data
        flat_data = img_array.flatten().tobytes()

        # Find header end
        header_end = flat_data.find(b':')
        if header_end == -1:
            raise ValueError("Invalid encoded image format")

        # Parse header
        header_parts = flat_data[:header_end + 1].split(b':')
        if len(header_parts) < 4 or header_parts[0] != b'MODEL':
            raise ValueError("Invalid header format")

        original_name = header_parts[1].decode('utf-8')
        original_size = int(header_parts[2])
        compressed_size = int(header_parts[3])

        # Find start of base64 data (after full header)
        full_header_end = flat_data.find(b':', header_end + 1)
        full_header_end = flat_data.find(b':', full_header_end + 1)
        full_header_end = flat_data.find(b':', full_header_end + 1) + 1

        # Extract base64 data
        b64_data = flat_data[full_header_end:full_header_end + math.ceil(compressed_size * 4/3)]

        # Remove any null padding from base64 data
        b64_data = b64_data.rstrip(b'\x00')

        # Decode base64 and decompress
        compressed_data = base64.b64decode(b64_data)
        model_data = zlib.decompress(compressed_data)

        # Verify size
        if len(model_data) != original_size:
            raise ValueError(f"Size mismatch: expected {original_size}, got {len(model_data)}")

        # Write the model file
        with open(output_path, 'wb') as f:
            f.write(model_data)

        print(f"Original filename: {original_name}")
        print(f"Original size: {original_size:,} bytes")
        print(f"Compressed size: {compressed_size:,} bytes")
        print(f"Model decoded from PNG pixels: {output_path}")


def main():
    parser = argparse.ArgumentParser(description="Encode/decode ML models in PNG images")
    parser.add_argument("action", choices=["encode", "decode"], help="Action to perform")
    parser.add_argument("input", help="Input file path")
    parser.add_argument("output", help="Output file path")
    parser.add_argument("--method", choices=["chunk", "pixel"], default="chunk",
                       help="Encoding method (default: chunk)")

    args = parser.parse_args()

    encoder = ModelImageEncoder()

    try:
        if args.action == "encode":
            if not os.path.exists(args.input):
                print(f"Error: Input file '{args.input}' not found")
                sys.exit(1)

            if args.method == "chunk":
                encoder.encode_to_chunk(args.input, args.output)
            else:  # pixel
                encoder.encode_to_pixels(args.input, args.output)

        else:  # decode
            if not os.path.exists(args.input):
                print(f"Error: Input file '{args.input}' not found")
                sys.exit(1)

            if args.method == "chunk":
                encoder.decode_from_chunk(args.input, args.output)
            else:  # pixel
                encoder.decode_from_pixels(args.input, args.output)

    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()