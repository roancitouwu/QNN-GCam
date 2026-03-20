# Real-Time Bokeh Implementation Research

**Status**: Active investigation - found vendor libs and configs

## Device Capabilities (Xiaomi/SD685)

### Camera Hardware
- 7 cameras (IDs 0-6)
- Camera 4: Logical multi-camera with `android.logicalMultiCamera.physicalIds`
- Xiaomi vendor bokeh extensions available

### Xiaomi Bokeh Extensions Detected
```
com.xiaomi.camera.supportedfeatures.bokehVendorID
com.xiaomi.camera.supportedfeatures.bokehDepthBufferSize
xiaomi.camera.bokehinfo.AvailableTargetFpsRanges
```

## Implementation Options

### Option A: Camera2 Extensions API (Android 12+)
Uses `CameraExtensionSession` with `EXTENSION_BOKEH`:
- Native hardware bokeh
- Real-time preview with blur
- Requires Android 12 (API 31+)

```kotlin
// Check if bokeh extension is supported
val extensionChars = cameraManager.getCameraExtensionCharacteristics(cameraId)
val supportsBokeh = extensionChars.supportedExtensions.contains(
    CameraExtensionCharacteristics.EXTENSION_BOKEH
)

// Create bokeh session
val config = ExtensionSessionConfiguration(
    CameraExtensionCharacteristics.EXTENSION_BOKEH,
    outputConfigs,
    executor,
    stateCallback
)
cameraDevice.createExtensionSession(config)
```

### Option B: Xiaomi Vendor Tags (Direct HAL Access)
Access Xiaomi's proprietary bokeh via CaptureRequest vendor tags:
- `com.xiaomi.camera.supportedfeatures.bokehVendorID`
- Requires discovering correct vendor tag keys
- May work on older Android versions

### Option C: Dual Camera Depth + GPU Blur
Manual implementation:
1. Use logical camera (ID 4) with physical cameras
2. Get depth from secondary camera
3. Apply GPU-accelerated blur based on depth
4. Render to preview surface

## Video Recording Light Stabilization

### Problem
Inconsistent brightness/exposure during video recording.

### Solution: Lock AE (Auto Exposure)
```kotlin
captureRequestBuilder.set(
    CaptureRequest.CONTROL_AE_LOCK, 
    true
)
// Or use manual exposure
captureRequestBuilder.set(
    CaptureRequest.CONTROL_AE_MODE,
    CaptureRequest.CONTROL_AE_MODE_OFF
)
captureRequestBuilder.set(
    CaptureRequest.SENSOR_EXPOSURE_TIME,
    fixedExposureTime
)
captureRequestBuilder.set(
    CaptureRequest.SENSOR_SENSITIVITY,
    fixedISO
)
```

### Available Exposure Modes on Device
```
org.codeaurora.qcamera3.exposure_metering.available_modes
org.codeaurora.qcamera3.iso_exp_priority.exposure_time_range
```

## Recommended Approach

1. **First**: Try Camera2 Extensions API with EXTENSION_BOKEH
   - Cleanest solution if supported
   - Hardware-accelerated

2. **Fallback**: Xiaomi vendor tags
   - Investigate actual tag values/usage
   - May require reverse engineering MIUI camera

3. **Last Resort**: Manual dual-camera + RenderScript/GPU blur
   - Most complex but guaranteed to work
   - Can use OpenGL ES for real-time blur

## Xiaomi Vendor Bokeh Libraries Found

### Native Libraries (`/vendor/lib64/`)
```
libmiphone_preview_bokeh.so   - Real-time preview bokeh
libmiphone_capture_bokeh.so   - Capture bokeh
libanc_single_rt_bokeh.so     - Single camera real-time bokeh
libanc_single_bokeh.so        - Single camera bokeh
libmialgo_mc_bokeh_cdsp_skel.so - Bokeh on Hexagon CDSP
com.xiaomi.plugin.mibokeh.so  - MI Bokeh plugin
com.xiaomi.plugin.capbokeh.so - Capture bokeh plugin
```

### Function Signatures (from libmiphone_preview_bokeh.so)
```cpp
MIALGO_PreviewBokeh_Launch(void**, tag_LaunchParam&)
MIALGO_PreviewBokeh_Proc(void**, tag_ProcParam&)
MIALGO_PreviewBokeh_Destroy(void**)
PreviewBokehEffect(stBokehPreview*, MialgoPoint, int, MialgoImg*, char*, char*, char*)
PreviewInitWhenLaunch_BokehEffect(stBokehPreview*, int, int)
PreviewDestoryWhenClose_BokehEffect(stBokehPreview*)
```

### Configuration Files
```
/vendor/etc/camera/preview_bokeh_params.json
/vendor/etc/camera/dualcam_bokeh_params.json
/vendor/etc/camera/xiaomi/dualbokehsnapshot.json
/vendor/etc/camera/xiaomi/frontbokehsnapshot.json
```

### Debug Properties
```
persist.vendor.camera.mipreviewbokeh.inputdump
```

## Implementation Approach

### Option 1: Direct JNI to Vendor Libs (Complex)
- Create JNI wrapper for libmiphone_preview_bokeh.so
- Need to reverse engineer struct definitions
- Risk: May need SELinux policy changes

### Option 2: Use Camera HAL Vendor Tags
The device exposes Xiaomi vendor tags:
```
com.xiaomi.camera.supportedfeatures.bokehVendorID
com.xiaomi.camera.supportedfeatures.bokehDepthBufferSize
xiaomi.camera.bokehinfo.AvailableTargetFpsRanges
```

Try setting these via Camera2 API CaptureRequest.

### Option 3: RenderScript/OpenCL Blur
Since the vendor libs use OpenCL, we could:
1. Get depth from MiDaS model (already working)
2. Apply blur using RenderScript ScriptIntrinsicBlur
3. Composite based on depth mask

## Next Steps
1. Try Camera2 vendor tags for bokeh activation
2. Test RenderScript blur approach
3. If needed, create JNI wrapper for vendor libs
4. Add video recording with locked exposure
