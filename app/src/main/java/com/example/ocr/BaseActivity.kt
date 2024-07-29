package com.example.ocr
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
// Open class allowing extension, so activities like com.example.ocr.com.example.ocr.com.example.ocr.MainActivity can inherit from it
// for common camera permission handling functionality
open class BaseActivity : ComponentActivity() {

    // Key Point 1: Camera Permission Request Launcher
    // Declare a launcher for the camera permission request, handling the permission result
    private val cameraPermissionRequestLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission granted: proceed with opening the camera
                startDefaultCamera()
            } else {
                // Permission denied: inform the user to enable it through settings
                Toast.makeText(
                    this,
                    "Go to settings and enable camera permission to use this feature",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    // Key Point 2: Camera Intent Launcher
    // Declare a launcher for taking a picture, handling the result of the camera app
    private val takePictureLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // This can be expanded to handle the result data
            Toast.makeText(this, "Photo taken", Toast.LENGTH_SHORT).show()
        }

    // Checks camera permission and either starts the camera directly or requests permission
    fun handleCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted: start the camera
                startDefaultCamera()
            }

            else -> {
                // Permission is not granted: request it
                cameraPermissionRequestLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // Starts the default camera app for taking a picture
    private fun startDefaultCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Camera app is available: launch it
                takePictureLauncher.launch(takePictureIntent)
            } ?: run {
                // No camera app available: inform the user
                Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openGallery(view: View) {}
}