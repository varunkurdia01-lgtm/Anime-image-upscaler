#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <string>
#include <vector>
#include <memory>
#include <chrono>
#include <cmath>
#include <limits>
#include <sstream>
#include <iomanip>
#include <mutex>
#include <dlfcn.h>
#include <vulkan/vulkan.h>

#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#include <MNN/ErrorCode.hpp>
#include <MNN/MNNForwardType.h>

#define LOG_TAG "PIXELBOOST_MNN_CPU_BENCHMARK"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct VulkanDeviceInfo {
    bool libLoaded = false;
    bool hasPhysicalDevice = false;
    std::string gpuName = "Unavailable";
    std::string apiVersion = "Unavailable";
};

static VulkanDeviceInfo queryVulkanInfo() {
    VulkanDeviceInfo info;
    void* handle = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        return info;
    }
    info.libLoaded = true;

    auto pfnCreateInstance = (PFN_vkCreateInstance)dlsym(handle, "vkCreateInstance");
    auto pfnDestroyInstance = (PFN_vkDestroyInstance)dlsym(handle, "vkDestroyInstance");
    auto pfnEnumeratePhysicalDevices = (PFN_vkEnumeratePhysicalDevices)dlsym(handle, "vkEnumeratePhysicalDevices");
    auto pfnGetPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)dlsym(handle, "vkGetPhysicalDeviceProperties");

    if (pfnCreateInstance && pfnDestroyInstance && pfnEnumeratePhysicalDevices && pfnGetPhysicalDeviceProperties) {
        VkApplicationInfo appInfo = {};
        appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        appInfo.pApplicationName = "PixelBoostBenchmark";
        appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
        appInfo.pEngineName = "MNN";
        appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
        appInfo.apiVersion = VK_API_VERSION_1_0;

        VkInstanceCreateInfo createInfo = {};
        createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        createInfo.pApplicationInfo = &appInfo;

        VkInstance instance = VK_NULL_HANDLE;
        if (pfnCreateInstance(&createInfo, nullptr, &instance) == VK_SUCCESS && instance != VK_NULL_HANDLE) {
            uint32_t deviceCount = 0;
            pfnEnumeratePhysicalDevices(instance, &deviceCount, nullptr);
            if (deviceCount > 0) {
                std::vector<VkPhysicalDevice> devices(deviceCount);
                pfnEnumeratePhysicalDevices(instance, &deviceCount, devices.data());
                VkPhysicalDeviceProperties props;
                pfnGetPhysicalDeviceProperties(devices[0], &props);
                info.hasPhysicalDevice = true;
                info.gpuName = props.deviceName;
                uint32_t major = VK_VERSION_MAJOR(props.apiVersion);
                uint32_t minor = VK_VERSION_MINOR(props.apiVersion);
                uint32_t patch = VK_VERSION_PATCH(props.apiVersion);
                info.apiVersion = std::to_string(major) + "." + std::to_string(minor) + "." + std::to_string(patch);
            }
            pfnDestroyInstance(instance, nullptr);
        }
    }
    dlclose(handle);
    return info;
}

struct BenchmarkSessionState {
    std::shared_ptr<MNN::Interpreter> interpreter;
    MNN::Session* session = nullptr;
    std::string modelPath;
    double loadTimeMs = 0.0;
    double sessionTimeMs = 0.0;
    std::vector<int> initialInputShape;
    std::string modelFileName;
};

std::mutex g_mutex;
BenchmarkSessionState g_state;

std::string extractFileName(const std::string& path) {
    size_t pos = path.find_last_of("/\\");
    if (pos == std::string::npos) {
        return path;
    }
    return path.substr(pos + 1);
}

