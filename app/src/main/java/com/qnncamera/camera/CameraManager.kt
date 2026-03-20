package com.qnncamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Camera management module handling preview, capture, and frame analysis
 */
class CameraManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CameraManager"
        private const val ANALYSIS_WIDTH = 320
        private const val ANALYSIS_HEIGHT = 240
    }
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    interface FrameCallback {
        fun onFrame(bitmap: Bitmap, rotationDegrees: Int)
    }
    
    interface CaptureCallback {
        fun onCaptured(file: File)
        fun onError(error: String)
    }
    
    /**
     * Start camera with preview and optional frame analysis
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        frameCallback: FrameCallback?,
        frameIntervalMs: Long = 50
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            
            // Preview
            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            // Image capture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            
            // Frame analysis
            if (frameCallback != null) {
                var lastFrameTime = 0L
                
                imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastFrameTime >= frameIntervalMs) {
                                processFrame(imageProxy, frameCallback)
                                lastFrameTime = currentTime
                            }
                            imageProxy.close()
                        }
                    }
            }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider?.unbindAll()
                
                val useCases = mutableListOf<UseCase>(preview!!, imageCapture!!)
                imageAnalysis?.let { useCases.add(it) }
                
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    *useCases.toTypedArray()
                )
                
                Log.i(TAG, "Camera started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    @androidx.camera.core.ExperimentalGetImage
    private fun processFrame(imageProxy: ImageProxy, callback: FrameCallback) {
        val bitmap = imageProxyToBitmap(imageProxy) ?: return
        
        // Apply rotation correction
        val rotatedBitmap = if (imageProxy.imageInfo.rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
        
        callback.onFrame(rotatedBitmap, imageProxy.imageInfo.rotationDegrees)
    }
    
    @androidx.camera.core.ExperimentalGetImage
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        
        val width = image.width
        val height = image.height
        
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        
        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride
        
        val nv21 = ByteArray(width * height * 3 / 2)
        
        // Copy Y plane
        var pos = 0
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(nv21, pos, width)
            pos += width
        }
        
        // Copy UV planes (interleaved as VU for NV21)
        val uvHeight = height / 2
        val uvWidth = width / 2
        
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val vIndex = row * uvRowStride + col * uvPixelStride
                val uIndex = row * uvRowStride + col * uvPixelStride
                
                vBuffer.position(vIndex)
                nv21[pos++] = vBuffer.get()
                
                uBuffer.position(uIndex)
                nv21[pos++] = uBuffer.get()
            }
        }
        
        val yuvImage = android.graphics.YuvImage(
            nv21, android.graphics.ImageFormat.NV21, width, height, null
        )
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    
    /**
     * Capture a photo
     */
    fun capturePhoto(callback: CaptureCallback) {
        val capture = imageCapture ?: run {
            callback.onError("Camera not ready")
            return
        }
        
        val photoFile = File(
            context.cacheDir,
            "QNN_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        )
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    callback.onCaptured(photoFile)
                }
                
                override fun onError(exception: ImageCaptureException) {
                    callback.onError("Capture failed: ${exception.message}")
                }
            }
        )
    }
    
    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
