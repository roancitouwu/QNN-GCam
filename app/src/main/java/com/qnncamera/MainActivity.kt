package com.qnncamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraExtensionCharacteristics
import android.os.Build
import android.os.Bundle
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
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
    private var imageAnalyzer: ImageAnalysis? = null
    private var depthBlurEnabled = false
    private var statusText: TextView? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var depthOverlay: ImageView? = null
    private var lastDepthTime = 0L
    
    // RenderScript for GPU blur
    private var renderScript: RenderScript? = null
    private var blurScript: ScriptIntrinsicBlur? = null
    
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
        
        // Bokeh overlay (replaces preview when active)
        depthOverlay = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 1.0f
            visibility = View.GONE
        }
        rootLayout.addView(depthOverlay, FrameLayout.LayoutParams(
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
                depthOverlay?.visibility = if (depthBlurEnabled) View.VISIBLE else View.GONE
                statusText?.text = if (depthBlurEnabled) "MiDaS depth active - processing..." else "Normal mode"
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
        
        // Initialize RenderScript for GPU blur
        renderScript = RenderScript.create(this)
        blurScript = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
        
        // Setup QNN environment and load model
        setupQnnEnvironment()
        loadModel()
        
        // Check Camera2 Extensions support
        checkCameraExtensions()
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }
    
    private fun checkCameraExtensions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
                val cameraIds = cameraManager.cameraIdList
                
                val sb = StringBuilder("Camera Extensions:\n")
                for (cameraId in cameraIds) {
                    val chars = cameraManager.getCameraCharacteristics(cameraId)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    val facingStr = if (facing == CameraCharacteristics.LENS_FACING_BACK) "Back" else "Front"
                    
                    val extChars = cameraManager.getCameraExtensionCharacteristics(cameraId)
                    val extensions = extChars.supportedExtensions
                    
                    sb.append("Camera $cameraId ($facingStr): ")
                    if (extensions.isEmpty()) {
                        sb.append("No extensions\n")
                    } else {
                        val extNames = extensions.map { ext ->
                            when (ext) {
                                CameraExtensionCharacteristics.EXTENSION_AUTOMATIC -> "AUTO"
                                CameraExtensionCharacteristics.EXTENSION_BOKEH -> "BOKEH"
                                CameraExtensionCharacteristics.EXTENSION_FACE_RETOUCH -> "FACE"
                                CameraExtensionCharacteristics.EXTENSION_HDR -> "HDR"
                                CameraExtensionCharacteristics.EXTENSION_NIGHT -> "NIGHT"
                                else -> "EXT_$ext"
                            }
                        }
                        sb.append(extNames.joinToString(", ")).append("\n")
                    }
                }
                Log.i(TAG, sb.toString())
                runOnUiThread { 
                    if (sb.contains("BOKEH")) {
                        statusText?.text = "Hardware BOKEH available!"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check camera extensions", e)
            }
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
        
        // Output shape is [1, 256, 256, 1]
        val outputBuffer = Array(1) { Array(MODEL_INPUT_SIZE) { Array(MODEL_INPUT_SIZE) { FloatArray(1) } } }
        tfliteInterpreter?.run(inputBuffer, outputBuffer)
        
        // Flatten output
        val depth = FloatArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        for (y in 0 until MODEL_INPUT_SIZE) {
            for (x in 0 until MODEL_INPUT_SIZE) {
                depth[y * MODEL_INPUT_SIZE + x] = outputBuffer[0][y][x][0]
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
            
            // Real-time depth analysis - lower resolution for speed
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(320, 240))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (depthBlurEnabled && System.currentTimeMillis() - lastDepthTime > 50) {
                            processFrameForDepth(imageProxy)
                            lastDepthTime = System.currentTimeMillis()
                        }
                        imageProxy.close()
                    }
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer)
                Log.i(TAG, "Camera started successfully with depth analysis")
                statusText?.text = statusText?.text.toString().replace("Initializing...", "Ready")
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                statusText?.text = "Camera error"
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    @androidx.camera.core.ExperimentalGetImage
    private fun processFrameForDepth(imageProxy: ImageProxy) {
        var bitmap = imageProxyToBitmap(imageProxy) ?: return
        
        // Fix orientation based on camera rotation
        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        
        try {
            val startTime = System.currentTimeMillis()
            val depthMap = runDepthInference(bitmap)
            val inferenceTime = System.currentTimeMillis() - startTime
            
            // Apply real bokeh blur based on depth
            val bokehBitmap = applyRealTimeBokeh(bitmap, depthMap)
            
            runOnUiThread {
                depthOverlay?.setImageBitmap(bokehBitmap)
                statusText?.text = "Bokeh: ${inferenceTime}ms"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame depth processing failed", e)
        }
    }
    
    private fun applyRealTimeBokeh(original: Bitmap, depthMap: FloatArray): Bitmap {
        val rs = renderScript ?: return original
        val blur = blurScript ?: return original
        
        val width = original.width
        val height = original.height
        
        // Create blurred version using RenderScript (GPU accelerated)
        val blurredBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val inputAlloc = Allocation.createFromBitmap(rs, blurredBitmap)
        val outputAlloc = Allocation.createFromBitmap(rs, blurredBitmap)
        
        blur.setRadius(25f) // Max blur
        blur.setInput(inputAlloc)
        blur.forEach(outputAlloc)
        outputAlloc.copyTo(blurredBitmap)
        
        inputAlloc.destroy()
        outputAlloc.destroy()
        
        // Normalize depth map
        val minDepth = depthMap.minOrNull() ?: 0f
        val maxDepth = depthMap.maxOrNull() ?: 1f
        val range = if (maxDepth - minDepth > 0.001f) maxDepth - minDepth else 1f
        
        // Use int arrays for faster pixel manipulation
        val origPixels = IntArray(width * height)
        val blurPixels = IntArray(width * height)
        val resultPixels = IntArray(width * height)
        
        original.getPixels(origPixels, 0, width, 0, 0, width, height)
        blurredBitmap.getPixels(blurPixels, 0, width, 0, 0, width, height)
        
        // Composite based on depth
        for (i in origPixels.indices) {
            val x = i % width
            val y = i / width
            val dx = (x * MODEL_INPUT_SIZE / width).coerceIn(0, MODEL_INPUT_SIZE - 1)
            val dy = (y * MODEL_INPUT_SIZE / height).coerceIn(0, MODEL_INPUT_SIZE - 1)
            val depthVal = (depthMap[dy * MODEL_INPUT_SIZE + dx] - minDepth) / range
            
            // High depth = near (sharp), low depth = far (blur)
            val blendFactor = (1f - depthVal).coerceIn(0f, 1f)
            
            val origPx = origPixels[i]
            val blurPx = blurPixels[i]
            
            val r = ((Color.red(origPx) * (1 - blendFactor) + Color.red(blurPx) * blendFactor)).toInt()
            val g = ((Color.green(origPx) * (1 - blendFactor) + Color.green(blurPx) * blendFactor)).toInt()
            val b = ((Color.blue(origPx) * (1 - blendFactor) + Color.blue(blurPx) * blendFactor)).toInt()
            
            resultPixels[i] = Color.rgb(r, g, b)
        }
        
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        
        return result
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
        
        // NV21 format: Y plane followed by interleaved VU
        val nv21 = ByteArray(width * height * 3 / 2)
        
        // Copy Y plane with correct stride handling
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
        
        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    
    private fun createDepthVisualization(depthMap: FloatArray): Bitmap {
        val bitmap = Bitmap.createBitmap(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, Bitmap.Config.ARGB_8888)
        
        // Normalize depth
        val minDepth = depthMap.minOrNull() ?: 0f
        val maxDepth = depthMap.maxOrNull() ?: 1f
        val range = if (maxDepth - minDepth > 0) maxDepth - minDepth else 1f
        
        for (y in 0 until MODEL_INPUT_SIZE) {
            for (x in 0 until MODEL_INPUT_SIZE) {
                val depth = (depthMap[y * MODEL_INPUT_SIZE + x] - minDepth) / range
                // Color: near=red, far=blue
                val r = (depth * 255).toInt().coerceIn(0, 255)
                val b = ((1 - depth) * 255).toInt().coerceIn(0, 255)
                bitmap.setPixel(x, y, Color.argb(200, r, 0, b))
            }
        }
        return bitmap
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
        blurScript?.destroy()
        renderScript?.destroy()
        cameraExecutor.shutdown()
    }
}
