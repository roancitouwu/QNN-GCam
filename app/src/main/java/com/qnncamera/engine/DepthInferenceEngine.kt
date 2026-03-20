package com.qnncamera.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tflite.java.TfLite
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Depth inference engine using LiteRT in Google Play Services
 * This is the modern approach replacing deprecated NNAPI
 */
class DepthInferenceEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "DepthEngine"
        private const val MODEL_INPUT_SIZE = 256
    }
    
    private var interpreter: InterpreterApi? = null
    private var isInitialized = false
    private var acceleratorType = "Initializing"
    
    interface InitCallback {
        fun onInitialized(accelerator: String)
        fun onError(error: String)
    }
    
    interface InferenceCallback {
        fun onResult(depthMap: FloatArray, inferenceTimeMs: Long)
        fun onError(error: String)
    }
    
    /**
     * Initialize TFLite with Play Services runtime
     * This provides automatic updates and better hardware support
     */
    fun initialize(callback: InitCallback) {
        // Initialize TFLite in Play Services
        TfLite.initialize(context).addOnSuccessListener {
            Log.i(TAG, "TFLite Play Services initialized")
            loadModel(callback)
        }.addOnFailureListener { e ->
            Log.e(TAG, "TFLite Play Services init failed: ${e.message}")
            // Fallback to bundled TFLite
            loadModelBundled(callback)
        }
    }
    
    private fun loadModel(callback: InitCallback) {
        try {
            val modelBuffer = loadModelFile("midas.tflite")
            
            // Try GPU delegate first
            val options = InterpreterApi.Options()
                .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
            
            try {
                val gpuOptions = GpuDelegateFactory.Options()
                options.addDelegateFactory(GpuDelegateFactory(gpuOptions))
                interpreter = InterpreterApi.create(modelBuffer, options)
                acceleratorType = "GPU (Play Services)"
                isInitialized = true
                Log.i(TAG, "Model loaded with GPU delegate")
                callback.onInitialized(acceleratorType)
            } catch (e: Exception) {
                Log.w(TAG, "GPU delegate failed: ${e.message}")
                // Fallback to CPU
                options.setNumThreads(4)
                interpreter = InterpreterApi.create(modelBuffer, options)
                acceleratorType = "CPU (Play Services)"
                isInitialized = true
                Log.i(TAG, "Model loaded with CPU")
                callback.onInitialized(acceleratorType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed: ${e.message}")
            callback.onError("Model load failed: ${e.message}")
        }
    }
    
    private fun loadModelBundled(callback: InitCallback) {
        try {
            val modelBuffer = loadModelFile("midas.tflite")
            val options = InterpreterApi.Options()
                .setRuntime(TfLiteRuntime.FROM_APPLICATION_ONLY)
                .setNumThreads(4)
            
            interpreter = InterpreterApi.create(modelBuffer, options)
            acceleratorType = "CPU (Bundled)"
            isInitialized = true
            Log.i(TAG, "Model loaded with bundled runtime")
            callback.onInitialized(acceleratorType)
        } catch (e: Exception) {
            Log.e(TAG, "Bundled model load failed: ${e.message}")
            callback.onError("Bundled model load failed: ${e.message}")
        }
    }
    
    private fun loadModelFile(filename: String): MappedByteBuffer {
        val fd = context.assets.openFd(filename)
        val input = FileInputStream(fd.fileDescriptor)
        val channel = input.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }
    
    /**
     * Run depth inference on a bitmap
     */
    fun runInference(bitmap: Bitmap, callback: InferenceCallback) {
        if (!isInitialized) {
            callback.onError("Engine not initialized")
            return
        }
        
        try {
            val startTime = System.currentTimeMillis()
            
            // Preprocess
            val inputBuffer = preprocessBitmap(bitmap)
            
            // Output buffer [1, 256, 256, 1]
            val outputBuffer = Array(1) { Array(MODEL_INPUT_SIZE) { Array(MODEL_INPUT_SIZE) { FloatArray(1) } } }
            
            // Run inference
            interpreter?.run(inputBuffer, outputBuffer)
            
            // Flatten output
            val depthMap = FloatArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
            for (y in 0 until MODEL_INPUT_SIZE) {
                for (x in 0 until MODEL_INPUT_SIZE) {
                    depthMap[y * MODEL_INPUT_SIZE + x] = outputBuffer[0][y][x][0]
                }
            }
            
            val inferenceTime = System.currentTimeMillis() - startTime
            callback.onResult(depthMap, inferenceTime)
            
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}")
            callback.onError("Inference failed: ${e.message}")
        }
    }
    
    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        scaledBitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }
        
        inputBuffer.rewind()
        return inputBuffer
    }
    
    fun getAcceleratorType(): String = acceleratorType
    
    fun getModelInputSize(): Int = MODEL_INPUT_SIZE
    
    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }
}
