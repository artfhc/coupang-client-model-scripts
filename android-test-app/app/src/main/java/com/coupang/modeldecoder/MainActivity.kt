package com.coupang.modeldecoder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.coupang.modeldecoder.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val modelDecoder = ModelDecoder()

    // File picker for selecting PNG files
    private val pngFilePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { decodeLocalPngFile(it) }
    }

    // Permission launcher for storage access
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            selectPngFile()
        } else {
            showToast("Storage permission required to select files")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // URL decode button
        binding.btnDecodeUrl.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                if (hasStoragePermission()) {
                    decodeFromUrl(url)
                } else {
                    showToast("Storage permission required to save decoded files")
                    requestStoragePermission()
                }
            } else {
                showToast("Please enter a URL")
            }
        }

        // Local file decode button
        binding.btnDecodeLocal.setOnClickListener {
            checkPermissionAndSelectFile()
        }

        // Clear log button
        binding.btnClearLog.setOnClickListener {
            binding.tvLog.text = ""
        }

        // Pre-fill with example URL for testing
        binding.etUrl.setText(getString(R.string.model_png_url))
    }

    private fun checkPermissionAndSelectFile() {
        if (hasStoragePermission()) {
            selectPngFile()
        } else {
            requestStoragePermission()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+, request MANAGE_EXTERNAL_STORAGE
            showToast("Please grant storage access in settings")
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else {
            // For older versions, request WRITE_EXTERNAL_STORAGE
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun selectPngFile() {
        try {
            pngFilePicker.launch("image/png")
        } catch (e: Exception) {
            showToast("Error opening file picker: ${e.message}")
        }
    }

    private fun decodeFromUrl(url: String) {
        logMessage("Starting URL decode: $url")
        setButtonsEnabled(false)

        lifecycleScope.launch {
            try {
                val outputFile = getExternalOutputFile("decoded_model_${System.currentTimeMillis()}.bin")

                val result = modelDecoder.downloadAndDecodeModel(
                    url = url,
                    outputFile = outputFile
                ) { progress ->
                    logMessage(progress)
                }

                handleDecodingResult(result)

            } catch (e: Exception) {
                logMessage("Error: ${e.message}")
            } finally {
                setButtonsEnabled(true)
            }
        }
    }

    private fun decodeLocalPngFile(uri: Uri) {
        logMessage("Starting local file decode: $uri")
        setButtonsEnabled(false)

        lifecycleScope.launch {
            try {
                // Copy URI content to temporary file
                val tempPngFile = File(cacheDir, "temp_input.png")
                contentResolver.openInputStream(uri)?.use { input ->
                    tempPngFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val outputFile = getExternalOutputFile("decoded_model_${System.currentTimeMillis()}.bin")

                val result = modelDecoder.decodeLocalPngFile(
                    pngFile = tempPngFile,
                    outputFile = outputFile
                ) { progress ->
                    logMessage(progress)
                }

                handleDecodingResult(result)

                // Clean up temp file
                tempPngFile.delete()

            } catch (e: Exception) {
                logMessage("Error: ${e.message}")
            } finally {
                setButtonsEnabled(true)
            }
        }
    }

    private fun handleDecodingResult(result: DecodingResult) {
        when (result) {
            is DecodingResult.Success -> {
                logMessage("✅ SUCCESS!")
                logMessage("Original size: ${String.format("%,d", result.originalSize)} bytes")
                logMessage("SHA-256: ${result.checksum}")
                logMessage("Output: ${result.outputFile.absolutePath}")
                logMessage("File exists: ${result.outputFile.exists()}")

                showToast("Model decoded successfully!")
            }

            is DecodingResult.Error -> {
                logMessage("❌ FAILED: ${result.message}")
                showToast("Decoding failed: ${result.message}")
            }
        }
    }

    private fun logMessage(message: String) {
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val logLine = "[$timestamp] $message\n"

            binding.tvLog.append(logLine)

            // Auto-scroll to bottom
            binding.scrollView.post {
                binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        runOnUiThread {
            binding.btnDecodeUrl.isEnabled = enabled
            binding.btnDecodeLocal.isEnabled = enabled
            binding.etUrl.isEnabled = enabled
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Get external storage file for decoded models
     */
    private fun getExternalOutputFile(filename: String): File {
        // Use Downloads directory for easy ADB access
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val modelDecoderDir = File(downloadsDir, "ModelDecoder")

        // Create directory if it doesn't exist
        if (!modelDecoderDir.exists()) {
            modelDecoderDir.mkdirs()
        }

        val outputFile = File(modelDecoderDir, filename)
        logMessage("Output file path: ${outputFile.absolutePath}")

        return outputFile
    }
}