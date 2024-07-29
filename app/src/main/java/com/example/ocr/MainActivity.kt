package com.example.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var overlayView: OverlayView
    private lateinit var captureButton: Button
    private var cameraDevice: CameraDevice? = null
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private var cameraCaptureSession: CameraCaptureSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        overlayView = findViewById(R.id.overlay)
        captureButton = findViewById(R.id.captureButton)

        textureView.surfaceTextureListener = textureListener
        captureButton.setOnClickListener {
            captureImage()
        }
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val imageDimension = map?.getOutputSizes(SurfaceTexture::class.java)?.maxByOrNull { it.height * it.width }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
                return
            }
            manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }
    }

    private fun createCameraPreview() {
        try {
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(1920, 1080) // Usar una resolución más alta si es posible
            val surface = Surface(texture)
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)

            // Ajustes de autoenfoque y control de exposición
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    cameraCaptureSession = session
                    updatePreview()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun updatePreview() {
        if (cameraDevice == null) return
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        try {
            cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun captureImage() {
        if (cameraDevice == null) return
        try {
            val captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(Surface(textureView.surfaceTexture))

            val captureListener = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    cropImageAndPerformOCR()
                    restartPreview()
                }
            }

            cameraCaptureSession?.stopRepeating()
            cameraCaptureSession?.capture(captureBuilder.build(), captureListener, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun cropImageAndPerformOCR() {
        val bitmap = textureView.bitmap
        val rect = overlayView.rect
        val croppedBitmap =
            bitmap?.let { Bitmap.createBitmap(it, rect.left, rect.top, rect.width(), rect.height()) }
        val preprocessedBitmap = croppedBitmap?.let { preprocessImage(it) }

        // Realizar OCR en el bitmap preprocesado
        val image = preprocessedBitmap?.let { InputImage.fromBitmap(it, 0) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        if (image != null) {
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Manejar el texto reconocido
                    val recognizedText = visionText.text
                    Log.d("OCR", recognizedText)
                    Toast.makeText(this, recognizedText, Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { e ->
                    // Manejar error
                    Toast.makeText(this, "Error al realizar OCR: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (i in 0 until width) {
            for (j in 0 until height) {
                val pixel = bitmap.getPixel(i, j)
                val r = (pixel shr 16 and 0xFF)
                val g = (pixel shr 8 and 0xFF)
                val b = (pixel and 0xFF)
                val gray = (0.2989 * r + 0.5870 * g + 0.1140 * b).toInt()
                grayscaleBitmap.setPixel(i, j, Color.rgb(gray, gray, gray))
            }
        }

        return binarizeImage(grayscaleBitmap)
    }

    private fun binarizeImage(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val binarizedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (i in 0 until width) {
            for (j in 0 until height) {
                val pixel = bitmap.getPixel(i, j)
                val gray = (pixel shr 16 and 0xFF)
                val binarized = if (gray > 128) 255 else 0
                binarizedBitmap.setPixel(i, j, Color.rgb(binarized, binarized, binarized))
            }
        }

        return binarizedBitmap
    }

    private fun restartPreview() {
        try {
            captureRequestBuilder[CaptureRequest.CONTROL_AF_TRIGGER] = CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
            cameraCaptureSession?.capture(captureRequestBuilder.build(), null, null)
            updatePreview()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
}
