.class public Lai/qnn/QnnManager;
.super Ljava/lang/Object;
.source "QnnManager.java"

# Static instance for singleton
.field private static instance:Lai/qnn/QnnManager;

# Instance fields
.field private initialized:Z
.field private modelPath:Ljava/lang/String;

# Static initializer - load native library
.method static constructor <clinit>()V
    .registers 1
    
    const-string v0, "qnn_wrapper"
    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V
    
    return-void
.end method

# Constructor
.method private constructor <init>()V
    .registers 2
    
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    
    const/4 v0, 0x0
    iput-boolean v0, p0, Lai/qnn/QnnManager;->initialized:Z
    
    const-string v0, "/data/local/tmp/qnn_models/"
    iput-object v0, p0, Lai/qnn/QnnManager;->modelPath:Ljava/lang/String;
    
    return-void
.end method

# Get singleton instance
.method public static getInstance()Lai/qnn/QnnManager;
    .registers 2
    
    sget-object v0, Lai/qnn/QnnManager;->instance:Lai/qnn/QnnManager;
    
    if-nez v0, :return_instance
    
    # Create new instance
    new-instance v0, Lai/qnn/QnnManager;
    invoke-direct {v0}, Lai/qnn/QnnManager;-><init>()V
    sput-object v0, Lai/qnn/QnnManager;->instance:Lai/qnn/QnnManager;
    
    :return_instance
    return-object v0
.end method

# Initialize QNN
.method public init()I
    .registers 3
    
    iget-boolean v0, p0, Lai/qnn/QnnManager;->initialized:Z
    if-eqz v0, :do_init
    
    # Already initialized
    const/4 v0, 0x0
    return v0
    
    :do_init
    iget-object v0, p0, Lai/qnn/QnnManager;->modelPath:Ljava/lang/String;
    invoke-virtual {p0, v0}, Lai/qnn/QnnManager;->nativeInit(Ljava/lang/String;)I
    move-result v0
    
    if-nez v0, :init_failed
    
    const/4 v1, 0x1
    iput-boolean v1, p0, Lai/qnn/QnnManager;->initialized:Z
    
    :init_failed
    return v0
.end method

# Release QNN resources
.method public release()V
    .registers 2
    
    iget-boolean v0, p0, Lai/qnn/QnnManager;->initialized:Z
    if-eqz v0, :not_initialized
    
    invoke-virtual {p0}, Lai/qnn/QnnManager;->nativeRelease()V
    
    const/4 v0, 0x0
    iput-boolean v0, p0, Lai/qnn/QnnManager;->initialized:Z
    
    :not_initialized
    return-void
.end method

# Enhance image with AI
.method public enhanceImage(Landroid/graphics/Bitmap;I)Landroid/graphics/Bitmap;
    .registers 5
    .param p1, "bitmap"    # Landroid/graphics/Bitmap;
    .param p2, "mode"      # I (0=night, 1=portrait, 2=hdr)
    
    iget-boolean v0, p0, Lai/qnn/QnnManager;->initialized:Z
    if-nez v0, :do_enhance
    
    # Not initialized, return original
    return-object p1
    
    :do_enhance
    invoke-virtual {p0, p1, p2}, Lai/qnn/QnnManager;->nativeEnhanceImage(Landroid/graphics/Bitmap;I)Landroid/graphics/Bitmap;
    move-result-object v0
    
    return-object v0
.end method

# Check if NPU is available
.method public isNpuAvailable()Z
    .registers 2
    
    invoke-virtual {p0}, Lai/qnn/QnnManager;->nativeIsNpuAvailable()Z
    move-result v0
    
    return v0
.end method

# Get backend info string
.method public getBackendInfo()Ljava/lang/String;
    .registers 2
    
    invoke-virtual {p0}, Lai/qnn/QnnManager;->nativeGetBackendInfo()Ljava/lang/String;
    move-result-object v0
    
    return-object v0
.end method

# Set model path
.method public setModelPath(Ljava/lang/String;)V
    .registers 2
    .param p1, "path"    # Ljava/lang/String;
    
    iput-object p1, p0, Lai/qnn/QnnManager;->modelPath:Ljava/lang/String;
    
    return-void
.end method

# Native methods - implemented in libqnn_wrapper.so
.method public native nativeInit(Ljava/lang/String;)I
.end method

.method public native nativeRelease()V
.end method

.method public native nativeEnhanceImage(Landroid/graphics/Bitmap;I)Landroid/graphics/Bitmap;
.end method

.method public native nativeIsNpuAvailable()Z
.end method

.method public native nativeGetBackendInfo()Ljava/lang/String;
.end method
