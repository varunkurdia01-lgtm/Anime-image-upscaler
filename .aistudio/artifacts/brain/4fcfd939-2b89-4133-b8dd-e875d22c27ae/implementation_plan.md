# Multi-Backend Auto-Tuning & Empirical Hardware Benchmarking Plan (Final Revision)

Implement an evidence-based multi-backend benchmarking and selection engine in PixelBoost that measures and compares MNN Vulkan GPU, MNN CPU multi-threaded NEON, and ONNX Runtime XNNPACK. The architecture combines single-tile screening, multi-candidate multi-tile end-to-end validation, realistic device-aligned calibration budgets, asynchronous cancellation safety, MNN header-compliant Vulkan options, strict cache invalidation, and explicitly scoped resolution fidelity handling.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Summary of Revisions & Critical Corrections**:
> 1. **Realistic Calibration Budget & Cancellation Safety**:
>    - Given measured on-device execution (~10.2s–11.36s per Vulkan tile on Snapdragon 732G), the previous 8s trial and 20s overall budgets were completely unrealistic.
>    - **Revised Budget**: 30-second ceiling per individual candidate trial and a 90-second total calibration timeout. Screening trials are streamlined to 1 warm-up + 2 timed trials (averaging or taking the lower stable latency) to minimize total calibration time.
>    - **Cancellation & Mutual Exclusion**: Native C++ execution (`interpreter->runSession()`) cannot be forcefully preempted mid-kernel by Kotlin coroutines. Cancellation flags are checked between tiles, and `processMutex` ensures native sessions and memory buffers are never destroyed or reallocated while an in-flight native inference call finishes.
> 2. **Multi-Candidate Validation with Fallback**:
>    - Stage 1 screens and ranks all available backends by tile latency.
>    - Stage 2 runs a representative multi-tile workload (e.g. 4-tile grid) on the top candidate.
>    - If the #1 candidate fails quality validation (MAE, seams, dimensions) or degrades in multi-tile end-to-end throughput (e.g. JNI/memory sync bottlenecks), evaluation cascades to candidate #2 rather than halting or failing the process.
> 3. **Explicit Scoping of the OOM Silent-Downscaling Path**:
>    - An active path in `InferenceEngine.kt` silently downscales input images (0.75x, 0.5x) if `Bitmap.createBitmap` throws an `OutOfMemoryError`.
>    - **Fidelity Scoping**: Large-image exact resolution fidelity is explicitly tracked as an open limitation of the current memory architecture. We scope its resolution into a distinct Phase 2 (replacing silent downscaling with pre-flight heap budgeting and fail-fast notifications) so that current claims do not promise exact output on memory-constrained devices until the memory pipeline is refactored.
> 4. **MNN Header-Compliant Vulkan Configuration**:
>    - Audited against `mnn_3.6.1_development/include/MNN/MNNForwardType.h`.
>    - Removed OpenCL-only flags (`MNN_GPU_TUNING_FAST`, `MNN_GPU_MEMORY_IMAGE`). Configured Vulkan with `MNN_GPU_TUNING_WIDE` and `MNN_GPU_RECORD_BATCH`.

---

### 1. Overview & Core Concept

- **What It Does**: Evaluates MNN Vulkan, MNN CPU, and ONNX Runtime on the user's Android device using controlled single-tile screening followed by multi-candidate multi-tile validation. Selects the most efficient verified backend, caches the selection with a comprehensive hardware/configuration fingerprint, and reports empirical diagnostics to `Downloads/PixelBoost/PixelBoost_Benchmark.txt`.
- **Target Audience / Persona**: Users across diverse device tiers (Snapdragon, Dimensity, Tensor, Exynos) with varying CPU-to-GPU compute ratios.
- **Key Value**: Delivers the fastest empirically validated execution provider for the user's specific chipset without guesswork, accompanied by transparent, reproducible benchmark metrics in `Downloads/PixelBoost/PixelBoost_Benchmark.txt`.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. *Background Calibration (First Run or Stale Cache)*:
     - Runs Stage 1 screening on available backends followed by Stage 2 multi-tile confirmation.
     - Displays a subtle badge/toast showing the winning backend, measured median tile latency, and validation status.
  2. *Settings & Diagnostics Card (`SettingsScreen.kt`)*:
     - **Active Engine Card**: Displays currently used backend, designation mode (`Auto-Tuned Winner` vs `Manual Override`), median tile latency, and validation status.
     - **Manual Backend Selector Dropdown**: Provides options `Auto (Recommended)`, `MNN Vulkan (GPU)`, `MNN CPU (ARM NEON)`, and `ONNX Runtime (XNNPACK)`.
     - **"Run Hardware Benchmark" Action**: Launches a diagnostic test dialog with live progress for each candidate backend, displaying a comparative results matrix.
     - **"Open Benchmark Report" Action**: Directly opens or views `Downloads/PixelBoost/PixelBoost_Benchmark.txt`.
