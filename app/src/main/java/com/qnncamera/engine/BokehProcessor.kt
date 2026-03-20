package com.qnncamera.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur

/**
 * GPU-accelerated bokeh effect processor using RenderScript
 */
class BokehProcessor(context: Context) {
    
    companion object {
        private const val TAG = "BokehProcessor"
        private const val MAX_BLUR_RADIUS = 25f
    }
    
    private val renderScript: RenderScript = RenderScript.create(context)
    private val blurScript: ScriptIntrinsicBlur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
    
    /**
     * Apply bokeh effect based on depth map
     * @param original Original camera frame
     * @param depthMap Normalized depth values (0-1, higher = closer)
     * @param depthMapSize Size of the depth map (usually 256)
     * @param blurRadius Blur intensity (1-25)
     */
    fun applyBokeh(
        original: Bitmap,
        depthMap: FloatArray,
        depthMapSize: Int,
        blurRadius: Float = MAX_BLUR_RADIUS
    ): Bitmap {
        val width = original.width
        val height = original.height
        
        // Create blurred version using RenderScript (GPU accelerated)
        val blurredBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val inputAlloc = Allocation.createFromBitmap(renderScript, blurredBitmap)
        val outputAlloc = Allocation.createFromBitmap(renderScript, blurredBitmap)
        
        blurScript.setRadius(blurRadius.coerceIn(1f, MAX_BLUR_RADIUS))
        blurScript.setInput(inputAlloc)
        blurScript.forEach(outputAlloc)
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
            val dx = (x * depthMapSize / width).coerceIn(0, depthMapSize - 1)
            val dy = (y * depthMapSize / height).coerceIn(0, depthMapSize - 1)
            val depthVal = (depthMap[dy * depthMapSize + dx] - minDepth) / range
            
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
    
    /**
     * Create depth visualization (for debugging)
     */
    fun createDepthVisualization(depthMap: FloatArray, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        
        val minDepth = depthMap.minOrNull() ?: 0f
        val maxDepth = depthMap.maxOrNull() ?: 1f
        val range = if (maxDepth - minDepth > 0) maxDepth - minDepth else 1f
        
        val pixels = IntArray(size * size)
        for (i in depthMap.indices) {
            val depth = (depthMap[i] - minDepth) / range
            val r = (depth * 255).toInt().coerceIn(0, 255)
            val b = ((1 - depth) * 255).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(r, 0, b)
        }
        
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }
    
    fun destroy() {
        blurScript.destroy()
        renderScript.destroy()
    }
}
