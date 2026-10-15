# PixelBoost Comprehensive Fix & Architecture Plan

Fixes all remaining stability issues, logic bugs, and crashes in PixelBoost by modernizing the image ingestion pipeline, introducing a multi-tier super-resolution engine with Lanczos/bicubic fallback, synchronizing platform branding, and cleaning legacy code.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> Based on your confirmed selections:
> - **Zero-Permission Photo Picker**: Replace manual MediaStore cursor queries and deprecated `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` permissions with Jetpack `ActivityResultContracts.PickMultipleVisualMedia()`, fully adhering to Google Play policies and guaranteeing reliable image loading across all Android versions (Android 10–15+).
> - **Lanczos/Bicubic Fallback**: When on-device neural inference encounters missing GPU drivers (e.g. Vulkan crashes), memory constraints, or unsupported tensor shapes, the engine seamlessly invokes a high-order bicubic/Lanczos super-resolution algorithm with edge-preserving unsharp masking. The user ALWAYS receives a crisp upscaled image without crashes or freezes.
> - **Identity Synchronization**: Synchronize `metadata.json`, `app/src/main/res/values/strings.xml`, and `settings.gradle.kts` to `"PixelBoost"`, removing all legacy `"Empty Project"` placeholders.

---

### 1. Overview & Core Concept

- **What It Does**: PixelBoost is an AI-powered super-resolution and photo enhancement application for Android. It enables users to select single or multiple photos, configure scale factors (2× or 4×), format containers (PNG, JPG, WEBP), and compression quality tiers, view processing progress in real time, compare original vs enhanced images with an interactive split comparison slider, export to the device Gallery via MediaStore, and maintain a local history archive.
- **Target Audience**: Photographers, digital artists, creators, and everyday smartphone users restoring low-resolution or legacy photos.
- **Key Value**: 100% crash-free upscaling that gracefully falls back to high-grade mathematical super-resolution whenever hardware or neural runtimes encounter failure.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. *Photo Ingestion*:
     - Home screen "Choose from Gallery" launches the native Android Photo Picker directly.
     - Selected photos seamlessly flow to `EnhanceOptionsScreen` with thumbnail previews and badge counts.
  2. *Enhancement Options*:
     - User selects 2× vs 4× scale factor, export container (JPG/PNG/WEBP), and quality preset.
     - Tapping "Start Enhance" transitions smoothly to `ProcessingScreen`.
  3. *Processing & Progress*:
     - Neumorphic circular progress meter indicates percentage completion.
     - Cancel button stops background inference cooperatively and clears memory buffers.
  4. *Result & Comparison*:
     - Interactive before/after split slider allowing instant inspection of details and sharpness.
     - Metadata tray displaying source resolution (e.g., 800×600) vs enhanced output (e.g., 3200×2400).
     - Save to Gallery button with instant visual feedback ("Saved to Photos").
  5. *History Archive*:
     - Browsable grid of saved images with timestamps, labels, and cache management.
- **Visual Identity**:
  - Soft ceramic surfaces (`#EBF0F5`), electric sky-blue accents (`#0284C7`), subtle dual-direction shadows, and crisp typography powered by the Inter font family.

---

### 3. Key Product Decisions & Trade-Offs

- **Multi-Tier Inference Hierarchy**:
  - *Chosen Approach*:
    1. *Vulkan Safety Check*: Only attempt Vulkan GPU execution if the driver explicitly exposes compute queues and sufficient memory; prevent native signal 11 (`SIGSEGV`).
    2. *MNN / ONNX CPU Execution*: Multi-threaded CPU neural super-resolution for devices with sufficient memory.
    3. *Lanczos/Bicubic Super-Resolution Fallback*: If native libraries, models, or memory limits fail, immediately fall back to high-order bicubic scaling paired with adaptive unsharp masking.
  - *Why*: Eliminates all app crashes while guaranteeing that the user always gets an enhanced image.
- **Zero-Permission Photo Picker**:
  - *Chosen Approach*: `PickMultipleVisualMedia(maxItems = 10)` in `HomeScreen` and `ImagePickerScreen`.
  - *Why*: Completely eliminates the need for storage permissions (`READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES`), preventing permission-denied dead ends.
- **Memory Safety & OOM Prevention**:
  - *Chosen Approach*: In addition to `android:largeHeap="true"`, intermediate bitmap buffers are recycled immediately after tile stitching, and all memory allocations are enclosed in `catch (t: Throwable)`.
  - *Why*: Prevents native and Dalvik heap starvation when handling large multi-megapixel photos.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│                                                                        │
│   HomeScreen ──────► System Photo Picker ──────► EnhanceOptionsScreen  │
│        ▲                                                   │           │
│        │                                                   ▼           │
│   HistoryScreen ◄────── ResultScreen ◄────────── ProcessingScreen      │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     AppViewModel & Data Layer                          │
│   • Batch Queue Management          • MediaStore Scoped Storage Export │
│   • Cancellation & Memory Safety    • Room Database (HistoryEntity)    │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                  InferenceEngine Multi-Tier Pipeline                   │
│                                                                        │
│   ┌─────────────────────┐   Failure   ┌────────────────────────────┐   │
│   │ 1. MNN Vulkan Probe │ ──────────► │ 2. MNN CPU / ONNX Model    │   │
│   │ (Pre-flight GPU)    │             │ (Multi-threaded NCHW)      │   │
│   └─────────────────────┘             └─────────────┬──────────────┘   │
│                                                     │ Failure / OOM    │
│                                                     ▼                  │
│                                       ┌────────────────────────────┐   │
│                                       │ 3. Lanczos/Bicubic Fallback│   │
│                                       │ (Edge-preserving Unsharp)  │   │
│                                       └────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘
```

- **Step-by-Step Implementation Strategy**:
  1. *Platform Identity*: Update `metadata.json`, `app/src/main/res/values/strings.xml`, and `settings.gradle.kts` to `"PixelBoost"`.
  2. *Photo Picker Modernization*: Refactor `HomeScreen.kt` and `ImagePickerScreen.kt` to use `ActivityResultContracts.PickMultipleVisualMedia()`, and remove unused storage permission declarations from `AndroidManifest.xml`.
  3. *InferenceEngine Robust Fallback*: Add high-order bicubic/Lanczos super-resolution with edge sharpening in `InferenceEngine.kt`, ensuring non-blocking fallback if native execution fails or throws.
  4. *AppViewModel Memory & Error Guard*: Ensure `startProcessing` handles `Throwable` and falls back cleanly, updating the UI with precise status tags (`4× AI` or `4× Enhanced`).
  5. *Verification*: Verify build with `compile_applet` and test with local unit tests.