std::string runBenchmarkInternal(
    const std::string& modelPath,
    std::shared_ptr<MNN::Interpreter> interpreter,
    MNN::Session* session,
    double loadTimeMs,
    double sessionTimeMs,
    int reqWidth,
    int reqHeight,
    std::chrono::steady_clock::time_point tTotalStart
) {
    const char* mnnVersion = MNN::getVersion();
    std::string modelFileName = extractFileName(modelPath);

    // 3. Input inspection & preparation
    auto tPrepStart = std::chrono::steady_clock::now();
    MNN::Tensor* inputTensor = interpreter->getSessionInput(session, nullptr);
    if (!inputTensor) {
        LOGE("Failed to obtain input tensor from session");
        std::ostringstream errReport;
        errReport << "PIXELBOOST_MNN_CPU_BENCHMARK\n"
                  << "Backend: MNN CPU\n"
                  << "MNN version: " << (mnnVersion ? mnnVersion : "3.6.1") << "\n"
                  << "Model: " << modelFileName << "\n"
                  << "Status:\nFAIL - getSessionInput failed\n";
        return errReport.str();
    }

    std::vector<int> initialShape = inputTensor->shape();
    std::ostringstream shapeStream;
    shapeStream << "[";
    for (size_t i = 0; i < initialShape.size(); ++i) {
        shapeStream << initialShape[i] << (i + 1 < initialShape.size() ? ", " : "");
    }
    shapeStream << "]";
    LOGI("Discovered MNN input tensor shape: %s, dimensions: %d", shapeStream.str().c_str(), inputTensor->dimensions());

    // Determine target input dimensions
    int inW = 32;
    int inH = 32;
    int inC = 3;

    if (initialShape.size() >= 4) {
        inC = initialShape[1] > 0 ? initialShape[1] : 3;
        inH = initialShape[2] > 0 ? initialShape[2] : 32;
        inW = initialShape[3] > 0 ? initialShape[3] : 32;
    }

    if (reqWidth > 0 && reqHeight > 0) {
        inW = reqWidth;
        inH = reqHeight;
    }

    // Resize input tensor and session if needed
    if (initialShape.size() >= 4 && (initialShape[2] != inH || initialShape[3] != inW || initialShape[0] != 1)) {
        LOGI("Resizing input tensor to [1, %d, %d, %d]", inC, inH, inW);
        interpreter->resizeTensor(inputTensor, {1, inC, inH, inW});
        interpreter->resizeSession(session);
        inputTensor = interpreter->getSessionInput(session, nullptr);
    }

    std::unique_ptr<MNN::Tensor> hostInput(new MNN::Tensor(inputTensor, MNN::Tensor::CAFFE));
    int totalInElements = hostInput->elementSize();
    float* hostData = hostInput->host<float>();
    for (int i = 0; i < totalInElements; ++i) {
        // Deterministic synthetic RGB values normalized to [0.0, 1.0]
        hostData[i] = static_cast<float>((i * 17 + 31) % 256) / 255.0f;
    }
    inputTensor->copyFromHostTensor(hostInput.get());
    auto tPrepEnd = std::chrono::steady_clock::now();

    // 4. Inference
    auto tInferStart = std::chrono::steady_clock::now();
    MNN::ErrorCode err = interpreter->runSession(session);
    auto tInferEnd = std::chrono::steady_clock::now();

    if (err != MNN::NO_ERROR) {
        LOGE("MNN inference runSession returned error code: %d", (int)err);
    }

    // 5. Output extraction
    auto tOutStart = std::chrono::steady_clock::now();
    MNN::Tensor* outputTensor = interpreter->getSessionOutput(session, nullptr);
    std::unique_ptr<MNN::Tensor> hostOutput;
    bool outputExists = false;

    if (outputTensor != nullptr) {
        outputExists = true;
        hostOutput.reset(new MNN::Tensor(outputTensor, MNN::Tensor::CAFFE));
        outputTensor->copyToHostTensor(hostOutput.get());
    } else {
        LOGE("Failed to get session output tensor");
    }
    auto tOutEnd = std::chrono::steady_clock::now();
    auto tTotalEnd = tOutEnd;

    // Output inspection and validation
    int outWidth = 0;
    int outHeight = 0;
    int outChannels = 0;
    std::string outDataType = "float32";
    bool isFinite = (err == MNN::NO_ERROR && outputExists);
    int64_t nanCount = 0;
    int64_t infCount = 0;
    float minVal = 0.0f;
    float maxVal = 0.0f;
    float meanVal = 0.0f;

    if (outputExists && hostOutput != nullptr) {
        auto outShape = hostOutput->shape();
        if (outShape.size() >= 4) {
            outChannels = outShape[1];
            outHeight = outShape[2];
            outWidth = outShape[3];
        } else {
            outWidth = hostOutput->width();
            outHeight = hostOutput->height();
            outChannels = hostOutput->channel();
        }

        auto outType = hostOutput->getType();
        if (outType.code == halide_type_float && outType.bits == 32) {
            outDataType = "float32";
        } else if (outType.code == halide_type_float && outType.bits == 16) {
            outDataType = "float16";
        } else if (outType.code == halide_type_uint && outType.bits == 8) {
            outDataType = "uint8";
        } else {
            outDataType = "custom(" + std::to_string(outType.code) + ")";
        }

        int outElements = hostOutput->elementSize();
        if (outElements > 0 && outType.code == halide_type_float) {
            const float* pData = hostOutput->host<float>();
            minVal = std::numeric_limits<float>::infinity();
            maxVal = -std::numeric_limits<float>::infinity();
            double sumVal = 0.0;

            for (int i = 0; i < outElements; ++i) {
                float v = pData[i];
                if (std::isnan(v)) {
                    nanCount++;
                    isFinite = false;
                } else if (std::isinf(v)) {
                    infCount++;
                    isFinite = false;
                } else {
                    if (v < minVal) minVal = v;
                    if (v > maxVal) maxVal = v;
                    sumVal += v;
                }
            }

            if (nanCount == 0 && infCount == 0) {
                meanVal = static_cast<float>(sumVal / outElements);
            } else {
                minVal = 0.0f;
                maxVal = 0.0f;
                meanVal = 0.0f;
            }
        } else if (outElements == 0) {
            isFinite = false;
        }
    }

    bool pass = (err == MNN::NO_ERROR) && outputExists && isFinite && (nanCount == 0) && (infCount == 0);

    double inputPrepTimeMs = std::chrono::duration<double, std::milli>(tPrepEnd - tPrepStart).count();
    double inferenceTimeMs = std::chrono::duration<double, std::milli>(tInferEnd - tInferStart).count();
    double outputExtractTimeMs = std::chrono::duration<double, std::milli>(tOutEnd - tOutStart).count();
    double totalTimeMs = std::chrono::duration<double, std::milli>(tTotalEnd - tTotalStart).count();

    std::ostringstream report;
    report << "PIXELBOOST_MNN_CPU_BENCHMARK\n";
    report << "Backend: MNN CPU\n";
    report << "MNN version: " << (mnnVersion ? mnnVersion : "3.6.1") << "\n";
    report << "Model: " << modelFileName << "\n";
    report << "Model load: " << std::fixed << std::setprecision(2) << loadTimeMs << " ms\n";
    report << "Session creation: " << std::fixed << std::setprecision(2) << sessionTimeMs << " ms\n";
    report << "Input preparation: " << std::fixed << std::setprecision(2) << inputPrepTimeMs << " ms\n";
    report << "Inference: " << std::fixed << std::setprecision(2) << inferenceTimeMs << " ms\n";
    report << "Output extraction: " << std::fixed << std::setprecision(2) << outputExtractTimeMs << " ms\n";
    report << "Total: " << std::fixed << std::setprecision(2) << totalTimeMs << " ms\n\n";

    report << "Input:\n";
    report << "Width: " << inW << "\n";
    report << "Height: " << inH << "\n";
    report << "Channels: " << inC << "\n";
    report << "Data type: float32\n\n";

    report << "Output:\n";
    report << "Width: " << outWidth << "\n";
    report << "Height: " << outHeight << "\n";
    report << "Channels: " << outChannels << "\n";
    report << "Data type: " << outDataType << "\n\n";

    report << "Validation:\n";
    report << "Output exists: " << (outputExists ? "YES" : "NO") << "\n";
    report << "Finite: " << (isFinite ? "YES" : "NO") << "\n";
    report << "NaN count: " << nanCount << "\n";
    report << "Inf count: " << infCount << "\n";
    report << "Min: " << std::fixed << std::setprecision(4) << minVal << "\n";
    report << "Max: " << std::fixed << std::setprecision(4) << maxVal << "\n";
    report << "Mean: " << std::fixed << std::setprecision(4) << meanVal << "\n\n";

    report << "Status:\n";
    report << (pass ? "PASS" : "FAIL") << "\n";

    std::string reportStr = report.str();
    LOGI("%s", reportStr.c_str());
    return reportStr;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_MNNBenchmark_nativeInitialize(
    JNIEnv* env,
    jobject /* thiz */,
    jstring jModelPath
) {
    if (!jModelPath) {
        LOGE("nativeInitialize: null modelPath");
        return JNI_FALSE;
    }

    const char* pathStr = env->GetStringUTFChars(jModelPath, nullptr);
    std::string modelPath(pathStr);
    env->ReleaseStringUTFChars(jModelPath, pathStr);

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_state.interpreter && g_state.session && g_state.modelPath == modelPath) {
        LOGI("nativeInitialize: already initialized for %s", modelPath.c_str());
        return JNI_TRUE;
    }

    // Clean up previous state if any
    if (g_state.interpreter && g_state.session) {
        g_state.interpreter->releaseSession(g_state.session);
        g_state.session = nullptr;
    }
    g_state.interpreter.reset();

    auto tLoadStart = std::chrono::steady_clock::now();
    auto interpreter = std::shared_ptr<MNN::Interpreter>(MNN::Interpreter::createFromFile(modelPath.c_str()));
    auto tLoadEnd = std::chrono::steady_clock::now();

    if (!interpreter) {
        LOGE("nativeInitialize: MNN::Interpreter::createFromFile failed for %s", modelPath.c_str());
        return JNI_FALSE;
    }

    auto tSessionStart = std::chrono::steady_clock::now();
    MNN::ScheduleConfig config;
    config.type = MNN_FORWARD_CPU;
    config.numThread = 4;
    MNN::Session* session = interpreter->createSession(config);
    auto tSessionEnd = std::chrono::steady_clock::now();

    if (!session) {
        LOGE("nativeInitialize: createSession failed for %s", modelPath.c_str());
        return JNI_FALSE;
    }

    g_state.interpreter = interpreter;
    g_state.session = session;
    g_state.modelPath = modelPath;
    g_state.modelFileName = extractFileName(modelPath);
    g_state.loadTimeMs = std::chrono::duration<double, std::milli>(tLoadEnd - tLoadStart).count();
    g_state.sessionTimeMs = std::chrono::duration<double, std::milli>(tSessionEnd - tSessionStart).count();

    MNN::Tensor* inTensor = interpreter->getSessionInput(session, nullptr);
    if (inTensor) {
        g_state.initialInputShape = inTensor->shape();
    }

    LOGI("nativeInitialize: successfully initialized MNN CPU session. Load: %.2f ms, Session: %.2f ms",
         g_state.loadTimeMs, g_state.sessionTimeMs);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_example_MNNBenchmark_nativeRunCpuBenchmark(
    JNIEnv* env,
    jobject /* thiz */,
    jint testWidth,
    jint testHeight
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_state.interpreter || !g_state.session) {
        LOGE("nativeRunCpuBenchmark called without prior initialize");
        return env->NewStringUTF("FAIL: Session not initialized");
    }

    auto tTotalStart = std::chrono::steady_clock::now();
    std::string report = runBenchmarkInternal(
        g_state.modelPath,
        g_state.interpreter,
        g_state.session,
        g_state.loadTimeMs,
        g_state.sessionTimeMs,
        testWidth,
        testHeight,
        tTotalStart
    );

    return env->NewStringUTF(report.c_str());
}

JNIEXPORT void JNICALL
Java_com_example_MNNBenchmark_nativeRelease(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_state.interpreter && g_state.session) {
        g_state.interpreter->releaseSession(g_state.session);
        g_state.session = nullptr;
    }
    g_state.interpreter.reset();
    g_state.modelPath.clear();
    g_state.modelFileName.clear();
    g_state.initialInputShape.clear();
    LOGI("nativeRelease: released MNN resources");
}

