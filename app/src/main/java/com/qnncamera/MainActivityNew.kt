package com.qnncamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.qnncamera.camera.CameraManager
import com.qnncamera.engine.BokehProcessor
import com.qnncamera.engine.DepthInferenceEngine

/**
 * Simplified MainActivity - delegates to modular components
 */
class MainActivityNew : AppCompatActivity() {
    
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
    
    // Modules
    private lateinit var cameraManager: CameraManager
    private lateinit var depthEngine: DepthInferenceEngine
    private lateinit var bokehProcessor: BokehProcessor
    
    // UI
    private var previewView: PreviewView? = null
    private var bokehOverlay: ImageView? = null
    private var statusText: TextView? = null
    private var bokehEnabled = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize modules
        cameraManager = CameraManager(this)
        depthEngine = DepthInferenceEngine(this)
        bokehProcessor = BokehProcessor(this)
        
        // Build UI
        setupUI()
        
        // Initialize depth engine
        depthEngine.initialize(object : DepthInferenceEngine.InitCallback {
            override fun onInitialized(accelerator: String) {
                runOnUiThread {
                    statusText?.text = "Ready ($accelerator)"
                }
            }
            
            override fun onError(error: String) {
                runOnUiThread {
                    statusText?.text = "Error: $error"
                }
            }
        })
        
        // Start camera if permitted
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }
    
    private fun setupUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        
        // Camera preview
        previewView = PreviewView(this)
        root.addView(previewView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // Bokeh overlay
        bokehOverlay = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        root.addView(bokehOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // Controls
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(128, 0, 0, 0))
            setPadding(16, 24, 16, 24)
        }
        
        // Bokeh toggle
        val bokehBtn = Button(this).apply {
            text = "Bokeh: OFF"
            setOnClickListener {
                bokehEnabled = !bokehEnabled
                text = if (bokehEnabled) "Bokeh: ON" else "Bokeh: OFF"
                bokehOverlay?.visibility = if (bokehEnabled) View.VISIBLE else View.GONE
            }
        }
        controls.addView(bokehBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        
        // Capture button
        val captureBtn = Button(this).apply {
            text = "📷 Capture"
            setOnClickListener { capturePhoto() }
        }
        controls.addView(captureBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        
        val controlsParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }
        root.addView(controls, controlsParams)
        
        // Status text
        statusText = TextView(this).apply {
            text = "Initializing..."
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(128, 0, 0, 0))
            setPadding(16, 8, 16, 8)
        }
        val statusParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP }
        root.addView(statusText, statusParams)
        
        setContentView(root)
    }
    
    private fun startCamera() {
        val frameCallback = object : CameraManager.FrameCallback {
            override fun onFrame(bitmap: Bitmap, rotationDegrees: Int) {
                if (!bokehEnabled) return
                
                depthEngine.runInference(bitmap, object : DepthInferenceEngine.InferenceCallback {
                    override fun onResult(depthMap: FloatArray, inferenceTimeMs: Long) {
                        val bokehBitmap = bokehProcessor.applyBokeh(
                            bitmap, depthMap, depthEngine.getModelInputSize()
                        )
                        
                        runOnUiThread {
                            bokehOverlay?.setImageBitmap(bokehBitmap)
                            statusText?.text = "Bokeh: ${inferenceTimeMs}ms (${depthEngine.getAcceleratorType()})"
                        }
                    }
                    
                    override fun onError(error: String) {
                        runOnUiThread {
                            statusText?.text = "Error: $error"
                        }
                    }
                })
            }
        }
        
        cameraManager.startCamera(this, previewView!!, frameCallback)
    }
    
    private fun capturePhoto() {
        cameraManager.capturePhoto(object : CameraManager.CaptureCallback {
            override fun onCaptured(file: java.io.File) {
                runOnUiThread {
                    Toast.makeText(this@MainActivityNew, "Saved: ${file.name}", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivityNew, error, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
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
        cameraManager.shutdown()
        depthEngine.close()
        bokehProcessor.destroy()
    }
}
