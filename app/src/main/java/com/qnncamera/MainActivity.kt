package com.qnncamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "QNN_CAMERA"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val MODEL_INPUT_SIZE = 256
    }
    
    private lateinit var cameraExecutor: ExecutorService
    private var previewView: PreviewView? = null
    private var tfliteInterpreter: Interpreter? = null
    private var imageCapture: ImageCapture? = null
    private var depthBlurEnabled = false
    private var statusText: TextView? = null
    private var nnApiDelegate: NnApiDelegate? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create UI programmatically
        val rootLayout = FrameLayout(this)
        rootLayout.setBackgroundColor(Color.BLACK)
        
        // Camera preview
        previewView = PreviewView(this)
        rootLayout.addView(previewView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // Bottom controls container
        val controlsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(128, 0, 0, 0))
            setPadding(16, 24, 16, 24)
        }
        
        // Depth blur toggle button
        val depthToggle = Button(this).apply {
            text = "Depth: OFF"
            setOnClickListener {
                depthBlurEnabled = !depthBlurEnabled
                text = if (depthBlurEnabled) "Depth: ON" else "Depth: OFF"
                statusText?.text = if (depthBlurEnabled) "MiDaS depth active" else "Normal mode"
            }
        }
        controlsLayout.addView(depthToggle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        
        // Capture button
        val captureBtn = Button(this).apply {
            text = "📷 Capture"
            setOnClickListener { takePhoto() }
        }
        controlsLayout.addView(captureBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        
        val controlsParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }
        rootLayout.addView(controlsLayout, controlsParams)
        
        // Status text at top
        statusText = TextView(this).apply {
            text = "QNN Camera - Initializing..."
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(128, 0, 0, 0))
            setPadding(16, 8, 16, 8)
            textSize = 14f
        }
        val statusParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP }
        rootLayout.addView(statusText, statusParams)
        
        setContentView(rootLayout)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Setup QNN environment and load model
        setupQnnEnvironment()
        loadModel()
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }
    
    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile("midas.tflite")
            
            // Use NNAPI delegate for Hexagon DSP acceleration
            try {
                nnApiDelegate = NnApiDelegate()
                val options = Interpreter.Options().addDelegate(nnApiDelegate)
                tfliteInterpreter = Interpreter(modelBuffer, options)
                Log.i(TAG, "MiDaS model loaded with NNAPI (Hexagon DSP)")
                runOnUiThread { statusText?.text = "MiDaS ready (NNAPI/DSP)" }
            } catch (e: Exception) {
                Log.w(TAG, "NNAPI delegate failed, using CPU", e)
                tfliteInterpreter = Interpreter(modelBuffer)
                runOnUiThread { statusText?.text = "MiDaS ready (CPU)" }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MiDaS model", e)
            runOnUiThread { statusText?.text = "Model load failed: ${e.message}" }
        }
    }
    
    private fun loadModelFile(filename: String): MappedByteBuffer {
        val fd = assets.openFd(filename)
        val input = FileInputStream(fd.fileDescriptor)
        val channel = input.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }
    
    private fun setupQnnEnvironment() {
        val nativeLibDir = applicationInfo.nativeLibraryDir
        try {
            android.system.Os.setenv("ADSP_LIBRARY_PATH", 
                "$nativeLibDir:/system/lib64:/vendor/lib64/dsp", true)
            Log.i(TAG, "QNN environment configured: $nativeLibDir")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set QNN env vars", e)
        }
    }
    
    private fun takePhoto() {
        val imageCapture = imageCapture ?: run {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Use app's cache directory (always writable)
        val photoFile = File(
            cacheDir,
            "QNN_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        )
        
        Log.i(TAG, "Saving photo to: ${photoFile.absolutePath}")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (depthBlurEnabled) {
                        processWithDepthBlur(photoFile)
                    } else {
                        Toast.makeText(this@MainActivity, "Photo saved: ${photoFile.name}", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exc)
                    Toast.makeText(this@MainActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    private fun processWithDepthBlur(photoFile: File) {
        cameraExecutor.execute {
            try {
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                val depthMap = runDepthInference(bitmap)
                val blurred = applyDepthBlur(bitmap, depthMap)
                
                // Save blurred image
                val blurFile = File(photoFile.parent, photoFile.nameWithoutExtension + "_blur.jpg")
                FileOutputStream(blurFile).use { out ->
                    blurred.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                
                runOnUiThread {
                    Toast.makeText(this, "Depth blur applied: ${blurFile.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Depth processing failed", e)
                runOnUiThread {
                    Toast.makeText(this, "Depth processing failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun runDepthInference(bitmap: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
        val inputBuffer = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        // Normalize to [0,1] and fill buffer
        for (y in 0 until MODEL_INPUT_SIZE) {
            for (x in 0 until MODEL_INPUT_SIZE) {
                val pixel = scaled.getPixel(x, y)
                inputBuffer.putFloat(Color.red(pixel) / 255f)
                inputBuffer.putFloat(Color.green(pixel) / 255f)
                inputBuffer.putFloat(Color.blue(pixel) / 255f)
            }
        }
        
        val outputBuffer = Array(1) { Array(MODEL_INPUT_SIZE) { FloatArray(MODEL_INPUT_SIZE) } }
        tfliteInterpreter?.run(inputBuffer, outputBuffer)
        
        // Flatten output
        val depth = FloatArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        for (y in 0 until MODEL_INPUT_SIZE) {
            for (x in 0 until MODEL_INPUT_SIZE) {
                depth[y * MODEL_INPUT_SIZE + x] = outputBuffer[0][y][x]
            }
        }
        return depth
    }
    
    private fun applyDepthBlur(original: Bitmap, depthMap: FloatArray): Bitmap {
        val width = original.width
        val height = original.height
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        
        // Normalize depth map
        val minDepth = depthMap.minOrNull() ?: 0f
        val maxDepth = depthMap.maxOrNull() ?: 1f
        val range = maxDepth - minDepth
        
        // Simple blur based on depth - far objects get blurred
        val blurRadius = 15
        for (y in blurRadius until height - blurRadius) {
            for (x in blurRadius until width - blurRadius) {
                // Sample depth at this position (scaled)
                val dx = (x * MODEL_INPUT_SIZE / width).coerceIn(0, MODEL_INPUT_SIZE - 1)
                val dy = (y * MODEL_INPUT_SIZE / height).coerceIn(0, MODEL_INPUT_SIZE - 1)
                val depth = (depthMap[dy * MODEL_INPUT_SIZE + dx] - minDepth) / range
                
                // Blur far objects (low depth = far in MiDaS)
                if (depth < 0.4f) {
                    val blur = ((0.4f - depth) * blurRadius).toInt().coerceIn(1, blurRadius)
                    var r = 0; var g = 0; var b = 0; var count = 0
                    for (by in -blur..blur) {
                        for (bx in -blur..blur) {
                            val pixel = original.getPixel(x + bx, y + by)
                            r += Color.red(pixel)
                            g += Color.green(pixel)
                            b += Color.blue(pixel)
                            count++
                        }
                    }
                    result.setPixel(x, y, Color.rgb(r / count, g / count, b / count))
                }
            }
        }
        return result
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView?.surfaceProvider)
            }
            
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                Log.i(TAG, "Camera started successfully")
                statusText?.text = statusText?.text.toString().replace("Initializing...", "Ready")
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                statusText?.text = "Camera error"
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        tfliteInterpreter?.close()
        nnApiDelegate?.close()
        cameraExecutor.shutdown()
    }
}