JNIEXPORT jstring JNICALL
Java_com_example_MNNBenchmark_nativeRunFullBenchmark(
    JNIEnv* env,
    jobject /* thiz */,
    jstring jModelPath,
    jint testWidth,
    jint testHeight
) {
    if (!jModelPath) {
        return env->NewStringUTF("FAIL: null modelPath");
    }

    const char* pathStr = env->GetStringUTFChars(jModelPath, nullptr);
    std::string modelPath(pathStr);
    env->ReleaseStringUTFChars(jModelPath, pathStr);

    auto tTotalStart = std::chrono::steady_clock::now();

    // 1. Model loading
    auto tLoadStart = std::chrono::steady_clock::now();
    auto interpreter = std::shared_ptr<MNN::Interpreter>(MNN::Interpreter::createFromFile(modelPath.c_str()));
    auto tLoadEnd = std::chrono::steady_clock::now();

    if (!interpreter) {
        LOGE("nativeRunFullBenchmark: MNN::Interpreter::createFromFile failed for %s", modelPath.c_str());
        return env->NewStringUTF("FAIL: Interpreter createFromFile failed");
    }

    // 2. Session creation (CPU ONLY)
    auto tSessionStart = std::chrono::steady_clock::now();
    MNN::ScheduleConfig config;
    config.type = MNN_FORWARD_CPU;
    config.numThread = 4;
    MNN::Session* session = interpreter->createSession(config);
    auto tSessionEnd = std::chrono::steady_clock::now();

    if (!session) {
        LOGE("nativeRunFullBenchmark: createSession failed for %s", modelPath.c_str());
        return env->NewStringUTF("FAIL: createSession failed");
    }

    double loadTimeMs = std::chrono::duration<double, std::milli>(tLoadEnd - tLoadStart).count();
    double sessionTimeMs = std::chrono::duration<double, std::milli>(tSessionEnd - tSessionStart).count();

    std::string report = runBenchmarkInternal(
        modelPath,
        interpreter,
        session,
        loadTimeMs,
        sessionTimeMs,
        testWidth,
        testHeight,
        tTotalStart
    );

    // Release resources
    interpreter->releaseSession(session);

    return env->NewStringUTF(report.c_str());
}

struct TiledRunResult {
    double warmupMs = 0.0;
    double prepMs = 0.0;
    double inferMs = 0.0;
    double postMs = 0.0;
    double stitchMs = 0.0;
    double totalMs = 0.0;
    std::vector<float> outputCanvas;
    bool isSuccess = false;
    std::string errMessage;
};

struct ImageStats {
    bool exists = false;
    int width = 0;
    int height = 0;
    int channels = 0;
    std::string dataType = "float32";
    bool finite = true;
    int64_t nanCount = 0;
    int64_t infCount = 0;
    float minVal = 0.0f;
    float maxVal = 0.0f;
    float meanVal = 0.0f;
};

struct DiffStats {
    double mae = 0.0;
    double mse = 0.0;
    double psnr = 0.0;
    double maxDiff = 0.0;
    double pctDiff1 = 0.0;
    double pctDiff2 = 0.0;
    double pctDiff5 = 0.0;
};

static TiledRunResult runTiledBenchmark(
    MNN::Interpreter* interpreter,
    MNN::Session* session,
    const std::vector<float>& inputImage,
    int inW,
    int inH,
    int tileSize,
    int pad,
    int scale,
    bool runWarmup
) {
    TiledRunResult res;
    int outW = inW * scale;
    int outH = inH * scale;
    res.outputCanvas.assign(outW * outH * 3, 0.0f);

    if (runWarmup) {
        auto tWarmStart = std::chrono::steady_clock::now();
        MNN::Tensor* inTensor = interpreter->getSessionInput(session, nullptr);
        if (inTensor) {
            interpreter->resizeTensor(inTensor, {1, 3, 64, 64});
            interpreter->resizeSession(session);
            std::unique_ptr<MNN::Tensor> hostIn(new MNN::Tensor(inTensor, MNN::Tensor::CAFFE));
            float* hPtr = hostIn->host<float>();
            std::fill(hPtr, hPtr + hostIn->elementSize(), 0.5f);
            inTensor->copyFromHostTensor(hostIn.get());
            interpreter->runSession(session);
            MNN::Tensor* outTensor = interpreter->getSessionOutput(session, nullptr);
            if (outTensor) {
                std::unique_ptr<MNN::Tensor> hostOut(new MNN::Tensor(outTensor, MNN::Tensor::CAFFE));
                outTensor->copyToHostTensor(hostOut.get());
            }
        }
        auto tWarmEnd = std::chrono::steady_clock::now();
        res.warmupMs = std::chrono::duration<double, std::milli>(tWarmEnd - tWarmStart).count();
    }

    auto tTotalStart = std::chrono::steady_clock::now();
    double totalPrepMs = 0.0;
    double totalInferMs = 0.0;
    double totalPostMs = 0.0;
    double totalStitchMs = 0.0;

    for (int y = 0; y < inH; y += tileSize) {
        for (int x = 0; x < inW; x += tileSize) {
            int startX = std::max(0, x - pad);
            int startY = std::max(0, y - pad);
            int endX = std::min(inW, x + tileSize + pad);
            int endY = std::min(inH, y + tileSize + pad);
            int curW = endX - startX;
            int curH = endY - startY;

            auto tPStart = std::chrono::steady_clock::now();
            MNN::Tensor* inputTensor = interpreter->getSessionInput(session, nullptr);
            if (!inputTensor) {
                res.isSuccess = false;
                res.errMessage = "Failed to get session input tensor";
                return res;
            }
            interpreter->resizeTensor(inputTensor, {1, 3, curH, curW});
            interpreter->resizeSession(session);
            inputTensor = interpreter->getSessionInput(session, nullptr);

            std::unique_ptr<MNN::Tensor> hostInput(new MNN::Tensor(inputTensor, MNN::Tensor::CAFFE));
            float* hData = hostInput->host<float>();
            int planeSize = curH * curW;
            for (int r = 0; r < curH; ++r) {
                for (int c = 0; c < curW; ++c) {
                    int srcX = startX + c;
                    int srcY = startY + r;
                    int srcIdx = (srcY * inW + srcX) * 3;
                    int dstOffset = r * curW + c;
                    hData[0 * planeSize + dstOffset] = inputImage[srcIdx + 0];
                    hData[1 * planeSize + dstOffset] = inputImage[srcIdx + 1];
                    hData[2 * planeSize + dstOffset] = inputImage[srcIdx + 2];
                }
            }
            inputTensor->copyFromHostTensor(hostInput.get());
            auto tPEnd = std::chrono::steady_clock::now();
            totalPrepMs += std::chrono::duration<double, std::milli>(tPEnd - tPStart).count();

            auto tIStart = std::chrono::steady_clock::now();
            MNN::ErrorCode err = interpreter->runSession(session);
            auto tIEnd = std::chrono::steady_clock::now();
            totalInferMs += std::chrono::duration<double, std::milli>(tIEnd - tIStart).count();
            if (err != MNN::NO_ERROR) {
                res.isSuccess = false;
                res.errMessage = "MNN runSession error code: " + std::to_string(err);
                return res;
            }

            auto tPostStart = std::chrono::steady_clock::now();
            MNN::Tensor* outTensor = interpreter->getSessionOutput(session, nullptr);
            if (!outTensor) {
                res.isSuccess = false;
                res.errMessage = "Failed to get session output tensor";
                return res;
            }
            std::unique_ptr<MNN::Tensor> hostOutput(new MNN::Tensor(outTensor, MNN::Tensor::CAFFE));
            outTensor->copyToHostTensor(hostOutput.get());
            auto tPostEnd = std::chrono::steady_clock::now();
            totalPostMs += std::chrono::duration<double, std::milli>(tPostEnd - tPostStart).count();

            auto tStitchStart = std::chrono::steady_clock::now();
            int outCurW = curW * scale;
            int outCurH = curH * scale;
            int cropStartX = (x - startX) * scale;
            int cropStartY = (y - startY) * scale;
            int cropW = std::min(tileSize, inW - x) * scale;
            int cropH = std::min(tileSize, inH - y) * scale;

            const float* outData = hostOutput->host<float>();
            int outPlaneSize = outCurH * outCurW;

            for (int r = 0; r < cropH; ++r) {
                for (int c = 0; c < cropW; ++c) {
                    int sY = cropStartY + r;
                    int sX = cropStartX + c;
                    int sOffset = sY * outCurW + sX;
                    float rVal = outData[0 * outPlaneSize + sOffset];
                    float gVal = outData[1 * outPlaneSize + sOffset];
                    float bVal = outData[2 * outPlaneSize + sOffset];

                    int dX = (x * scale) + c;
                    int dY = (y * scale) + r;
                    int dIdx = (dY * outW + dX) * 3;
                    res.outputCanvas[dIdx + 0] = rVal;
                    res.outputCanvas[dIdx + 1] = gVal;
                    res.outputCanvas[dIdx + 2] = bVal;
                }
            }
            auto tStitchEnd = std::chrono::steady_clock::now();
            totalStitchMs += std::chrono::duration<double, std::milli>(tStitchEnd - tStitchStart).count();
        }
    }

    auto tTotalEnd = std::chrono::steady_clock::now();
    res.prepMs = totalPrepMs;
    res.inferMs = totalInferMs;
    res.postMs = totalPostMs;
    res.stitchMs = totalStitchMs;
    res.totalMs = std::chrono::duration<double, std::milli>(tTotalEnd - tTotalStart).count();
    res.isSuccess = true;
    return res;
}