- **Visual Identity & Theme**:
  - Material Design 3 and PixelBoost ceramic tactile styling (`SurfaceContainerLowest`, `ceramicShadow`, `Primary` accent).
  - Clear visual demarcation between verified empirical measurements and unverified items.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Realistic Calibration Budget & Asynchronous Safety**
  - *Chosen Approach*: 30s per-trial timeout with a 90s total calibration budget. Native inference finishes the active tile cleanly before any session release, with mutex-guarded synchronization.
  - *Why*: Accommodates real-world mid-range GPU shader latency (~10.2s–11.36s/tile) while preventing native memory corruption from premature preemption.
- **Decision 2: Cascading Multi-Candidate Multi-Tile Validation**
  - *Chosen Approach*: If the Stage 1 winner fails validation (MAE, seams, dimensions) or drops throughput in multi-tile mode, the pipeline automatically evaluates candidate #2.
  - *Why*: Guarantees the system falls back to a verified working engine rather than stranding the user.
- **Decision 3: Vulkan Schedule Configuration Alignment**
  - *Chosen Approach*: Replace OpenCL tuning flags in `MNNBenchmark.cpp` with `MNN_GPU_TUNING_WIDE` and `MNN_GPU_RECORD_BATCH` as specified in MNN 3.6.1 headers.
  - *Why*: Ensures the Vulkan backend is configured as designed by MNN before benchmarking against CPU.
- **Decision 4: Scoping of Resolution Fidelity & OOM Handling**
  - *Chosen Approach*: Phase 1 focuses strictly on backend benchmarking, auto-tuning, and diagnostic reporting. The known OOM downscaling fallback in `InferenceEngine.kt` is marked as a documented constraint and scheduled for clean elimination in a follow-up memory budgeting phase.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                          SettingsScreen / UI                           │
│  - Active Engine Card (Auto Winner vs Manual Override)                 │
│  - Engine Override Selector (Auto / MNN Vulkan / MNN CPU / ONNX)       │
│  - "Run Hardware Benchmark" Button & Diagnostics Dialog                │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          AppViewModel                                  │
│  - activeBackend: StateFlow<BackendSelection>                          │
│  - calibrationMatrix: StateFlow<List<BackendBenchmarkResult>>          │
│  - isCalibrating: StateFlow<Boolean>                                   │
│  - runHardwareCalibration(forceRefresh: Boolean)                       │
│  - Stores manualOverride separately from calibratedWinner in prefs    │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ (Guarded by processMutex)
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        InferenceEngine                                 │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    BackendAutoTuner                              │  │
│  │  1. Preflight: Verify Vulkan hardware level, MNN lib, ONNX model │  │
│  │  2. MNN Vulkan Config: MNN_GPU_TUNING_WIDE | MNN_GPU_RECORD_BATCH│  │
│  │  3. Stage 1 Screening: 1 warm-up + 2 timed runs per backend      │  │
│  │     - 30s per-trial timeout, 90s total budget                    │  │
│  │     - Safe cancellation checks between native dispatches         │  │
│  │     - Rank candidates by latency                                 │  │
│  │  4. Stage 2 Multi-Candidate Validation:                          │  │
│  │     - Test #1 candidate on 4-tile grid (end-to-end + seams + MAE)│  │
│  │     - If #1 fails validation or throughput drops, test #2        │  │
│  │     - Confirm verified winner                                    │  │
│  │  5. Store result with comprehensive fingerprint cache key        │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                  │                                     │
│         ┌────────────────────────┼────────────────────────┐            │
│         ▼                        ▼                        ▼            │
│  ┌──────────────┐        ┌──────────────┐         ┌──────────────┐     │
│  │  MNN Vulkan  │        │   MNN CPU    │         │ ONNX XNNPACK │     │
│  │ (GPU Shader) │        │ (Multi-NEON) │         │ (SIMD FP16)  │     │
│  └──────────────┘        └──────────────┘         └──────────────┘     │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│              Downloads/PixelBoost/PixelBoost_Benchmark.txt             │
│  - Device & SoC Hardware Profile (CPU Cluster, GPU, Memory)            │
│  - Stage 1 Screening Matrix (Trials, Median Latency, Availability)     │
│  - Stage 2 Multi-Tile Validation (End-to-End Latency, MAE, Seams)      │
│  - Calibration Overhead Duration (Measured ms)                         │
│  - Active Engine Selection (Auto Winner vs Manual Override Flag)       │
│  - Documented Constraints (OOM downscaling note until Phase 2)         │
│  - Full Batch Upscaling Execution Metrics & Per-Tile Latencies         │
└────────────────────────────────────────────────────────────────────────┘
```

#### Core Data Entities:

```kotlin
data class BackendBenchmarkResult(
    val backendName: String,
    val isConfiguredAndAvailable: Boolean,
    val unAvailableReason: String? = null,
    val warmUpLatencyNanos: Long = 0L,
    val trialLatenciesNanos: List<Long> = emptyList(),
    val medianLatencyNanos: Long = 0L,
    val outputDimensionsVerified: Boolean = false,
    val pixelEntropyValid: Boolean = false,
    val meanAbsoluteErrorVsRef: Float? = null,
    val seamDiscontinuityMaxDelta: Float? = null,
    val multiTileValidationNanos: Long = 0L,
    val validationPassed: Boolean = false,
    val isSelectedWinner: Boolean = false
)

