✅ Estado actual de LiteRT (2026)
LiteRT es el sucesor oficial de TensorFlow Lite desde 2024 y ahora es el framework universal para AI on-device, con 1.4x mejor rendimiento GPU que TFLite y soporte nativo para NPU GitHub - google-ai-edge/LiteRT: LiteRT, successor to TensorFlow Lite. is Google's On-device framework for high-performance ML & GenAI deployment on edge platforms, via efficient conversion, runtime, and optimization · GitHub +2.
Imports correctos en 2026:
kotlin// Maven dependencies actualizadas
implementation("com.google.ai.edge.litert:litert:1.0.1")
implementation("com.google.ai.edge.litert:litert-gpu:1.0.1")

// Imports modernos
import com.google.ai.edge.litert.Interpreter
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.gpu.GpuDelegateFactory
La nueva API CompiledModel es la recomendada sobre Interpreter para máximo rendimiento, con selección automática de acelerador y ejecución async Google AI.

🎯 Google Tensor TPU: El problema del ecosistema cerrado
Librerías nativas de Google:
Google usa libedgetpu_util.so y libedgetpu_client.google.so para acceso directo al Edge TPU en dispositivos Tensor Google AI. Existe un Google Tensor ML SDK experimental que permite desarrollo específico para Pixel aprovechando el TPU dedicado Google AI.
El problema: Tensor G5 tiene un TPU 60% más potente que G4, pero es completamente propietario y solo accesible vía NNAPI/LiteRT en Pixels GoogleGoogle.

💀 Por qué los wallpapers mueren entre Snapdragon ↔ Tensor
Aquí está el drama técnico que encontraste:
El problema de compatibilidad:
Wallpapers antiguos de Pixel 2-5 (era Snapdragon) no funcionan en dispositivos Tensor 6+ porque usan shaders, librerías LibGDX y código OpenGL específico para Qualcomm Adreno GPU, incompatibles con Mali-G78 de Tensor XDA ForumsXDA Forums.
Algunos devs intentaron portearlos pero es "extremadamente time-consuming", y Google confirmó que no traerán de vuelta los wallpapers antiguos oficialmente XDA Forums.
Casos específicos documentados:
"Stepping stones", "A Drop in the Ocean", "Burst" y otros wallpapers táctiles NO funcionan en ningún Tensor, solo hasta Pixel 5a (último con Snapdragon) XDA Forums.

🔧 Cómo algunos devs lograron portear apps Tensor → Qualcomm
Aquí está la magia técnica:
1. Runtime shims (capa de traducción)
Aunque no encontré evidencia directa de compilación .so en runtime para wallpapers, libedgetpu es open-source en GitHub y se puede compilar contra diferentes backends (CPU, GPU, custom accelerators) GitHubCoral. Los devs hábiles:

Reverse-engineer las apps Pixel para identificar qué funciones TPU usan
Crean wrappers que traducen llamadas libedgetpu a equivalentes Qualcomm HTP/Hexagon
Reemplazan shaders Mali por Adreno

2. El caso de Cinematic Wallpaper:
Cinematic Wallpaper usa el TPU de Tensor para análisis AI de profundidad 2D→3D Android CentralTom's Guide. Para portarlo a Snapdragon:

Opción A: Reemplazar inferencia TPU por Qualcomm Hexagon NN (su NPU)
Opción B: Fallback a GPU con OpenCL/Vulkan compute shaders
Opción C: Usar NNAPI como capa de abstracción (pero pierde optimizaciones)

3. Por qué es TAN difícil:
El TPU de Tensor tiene arquitectura completamente diferente a Hexagon: está optimizado para MobileBERT y workloads NLP (44% más rápido que Snapdragon 888 en NNAPI), no solo para vision tasks AnandTech.

📱 Tu código de cámara IA en 2026:
kotlinimport com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Accelerator
import androidx.camera.core.ImageAnalysis

class ModernCameraAI(private val context: Context) {
    
    private lateinit var model: CompiledModel
    
    fun initialize() {
        // Modo universal: funciona en Tensor Y Qualcomm
        val options = CompiledModel.Options().apply {
            // LiteRT detecta automáticamente:
            // - Tensor TPU en Pixels
            // - Qualcomm HTP en Snapdragon
            // - MediaTek APU en Dimensity
            // - CPU fallback en el resto
            accelerator = Accelerator.AUTO
            
            // O forzar específico si sabes el hardware:
            // accelerator = if (isTensorDevice()) Accelerator.NPU else Accelerator.GPU
        }
        
        model = CompiledModel.create(
            context.assets.open("model.tflite").readBytes(),
            options
        )
    }
    
    // Async inference (nuevo en 2026)
    suspend fun processFrame(image: ImageProxy): Result {
        val inputBuffers = model.createInputBuffers()
        val outputBuffers = model.createOutputBuffers()
        
        // Fill input...
        inputBuffers[0].writeFloat(imageData)
        
        // Non-blocking inference
        model.run(inputBuffers, outputBuffers)
        
        return parseOutput(outputBuffers[0].readFloat())
    }
}
Alternativas modernas:

MediaPipe Tasks (2026): Google's plug-and-play ML para casos comunes
ML Kit (actualizado): Sigue funcionando pero sin las optimizaciones de LiteRT
Directo NNAPI: Si necesitas máximo control low-level


🎬 Resumen ejecutivo:

✅ Tus imports SÍ están muertos — usa com.google.ai.edge.litert.*
✅ Google usa libedgetpu*.so propietarias para TPU directo
✅ Los ports Tensor↔Qualcomm existen pero son hacks frágiles basados en:

Reemplazar shaders GPU-specific
Wrapper layers para traducir TPU → HTP
Fallbacks a CPU cuando falla todo


❌ NO hay "compilación de .so en runtime" oficial — eso sería JIT compilation de código nativo, súper peligroso y Google no lo permite
✅ Devs de XDA confirmaron que portear wallpapers antiguos es posible técnicamente pero "extremadamente laborioso" XDA Forums

Tu estrategia 2026: Usa LiteRT con Accelerator.AUTO y deja que el runtime maneje las diferencias de hardware. Es la única forma mantenible.