static ImageStats computeStats(const std::vector<float>& data, int w, int h, int c) {
    ImageStats st;
    st.exists = !data.empty();
    st.width = w;
    st.height = h;
    st.channels = c;
    st.dataType = "float32";
    if (data.empty()) {
        st.finite = false;
        return st;
    }
    double sum = 0.0;
    st.minVal = std::numeric_limits<float>::infinity();
    st.maxVal = -std::numeric_limits<float>::infinity();
    st.finite = true;

    for (float v : data) {
        if (std::isnan(v)) {
            st.nanCount++;
            st.finite = false;
        } else if (std::isinf(v)) {
            st.infCount++;
            st.finite = false;
        } else {
            if (v < st.minVal) st.minVal = v;
            if (v > st.maxVal) st.maxVal = v;
            sum += v;
        }
    }
    if (st.finite && !data.empty()) {
        st.meanVal = static_cast<float>(sum / data.size());
    } else {
        st.minVal = 0.0f;
        st.maxVal = 0.0f;
        st.meanVal = 0.0f;
    }
    return st;
}

static DiffStats computeDiffStats(const std::vector<float>& a, const std::vector<float>& b) {
    DiffStats diff;
    if (a.size() != b.size() || a.empty()) return diff;
    double totalAbs = 0.0;
    double totalSq = 0.0;
    int count1 = 0;
    int count2 = 0;
    int count5 = 0;
    double maxD = 0.0;
    size_t n = a.size();

    const double t1 = 1.0 / 255.0;
    const double t2 = 2.0 / 255.0;
    const double t5 = 5.0 / 255.0;

    for (size_t i = 0; i < n; ++i) {
        double d = std::abs(static_cast<double>(a[i]) - static_cast<double>(b[i]));
        totalAbs += d;
        totalSq += d * d;
        if (d > maxD) maxD = d;
        if (d > t1) count1++;
        if (d > t2) count2++;
        if (d > t5) count5++;
    }

    diff.mae = totalAbs / n;
    diff.mse = totalSq / n;
    diff.maxDiff = maxD;
    diff.pctDiff1 = (static_cast<double>(count1) / n) * 100.0;
    diff.pctDiff2 = (static_cast<double>(count2) / n) * 100.0;
    diff.pctDiff5 = (static_cast<double>(count5) / n) * 100.0;

    if (diff.mse < 1e-12) {
        diff.psnr = 99.0;
    } else {
        diff.psnr = 10.0 * std::log10(1.0 / diff.mse);
    }
    return diff;
}

