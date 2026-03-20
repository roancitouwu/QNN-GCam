package com.qnncamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "QNN_CAMERA"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
    
    private lateinit var cameraExecutor: ExecutorService
    private var previewView: PreviewView? = null
    private var tfliteInterpreter: Interpreter? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        previewView = PreviewView(this)
        setContentView(previewView)
        
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
            tfliteInterpreter = Interpreter(modelBuffer)
            Log.i(TAG, "MiDaS model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MiDaS model", e)
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
            Log.w(TAG, "Could not set QNN env vars (normal on some devices)", e)
        }
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
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
                Log.i(TAG, "Camera started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
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
        cameraExecutor.shutdown()
    }
}