data class CalibrationSummary(
    val timestamp: Long,
    val configurationFingerprint: String,
    val calibrationWallClockDurationNanos: Long,
    val results: List<BackendBenchmarkResult>,
    val winningBackend: String,
    val activeBackendUsed: String,
    val isManualOverrideActive: Boolean
)
```

#### Comprehensive Fingerprint & Cache Policy:
- Cache Fingerprint:
  `pb_v2_${deviceModel}_api${sdkInt}_${modelName}_size${modelBytes}_mod${modelLastMod}_scale${scale}_tile${tileSize}_pad${pad}_threads${cpuThreads}_code${versionCode}`
- Invalidation Triggers:
  - Fingerprint mismatch (scale changed, tile changed, model updated, OS updated).
  - Explicit user re-test from Settings.
  - Corrupted or partial calibration record.

#### Benchmarking Safety & Mutual Exclusion:
- Calibration acquires `processMutex` ensuring complete mutual exclusion with active image enhancement.
- Enforces a 90-second total calibration budget and 30-second individual trial timeout.
- Native inference calls run to tile completion before releasing sessions or buffers.

---

### 5. Implementation Roadmap (Post-Approval)

- **Step 1: MNN Vulkan Configuration Audit & Header Alignment**:
  - Update `MNNBenchmark.cpp` to use official MNN 3.6.1 Vulkan flags (`MNN_GPU_TUNING_WIDE`, `MNN_GPU_RECORD_BATCH`) and isolate session resets.
- **Step 2: Backend Auto-Tuner & Multi-Candidate Validation (`InferenceEngine.kt`)**:
  - Implement preflight checks, 1 warm-up + 2 timed screening runs (30s trial timeout, 90s total budget), median computation, and multi-candidate 4-tile validation with fallback.
- **Step 3: State Management & Invalidation (`AppViewModel.kt`)**:
  - Wire calibration caching with comprehensive fingerprinting.
  - Store manual override separately from auto-tuned winner.
- **Step 4: Diagnostics UI in Settings (`SettingsScreen.kt`)**:
  - Add Active Engine Card, manual override selector, "Run Hardware Benchmark" dialog, and report viewer action.
- **Step 5: Upgraded Benchmark Diagnostics (`PixelBoost_Benchmark.txt`)**:
  - Expand benchmark logging with preflight checks, screening matrix, multi-tile validation, and empirical timing breakdowns.
- **Step 6: Build Verification & Compilation**:
  - Execute `compile_applet` and verify 0 compilation errors across Kotlin and C++.