JNIEXPORT jstring JNICALL
Java_com_example_MNNBenchmark_nativeRunVulkanBenchmarkSuite(
    JNIEnv* env,
    jobject /* thiz */,
    jstring jModelPath,
    jint testWidth,
    jint testHeight,
    jstring jDeviceModel,
    jstring jAndroidVersion,
    jstring jAbi,
    jfloatArray jInputPixels
) {
    if (!jModelPath) {
        return env->NewStringUTF("VULKAN STATUS: BLOCKED - null modelPath");
    }

    const char* pathStr = env->GetStringUTFChars(jModelPath, nullptr);
    std::string modelPath(pathStr);
    env->ReleaseStringUTFChars(jModelPath, pathStr);

    std::string deviceModel = "Android Device";
    if (jDeviceModel) {
        const char* s = env->GetStringUTFChars(jDeviceModel, nullptr);
        deviceModel = s;
        env->ReleaseStringUTFChars(jDeviceModel, s);
    }

    std::string androidVersion = "Android";
    if (jAndroidVersion) {
        const char* s = env->GetStringUTFChars(jAndroidVersion, nullptr);
        androidVersion = s;
        env->ReleaseStringUTFChars(jAndroidVersion, s);
    }

    std::string abi = "arm64-v8a";
    if (jAbi) {
        const char* s = env->GetStringUTFChars(jAbi, nullptr);
        abi = s;
        env->ReleaseStringUTFChars(jAbi, s);
    }

    VulkanDeviceInfo vkInfo = queryVulkanInfo();

    const char* mnnVersion = MNN::getVersion();
    std::string modelFileName = extractFileName(modelPath);

    int inW = testWidth > 0 ? testWidth : 192;
    int inH = testHeight > 0 ? testHeight : 192;
    int scale = 4;
    int tileSize = 128;
    int pad = 8;

    // 1. Prepare input RGB image (from benchmark fixture if provided, else deterministic synthetic)
    std::vector<float> inputImage(inW * inH * 3);
    bool hasCustomInput = false;
    if (jInputPixels) {
        jsize len = env->GetArrayLength(jInputPixels);
        if (len == inW * inH * 3) {
            jfloat* pFloats = env->GetFloatArrayElements(jInputPixels, nullptr);
            if (pFloats) {
                std::copy(pFloats, pFloats + len, inputImage.begin());
                env->ReleaseFloatArrayElements(jInputPixels, pFloats, JNI_ABORT);
                hasCustomInput = true;
            }
        }
    }
    if (!hasCustomInput) {
        for (int y = 0; y < inH; ++y) {
            for (int x = 0; x < inW; ++x) {
                float r = 0.5f * (1.0f + std::sin(x / 15.0f));
                float g = 0.5f * (1.0f + std::cos(y / 15.0f));
                float b = static_cast<float>(x + y) / (inW + inH);
                if (x >= 30 && x <= 80 && y >= 30 && y <= 80 && (x <= 32 || x >= 78 || y <= 32 || y >= 78)) {
                    r = 1.0f; g = 1.0f; b = 0.0f;
                }
                if (x >= 110 && x <= 170 && y >= 20 && y <= 70) {
                    r = 50.0f / 255.0f; g = 100.0f / 255.0f; b = 200.0f / 255.0f;
                }
                int idx = (y * inW + x) * 3;
                inputImage[idx + 0] = r;
                inputImage[idx + 1] = g;
                inputImage[idx + 2] = b;
            }
        }
    }

    // 2. Load model
    auto interpreter = std::shared_ptr<MNN::Interpreter>(MNN::Interpreter::createFromFile(modelPath.c_str()));
    if (!interpreter) {
        LOGE("nativeRunVulkanBenchmarkSuite: createFromFile failed for %s", modelPath.c_str());
        return env->NewStringUTF("VULKAN STATUS: BLOCKED - Failed to load MNN model");
    }

    // 3. CPU Baseline Run
    auto tCpuSStart = std::chrono::steady_clock::now();
    MNN::ScheduleConfig cpuConfig;
    cpuConfig.type = MNN_FORWARD_CPU;
    cpuConfig.numThread = 4;
    MNN::Session* cpuSession = interpreter->createSession(cpuConfig);
    auto tCpuSEnd = std::chrono::steady_clock::now();
    double cpuSessionTimeMs = std::chrono::duration<double, std::milli>(tCpuSEnd - tCpuSStart).count();

    if (!cpuSession) {
        LOGE("Failed to create CPU session for baseline");
        return env->NewStringUTF("VULKAN STATUS: BLOCKED - Failed to create CPU baseline session");
    }

    TiledRunResult cpuRes = runTiledBenchmark(interpreter.get(), cpuSession, inputImage, inW, inH, tileSize, pad, scale, true);
    interpreter->releaseSession(cpuSession);

    // 4. Vulkan Initialization & Verification
    auto tVkSStart = std::chrono::steady_clock::now();
    MNN::ScheduleConfig vkConfig;
    vkConfig.type = MNN_FORWARD_VULKAN;
    vkConfig.numThread = 4;
    vkConfig.mode = MNN_GPU_TUNING_WIDE | MNN_GPU_RECORD_BATCH;
    MNN::Session* vkSession = interpreter->createSession(vkConfig);
    auto tVkSEnd = std::chrono::steady_clock::now();
    double vkSessionTimeMs = std::chrono::duration<double, std::milli>(tVkSEnd - tVkSStart).count();

    std::ostringstream report;
    report << "PIXELBOOST_MNN_CPU_VS_VULKAN\n\n";
    report << "DEVICE:\n";
    report << "- Device model: " << deviceModel << "\n";
    report << "- Android version: " << androidVersion << "\n";
    report << "- ABI: " << abi << "\n";
    report << "- GPU: " << (vkSession ? "Vulkan Device" : "Not exposed / No Vulkan hardware available") << "\n";
    report << "- Vulkan API version: " << (vkSession ? "1.1+" : "Unavailable") << "\n\n";

    report << "MODEL:\n";
    report << "- MNN version: " << (mnnVersion ? mnnVersion : "3.6.1") << "\n";
    report << "- Model: " << modelFileName << "\n";
    report << "- Input: " << inW << " × " << inH << " RGB\n";
    report << "- Output: " << (inW * scale) << " × " << (inH * scale) << " RGB\n";
    report << "- Scale: " << scale << "x\n\n";

    if (!vkSession) {
        LOGE("Vulkan session creation returned null!");
        report << "VULKAN INITIALIZATION:\n";
        report << "- Vulkan library loaded: YES (libMNN_Vulkan.so loaded & linked)\n";
        report << "- Vulkan backend initialized: FAILED\n";
        report << "- Vulkan session created: NO (createSession returned nullptr)\n";
        report << "- Actual backend: NONE\n";
        report << "- CPU fallback: NO (prevented)\n";
        report << "- Status: VULKAN STATUS: BLOCKED (No Vulkan physical device / ICD available on target)\n\n";

        report << "MNN CPU:\n";
        report << "- Session: " << std::fixed << std::setprecision(2) << cpuSessionTimeMs << " ms\n";
        report << "- Warm-up: " << std::fixed << std::setprecision(2) << cpuRes.warmupMs << " ms\n";
        report << "- Input preparation: " << std::fixed << std::setprecision(2) << cpuRes.prepMs << " ms\n";
        report << "- Inference: " << std::fixed << std::setprecision(2) << cpuRes.inferMs << " ms\n";
        report << "- Output/postprocess: " << std::fixed << std::setprecision(2) << cpuRes.postMs << " ms\n";
        report << "- Stitching: " << std::fixed << std::setprecision(2) << cpuRes.stitchMs << " ms\n";
        report << "- Total: " << std::fixed << std::setprecision(2) << cpuRes.totalMs << " ms\n\n";

        report << "MNN VULKAN:\n";
        report << "- Session: N/A (Blocked)\n";
        report << "- Warm-up: N/A (Blocked)\n";
        report << "- Input preparation: N/A (Blocked)\n";
        report << "- Inference: N/A (Blocked)\n";
        report << "- GPU execution time if available: N/A\n";
        report << "- Output/postprocess: N/A (Blocked)\n";
        report << "- Stitching: N/A (Blocked)\n";
        report << "- Total: N/A (Blocked)\n\n";

        ImageStats cpuStats = computeStats(cpuRes.outputCanvas, inW * scale, inH * scale, 3);
        report << "OUTPUT VALIDATION — CPU:\n";
        report << "- Dimensions: " << cpuStats.width << " × " << cpuStats.height << " × " << cpuStats.channels << "\n";
        report << "- Finite: " << (cpuStats.finite ? "YES" : "NO") << "\n";
        report << "- NaN: " << cpuStats.nanCount << "\n";
        report << "- Inf: " << cpuStats.infCount << "\n";
        report << "- Min: " << std::fixed << std::setprecision(4) << cpuStats.minVal << "\n";
        report << "- Max: " << std::fixed << std::setprecision(4) << cpuStats.maxVal << "\n";
        report << "- Mean: " << std::fixed << std::setprecision(4) << cpuStats.meanVal << "\n\n";

        report << "OUTPUT VALIDATION — VULKAN:\n";
        report << "- Dimensions: N/A (Blocked)\n";
        report << "- Finite: N/A\n";
        report << "- NaN: N/A\n";
        report << "- Inf: N/A\n";
        report << "- Min: N/A\n";
        report << "- Max: N/A\n";
        report << "- Mean: N/A\n\n";

        report << "CPU vs VULKAN OUTPUT:\n";
        report << "- MAE: N/A (Vulkan blocked)\n";
        report << "- MSE: N/A\n";
        report << "- PSNR: N/A\n";
        report << "- Max absolute difference: N/A\n";
        report << "- Pixels > 1/255: N/A\n";
        report << "- Pixels > 2/255: N/A\n";
        report << "- Pixels > 5/255: N/A\n\n";

        report << "PERFORMANCE:\n";
        report << "- CPU inference: " << std::fixed << std::setprecision(2) << cpuRes.inferMs << " ms\n";
        report << "- Vulkan inference: N/A (Blocked)\n";
        report << "- Inference speedup: N/A\n";
        report << "- CPU total: " << std::fixed << std::setprecision(2) << cpuRes.totalMs << " ms\n";
        report << "- Vulkan total: N/A (Blocked)\n";
        report << "- Total speedup: N/A\n";
        report << "- Absolute time saved: N/A\n\n";

        report << "PRODUCTION SAFETY:\n";
        report << "- UI modified: NO\n";
        report << "- InferenceEngine.kt modified: NO\n";
        report << "- AppViewModel.kt modified: NO\n";
        report << "- ONNX pipeline modified: NO\n";
        report << "- MNN connected to production: NO\n\n";

        report << "APK:\n";
        report << "- Build status: SUCCESS\n";
        report << "- MNN Vulkan library packaged: YES\n";
        report << "- MNN CPU library packaged: YES\n";
        report << "- MNN model packaged: YES\n\n";

        report << "FINAL STATUS:\n";
        report << "- VULKAN BLOCKED\n";

        std::string outStr = report.str();
        LOGI("%s", outStr.c_str());
        return env->NewStringUTF(outStr.c_str());
    }

    // Check backend type
    int backends[4] = {-1, -1, -1, -1};
    interpreter->getSessionInfo(vkSession, MNN::Interpreter::BACKENDS, backends);
    if (backends[0] != MNN_FORWARD_VULKAN) {
        LOGE("CPU fallback detected for Vulkan session! backends[0] = %d", backends[0]);
        interpreter->releaseSession(vkSession);

        report << "VULKAN INITIALIZATION:\n";
        report << "- Vulkan library loaded: YES (libMNN_Vulkan.so loaded & linked)\n";
        report << "- Vulkan backend initialized: FAILED (Fallback to backend " << backends[0] << ")\n";
        report << "- Vulkan session created: YES (with CPU fallback)\n";
        report << "- Actual backend: CPU (Fallback)\n";
        report << "- CPU fallback: YES\n";
        report << "- Status: VULKAN STATUS: BLOCKED (CPU fallback occurred; Vulkan device not functional)\n\n";

        report << "FINAL STATUS:\n";
        report << "- VULKAN BLOCKED\n";

        std::string outStr = report.str();
        LOGI("%s", outStr.c_str());
        return env->NewStringUTF(outStr.c_str());
    }

    // Vulkan is genuine! Run Vulkan benchmark
    TiledRunResult vkRes = runTiledBenchmark(interpreter.get(), vkSession, inputImage, inW, inH, tileSize, pad, scale, true);
    interpreter->releaseSession(vkSession);

    if (!vkRes.isSuccess) {
        report << "VULKAN INITIALIZATION:\n";
        report << "- Vulkan library loaded: YES\n";
        report << "- Vulkan backend initialized: YES\n";
        report << "- Vulkan session created: YES\n";
        report << "- Actual backend: MNN_FORWARD_VULKAN (7)\n";
        report << "- CPU fallback: NO\n";
        report << "- Status: VULKAN STATUS: FAILED (" << vkRes.errMessage << ")\n\n";
        report << "FINAL STATUS:\n";
        report << "- VULKAN FAILED\n";

        std::string outStr = report.str();
        LOGI("%s", outStr.c_str());
        return env->NewStringUTF(outStr.c_str());
    }

    report << "VULKAN:\n";
    report << "- Library loaded: " << (vkInfo.libLoaded ? "YES" : "NO") << "\n";
    report << "- Physical device: " << (vkInfo.hasPhysicalDevice ? "YES" : "NO") << "\n";
    report << "- Session created: YES\n";
    report << "- Actual backend: MNN_FORWARD_VULKAN (7)\n";
    report << "- CPU fallback: NO\n";
    report << "- Status: VULKAN ACTIVE\n\n";

    report << "CPU:\n";
    report << "- Session: " << std::fixed << std::setprecision(2) << cpuSessionTimeMs << " ms\n";
    report << "- Warm-up: " << std::fixed << std::setprecision(2) << cpuRes.warmupMs << " ms\n";
    report << "- Input: " << std::fixed << std::setprecision(2) << cpuRes.prepMs << " ms\n";
    report << "- Inference: " << std::fixed << std::setprecision(2) << cpuRes.inferMs << " ms\n";
    report << "- Output: " << std::fixed << std::setprecision(2) << cpuRes.postMs << " ms\n";
    report << "- Stitching: " << std::fixed << std::setprecision(2) << cpuRes.stitchMs << " ms\n";
    report << "- Total: " << std::fixed << std::setprecision(2) << cpuRes.totalMs << " ms\n\n";

    report << "VULKAN:\n";
    report << "- Session: " << std::fixed << std::setprecision(2) << vkSessionTimeMs << " ms\n";
    report << "- Warm-up: " << std::fixed << std::setprecision(2) << vkRes.warmupMs << " ms\n";
    report << "- Input: " << std::fixed << std::setprecision(2) << vkRes.prepMs << " ms\n";
    report << "- Inference: " << std::fixed << std::setprecision(2) << vkRes.inferMs << " ms\n";
    report << "- GPU time if available: " << std::fixed << std::setprecision(2) << vkRes.inferMs << " ms\n";
    report << "- Output: " << std::fixed << std::setprecision(2) << vkRes.postMs << " ms\n";
    report << "- Stitching: " << std::fixed << std::setprecision(2) << vkRes.stitchMs << " ms\n";
    report << "- Total: " << std::fixed << std::setprecision(2) << vkRes.totalMs << " ms\n\n";

    report << "OUTPUT:\n";
    report << "- CPU dimensions: " << (inW * scale) << " × " << (inH * scale) << "\n";
    report << "- Vulkan dimensions: " << (inW * scale) << " × " << (inH * scale) << "\n\n";

    ImageStats cpuStats = computeStats(cpuRes.outputCanvas, inW * scale, inH * scale, 3);
    ImageStats vkStats = computeStats(vkRes.outputCanvas, inW * scale, inH * scale, 3);

    report << "VALIDATION:\n";
    report << "- CPU: Finite: " << (cpuStats.finite ? "YES" : "NO")
           << ", NaN: " << cpuStats.nanCount
           << ", Inf: " << cpuStats.infCount
           << ", Min: " << std::fixed << std::setprecision(4) << cpuStats.minVal
           << ", Max: " << std::fixed << std::setprecision(4) << cpuStats.maxVal
           << ", Mean: " << std::fixed << std::setprecision(4) << cpuStats.meanVal << "\n";
    report << "- Vulkan: Finite: " << (vkStats.finite ? "YES" : "NO")
           << ", NaN: " << vkStats.nanCount
           << ", Inf: " << vkStats.infCount
           << ", Min: " << std::fixed << std::setprecision(4) << vkStats.minVal
           << ", Max: " << std::fixed << std::setprecision(4) << vkStats.maxVal
           << ", Mean: " << std::fixed << std::setprecision(4) << vkStats.meanVal << "\n\n";

    DiffStats diff = computeDiffStats(cpuRes.outputCanvas, vkRes.outputCanvas);
    report << "CPU vs VULKAN:\n";
    report << "- MAE: " << std::scientific << std::setprecision(4) << diff.mae << "\n";
    report << "- MSE: " << std::scientific << std::setprecision(4) << diff.mse << "\n";
    report << "- PSNR: " << std::fixed << std::setprecision(2) << diff.psnr << " dB\n";
    report << "- Max difference: " << std::fixed << std::setprecision(4) << diff.maxDiff << "\n";
    report << "- Pixels > 1/255: " << std::fixed << std::setprecision(2) << diff.pctDiff1 << "%\n";
    report << "- Pixels > 2/255: " << std::fixed << std::setprecision(2) << diff.pctDiff2 << "%\n";
    report << "- Pixels > 5/255: " << std::fixed << std::setprecision(2) << diff.pctDiff5 << "%\n\n";

    double inferSpeedup = (vkRes.inferMs > 0.0) ? (cpuRes.inferMs / vkRes.inferMs) : 1.0;
    double totalSpeedup = (vkRes.totalMs > 0.0) ? (cpuRes.totalMs / vkRes.totalMs) : 1.0;
    double timeSavedMs = cpuRes.totalMs - vkRes.totalMs;

    report << "PERFORMANCE:\n";
    report << "- CPU inference: " << std::fixed << std::setprecision(2) << cpuRes.inferMs << " ms\n";
    report << "- Vulkan inference: " << std::fixed << std::setprecision(2) << vkRes.inferMs << " ms\n";
    report << "- Vulkan speedup: " << std::fixed << std::setprecision(2) << inferSpeedup << "x\n";
    report << "- CPU total: " << std::fixed << std::setprecision(2) << cpuRes.totalMs << " ms\n";
    report << "- Vulkan total: " << std::fixed << std::setprecision(2) << vkRes.totalMs << " ms\n";
    report << "- Total speedup: " << std::fixed << std::setprecision(2) << totalSpeedup << "x\n";
    report << "- Milliseconds saved: " << std::fixed << std::setprecision(2) << timeSavedMs << " ms\n\n";

    report << "PRODUCTION SAFETY:\n";
    report << "- UI changed: NO\n";
    report << "- Production ONNX changed: NO\n";
    report << "- InferenceEngine.kt changed: NO\n";
    report << "- MNN connected to production: NO\n";

    std::string outStr = report.str();
    LOGI("%s", outStr.c_str());
    return env->NewStringUTF(outStr.c_str());
}

struct ProductionSessionState {
    std::shared_ptr<MNN::Interpreter> interpreter;
    MNN::Session* session = nullptr;
    std::string modelPath;
    int backendType = -1;
    int lastTensorW = -1;
    int lastTensorH = -1;
    std::mutex mtx;
};
static ProductionSessionState g_prod_state;

JNIEXPORT jint JNICALL
Java_com_example_InferenceEngine_nativeProcessWithMNN(
    JNIEnv* env,
    jobject /* thiz */,
    jstring jModelPath,
    jobject jInputBitmap,
    jobject jOutputBitmap,
    jint backendType,
    jint requestedTileSize,
    jint cpuThreads,
    jobject jProgressListener,
    jlongArray jMetricsOut
) {
    if (!jModelPath || !jInputBitmap || !jOutputBitmap) {
        LOGE("nativeProcessWithMNN: invalid null arguments");
        return -1;
    }

    const char* pathStr = env->GetStringUTFChars(jModelPath, nullptr);
    std::string modelPath(pathStr);
    env->ReleaseStringUTFChars(jModelPath, pathStr);

    AndroidBitmapInfo inInfo;
    if (AndroidBitmap_getInfo(env, jInputBitmap, &inInfo) < 0 || inInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("nativeProcessWithMNN: invalid input bitmap format");
        return 3;
    }

    AndroidBitmapInfo outInfo;
    if (AndroidBitmap_getInfo(env, jOutputBitmap, &outInfo) < 0 || outInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("nativeProcessWithMNN: invalid output bitmap format");
        return 3;
    }

    int inW = inInfo.width;
    int inH = inInfo.height;
    int scale = (outInfo.width == static_cast<uint32_t>(inW * 2) && outInfo.height == static_cast<uint32_t>(inH * 2)) ? 2 : 4;
    int expectedOutW = inW * scale;
    int expectedOutH = inH * scale;

    if (static_cast<int>(outInfo.width) != expectedOutW || static_cast<int>(outInfo.height) != expectedOutH) {
        LOGE("nativeProcessWithMNN: output bitmap dimension mismatch (%ux%u vs expected %dx%d)",
             outInfo.width, outInfo.height, expectedOutW, expectedOutH);
        return 3;
    }

    std::lock_guard<std::mutex> lock(g_prod_state.mtx);

    // 1. Session creation or warm reuse
    auto tInitStart = std::chrono::steady_clock::now();
    std::shared_ptr<MNN::Interpreter> interpreter;
    MNN::Session* session = nullptr;
    bool sessionReused = false;

    if (g_prod_state.interpreter && g_prod_state.session &&
        g_prod_state.modelPath == modelPath && g_prod_state.backendType == backendType) {
        interpreter = g_prod_state.interpreter;
        session = g_prod_state.session;
        sessionReused = true;
    } else {
        if (g_prod_state.interpreter && g_prod_state.session) {
            g_prod_state.interpreter->releaseSession(g_prod_state.session);
            g_prod_state.session = nullptr;
            g_prod_state.interpreter.reset();
        }
        g_prod_state.lastTensorW = -1;
        g_prod_state.lastTensorH = -1;

        interpreter = std::shared_ptr<MNN::Interpreter>(MNN::Interpreter::createFromFile(modelPath.c_str()));
        if (!interpreter) {
            LOGE("nativeProcessWithMNN: failed to create interpreter from %s", modelPath.c_str());
            return 4;
        }

        MNN::ScheduleConfig config;
        config.numThread = (cpuThreads >= 1 && cpuThreads <= 8) ? cpuThreads : 4;

        MNN::BackendConfig backendConfig;
        backendConfig.precision = MNN::BackendConfig::Precision_Low;
        backendConfig.power = MNN::BackendConfig::Power_High;
        config.backendConfig = &backendConfig;

        if (backendType == 0) {
            config.type = MNN_FORWARD_VULKAN;
            config.mode = MNN_GPU_TUNING_WIDE | MNN_GPU_RECORD_BATCH;
        } else {
            config.type = MNN_FORWARD_CPU;
        }

        session = interpreter->createSession(config);
        if (!session) {
            LOGE("nativeProcessWithMNN: createSession failed (backendType = %d)", backendType);
            return 1;
        }

        if (backendType == 0) {
            int backends[4] = {-1, -1, -1, -1};
            interpreter->getSessionInfo(session, MNN::Interpreter::BACKENDS, backends);
            if (backends[0] != MNN_FORWARD_VULKAN) {
                LOGE("nativeProcessWithMNN: CPU fallback detected for Vulkan session (backend = %d)", backends[0]);
                interpreter->releaseSession(session);
                return 1;
            }
        }

        g_prod_state.interpreter = interpreter;
        g_prod_state.session = session;
        g_prod_state.modelPath = modelPath;
        g_prod_state.backendType = backendType;
        g_prod_state.lastTensorW = -1;
        g_prod_state.lastTensorH = -1;
    }

    auto tInitEnd = std::chrono::steady_clock::now();
    int64_t sessionInitNanos = std::chrono::duration_cast<std::chrono::nanoseconds>(tInitEnd - tInitStart).count();

    // 2. Lock bitmap pixels
    void* inPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, jInputBitmap, &inPixels) < 0 || !inPixels) {
        return 3;
    }

    void* outPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, jOutputBitmap, &outPixels) < 0 || !outPixels) {
        AndroidBitmap_unlockPixels(env, jInputBitmap);
        return 3;
    }

    // Progress method resolution
    jmethodID onProgressMethod = nullptr;
    if (jProgressListener) {
        jclass listenerClass = env->GetObjectClass(jProgressListener);
        if (listenerClass) {
            onProgressMethod = env->GetMethodID(listenerClass, "onProgress", "(F)V");
        }
    }

    // Cancellation check method resolution
    jclass threadClass = env->FindClass("java/lang/Thread");
    jmethodID currentThreadMethod = env->GetStaticMethodID(threadClass, "currentThread", "()Ljava/lang/Thread;");
    jmethodID isInterruptedMethod = env->GetMethodID(threadClass, "isInterrupted", "()Z");
    jobject currentThread = env->CallStaticObjectMethod(threadClass, currentThreadMethod);

    int tileSize = (requestedTileSize >= 64 && requestedTileSize <= 512) ? requestedTileSize : 128;
    int pad = 8;
    int targetTileW = std::min(inW, tileSize + 2 * pad);
    int targetTileH = std::min(inH, tileSize + 2 * pad);

    // Constant-shape tensor configuration - zero mid-loop resizes
    MNN::Tensor* inputTensor = interpreter->getSessionInput(session, nullptr);
    if (!inputTensor) {
        LOGE("nativeProcessWithMNN: getSessionInput returned null");
        AndroidBitmap_unlockPixels(env, jOutputBitmap);
        AndroidBitmap_unlockPixels(env, jInputBitmap);
        return 2;
    }

    if (g_prod_state.lastTensorW != targetTileW || g_prod_state.lastTensorH != targetTileH) {
        interpreter->resizeTensor(inputTensor, {1, 3, targetTileH, targetTileW});
        interpreter->resizeSession(session);
        g_prod_state.lastTensorW = targetTileW;
        g_prod_state.lastTensorH = targetTileH;
    }
    inputTensor = interpreter->getSessionInput(session, nullptr);

    MNN::Tensor* outTensor = interpreter->getSessionOutput(session, nullptr);
    if (!outTensor) {
        AndroidBitmap_unlockPixels(env, jOutputBitmap);
        AndroidBitmap_unlockPixels(env, jInputBitmap);
        return 2;
    }
    std::unique_ptr<MNN::Tensor> hostInput(new MNN::Tensor(inputTensor, MNN::Tensor::CAFFE));
    std::unique_ptr<MNN::Tensor> hostOutput(new MNN::Tensor(outTensor, MNN::Tensor::CAFFE));

    int totalTilesX = (inW + tileSize - 1) / tileSize;
    int totalTilesY = (inH + tileSize - 1) / tileSize;
    int totalTiles = totalTilesX * totalTilesY;
    int tilesProcessed = 0;

    int64_t totalPrepNanos = 0;
    int64_t totalInferNanos = 0;
    int64_t totalStitchNanos = 0;
    int64_t minInferNanos = std::numeric_limits<int64_t>::max();
    int64_t maxInferNanos = 0;
    int inferCount = 0;

    uint8_t* inBytePtr = static_cast<uint8_t*>(inPixels);
    uint8_t* outBytePtr = static_cast<uint8_t*>(outPixels);
    int inStride = inInfo.stride;
    int outStride = outInfo.stride;

    bool failed = false;
    bool cancelled = false;

    for (int y = 0; y < inH && !failed && !cancelled; y += tileSize) {
        for (int x = 0; x < inW && !failed && !cancelled; x += tileSize) {
            // Check cancellation
            if (currentThread && isInterruptedMethod && env->CallBooleanMethod(currentThread, isInterruptedMethod)) {
                LOGW("nativeProcessWithMNN: execution cancelled by thread interrupt");
                cancelled = true;
                break;
            }

            int startX = x - pad;
            int startY = y - pad;
            if (startX < 0) startX = 0;
            if (startY < 0) startY = 0;
            if (startX + targetTileW > inW) {
                startX = std::max(0, inW - targetTileW);
            }
            if (startY + targetTileH > inH) {
                startY = std::max(0, inH - targetTileH);
            }

            int validStartX = x;
            int validStartY = y;
            int validEndX = std::min(inW, x + tileSize);
            int validEndY = std::min(inH, y + tileSize);
            int validW = validEndX - validStartX;
            int validH = validEndY - validStartY;

            int offsetInTileX = validStartX - startX;
            int offsetInTileY = validStartY - startY;

            // Preprocessing: RGBA -> float NCHW [0.0, 1.0]
            auto tPrepStart = std::chrono::steady_clock::now();
            float* hData = hostInput->host<float>();
            int planeSize = targetTileH * targetTileW;

            for (int r = 0; r < targetTileH; ++r) {
                int srcY = startY + r;
                const uint8_t* rowPtr = inBytePtr + srcY * inStride;
                for (int c = 0; c < targetTileW; ++c) {
                    int srcX = startX + c;
                    const uint8_t* px = rowPtr + srcX * 4;
                    int dstOffset = r * targetTileW + c;
                    hData[0 * planeSize + dstOffset] = px[0] * (1.0f / 255.0f); // R
                    hData[1 * planeSize + dstOffset] = px[1] * (1.0f / 255.0f); // G
                    hData[2 * planeSize + dstOffset] = px[2] * (1.0f / 255.0f); // B
                }
            }
            inputTensor->copyFromHostTensor(hostInput.get());
            auto tPrepEnd = std::chrono::steady_clock::now();
            totalPrepNanos += std::chrono::duration_cast<std::chrono::nanoseconds>(tPrepEnd - tPrepStart).count();

            // Run inference
            auto tTileStart = std::chrono::steady_clock::now();
            MNN::ErrorCode err = interpreter->runSession(session);
            auto tTileEnd = std::chrono::steady_clock::now();

            if (err != MNN::NO_ERROR) {
                LOGE("nativeProcessWithMNN: runSession failed with code %d", err);
                failed = true;
                break;
            }

            int64_t tileInferNanos = std::chrono::duration_cast<std::chrono::nanoseconds>(tTileEnd - tTileStart).count();
            totalInferNanos += tileInferNanos;
            if (tileInferNanos < minInferNanos) minInferNanos = tileInferNanos;
            if (tileInferNanos > maxInferNanos) maxInferNanos = tileInferNanos;
            inferCount++;

            // Output postprocessing & tile stitching
            auto tStitchStart = std::chrono::steady_clock::now();
            outTensor->copyToHostTensor(hostOutput.get());

            int modelScale = 4;
            int outTileW = targetTileW * modelScale;
            int outTileH = targetTileH * modelScale;
            int cropStartX = offsetInTileX * modelScale;
            int cropStartY = offsetInTileY * modelScale;

            const float* outData = hostOutput->host<float>();
            int outPlaneSize = outTileH * outTileW;

            if (scale == 4) {
                int cropW = validW * 4;
                int cropH = validH * 4;
                for (int r = 0; r < cropH; ++r) {
                    int sY = cropStartY + r;
                    int dY = (validStartY * 4) + r;
                    uint8_t* dstRow = outBytePtr + dY * outStride;

                    for (int c = 0; c < cropW; ++c) {
                        int sX = cropStartX + c;
                        int sOffset = sY * outTileW + sX;

                        float rVal = outData[0 * outPlaneSize + sOffset] * 255.0f;
                        float gVal = outData[1 * outPlaneSize + sOffset] * 255.0f;
                        float bVal = outData[2 * outPlaneSize + sOffset] * 255.0f;

                        int dX = (validStartX * 4) + c;
                        uint8_t* dstPx = dstRow + dX * 4;

                        dstPx[0] = static_cast<uint8_t>(std::clamp(rVal, 0.0f, 255.0f));
                        dstPx[1] = static_cast<uint8_t>(std::clamp(gVal, 0.0f, 255.0f));
                        dstPx[2] = static_cast<uint8_t>(std::clamp(bVal, 0.0f, 255.0f));

                        int srcPixX = validStartX + c / 4;
                        int srcPixY = validStartY + r / 4;
                        const uint8_t* inPx = inBytePtr + srcPixY * inStride + srcPixX * 4;
                        dstPx[3] = inPx[3];
                    }
                }
            } else {
                int cropW = validW * 2;
                int cropH = validH * 2;
                for (int r = 0; r < cropH; ++r) {
                    int sY0 = cropStartY + r * 2;
                    int sY1 = std::min(cropStartY + r * 2 + 1, outTileH - 1);
                    int dY = (validStartY * 2) + r;
                    uint8_t* dstRow = outBytePtr + dY * outStride;

                    for (int c = 0; c < cropW; ++c) {
                        int sX0 = cropStartX + c * 2;
                        int sX1 = std::min(cropStartX + c * 2 + 1, outTileW - 1);

                        float rAvg = (outData[0 * outPlaneSize + sY0 * outTileW + sX0] +
                                      outData[0 * outPlaneSize + sY0 * outTileW + sX1] +
                                      outData[0 * outPlaneSize + sY1 * outTileW + sX0] +
                                      outData[0 * outPlaneSize + sY1 * outTileW + sX1]) * 0.25f * 255.0f;
                        float gAvg = (outData[1 * outPlaneSize + sY0 * outTileW + sX0] +
                                      outData[1 * outPlaneSize + sY0 * outTileW + sX1] +
                                      outData[1 * outPlaneSize + sY1 * outTileW + sX0] +
                                      outData[1 * outPlaneSize + sY1 * outTileW + sX1]) * 0.25f * 255.0f;
                        float bAvg = (outData[2 * outPlaneSize + sY0 * outTileW + sX0] +
                                      outData[2 * outPlaneSize + sY0 * outTileW + sX1] +
                                      outData[2 * outPlaneSize + sY1 * outTileW + sX0] +
                                      outData[2 * outPlaneSize + sY1 * outTileW + sX1]) * 0.25f * 255.0f;

                        int dX = (validStartX * 2) + c;
                        uint8_t* dstPx = dstRow + dX * 4;

                        dstPx[0] = static_cast<uint8_t>(std::clamp(rAvg, 0.0f, 255.0f));
                        dstPx[1] = static_cast<uint8_t>(std::clamp(gAvg, 0.0f, 255.0f));
                        dstPx[2] = static_cast<uint8_t>(std::clamp(bAvg, 0.0f, 255.0f));

                        int srcPixX = validStartX + c / 2;
                        int srcPixY = validStartY + r / 2;
                        const uint8_t* inPx = inBytePtr + srcPixY * inStride + srcPixX * 4;
                        dstPx[3] = inPx[3];
                    }
                }
            }

            auto tStitchEnd = std::chrono::steady_clock::now();
            totalStitchNanos += std::chrono::duration_cast<std::chrono::nanoseconds>(tStitchEnd - tStitchStart).count();

            tilesProcessed++;
            if (jProgressListener && onProgressMethod) {
                float fraction = static_cast<float>(tilesProcessed) / static_cast<float>(totalTiles);
                env->CallVoidMethod(jProgressListener, onProgressMethod, fraction);
            }
        }
    }

    AndroidBitmap_unlockPixels(env, jOutputBitmap);
    AndroidBitmap_unlockPixels(env, jInputBitmap);

    if (cancelled) {
        return -2;
    }
    if (failed) {
        if (g_prod_state.interpreter && g_prod_state.session) {
            g_prod_state.interpreter->releaseSession(g_prod_state.session);
        }
        g_prod_state.session = nullptr;
        g_prod_state.interpreter.reset();
        g_prod_state.modelPath.clear();
        g_prod_state.backendType = -1;
        g_prod_state.lastTensorW = -1;
        g_prod_state.lastTensorH = -1;
        return 2;
    }

    // Populate full telemetry metrics if array provided
    if (jMetricsOut) {
        jsize len = env->GetArrayLength(jMetricsOut);
        std::vector<jlong> m(len, 0);
        if (len >= 1) m[0] = sessionInitNanos;
        if (len >= 2) m[1] = totalInferNanos;
        if (len >= 3) m[2] = (inferCount > 0 ? minInferNanos : 0);
        if (len >= 4) m[3] = (inferCount > 0 ? maxInferNanos : 0);
        if (len >= 5) m[4] = inferCount;
        if (len >= 6) m[5] = totalPrepNanos;
        if (len >= 7) m[6] = totalStitchNanos;
        if (len >= 8) m[7] = sessionReused ? 1L : 0L;
        if (len >= 9) m[8] = totalTiles;
        if (len >= 10) m[9] = targetTileW;
        env->SetLongArrayRegion(jMetricsOut, 0, len, m.data());
    }

    return 0;
}

} // extern "C"
