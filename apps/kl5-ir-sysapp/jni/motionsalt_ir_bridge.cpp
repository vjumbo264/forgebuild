#include <jni.h>
#include <android/log.h>
#include <android/hardware/ir/1.0/IConsumerIr.h>
#include <hidl/HidlSupport.h>
#include <utils/StrongPointer.h>
#include <vector>
#include <mutex>

#define LOG_TAG "MOTIONSALT-Hook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using android::sp;
using android::hardware::Return;
using android::hardware::hidl_vec;
using android::hardware::ir::V1_0::IConsumerIr;
using android::hardware::ir::V1_0::ConsumerIrFreqRange;
using android::hidl::base::V1_0::IBase;

static std::mutex gIrLock;
static sp<IConsumerIr> gConsumerIrHal = nullptr;

struct HalDeathRecipient : public android::hardware::hidl_death_recipient {
    void serviceDied(uint64_t cookie, const android::wp<IBase>& who) override {
        LOGW("Native HIDL ConsumerIr HAL service died (cookie: %llu). Clearing proxy.", (unsigned long long)cookie);
        std::lock_guard<std::mutex> lock(gIrLock);
        gConsumerIrHal = nullptr;
    }
};

static sp<HalDeathRecipient> gDeathRecipient = new HalDeathRecipient();

static sp<IConsumerIr> getHalService() {
    std::lock_guard<std::mutex> lock(gIrLock);
    if (gConsumerIrHal != nullptr) {
        return gConsumerIrHal;
    }

    try {
        gConsumerIrHal = IConsumerIr::getService("default");
        if (gConsumerIrHal != nullptr) {
            Return<bool> linked = gConsumerIrHal->linkToDeath(gDeathRecipient, 0);
            if (!linked.isOk() || !linked) {
                LOGW("Failed to link DeathRecipient to HIDL ConsumerIr service");
            }
            LOGI("Native JNI successfully connected to android.hardware.ir@1.0::IConsumerIr/default");
        } else {
            LOGW("IConsumerIr::getService(\"default\") returned null");
        }
    } catch (...) {
        LOGE("Exception occurred while obtaining HIDL IConsumerIr service");
        gConsumerIrHal = nullptr;
    }

    return gConsumerIrHal;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_motionsalt_kl5irsysapp_NativeIrBridge_nativeIsAvailable(JNIEnv *env, jobject thiz) {
    sp<IConsumerIr> hal = getHalService();
    return hal != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_motionsalt_kl5irsysapp_NativeIrBridge_nativeTransmit(JNIEnv *env, jobject thiz, jint carrier_frequency, jintArray pattern_array) {
    sp<IConsumerIr> hal = getHalService();
    if (hal == nullptr) {
        LOGE("nativeTransmit: HAL service unavailable");
        return JNI_FALSE;
    }

    jsize len = env->GetArrayLength(pattern_array);
    jint *body = env->GetIntArrayElements(pattern_array, nullptr);
    if (body == nullptr) {
        LOGE("nativeTransmit: failed to get pattern array elements");
        return JNI_FALSE;
    }

    hidl_vec<int32_t> vec;
    vec.resize(len);
    for (jsize i = 0; i < len; ++i) {
        vec[i] = body[i];
    }
    env->ReleaseIntArrayElements(pattern_array, body, JNI_ABORT);

    LOGI("nativeTransmit: sending carrier=%d Hz, %d pulses to HIDL HAL", carrier_frequency, (int)len);
    Return<bool> ret = hal->transmit(carrier_frequency, vec);
    if (!ret.isOk()) {
        LOGE("nativeTransmit: HIDL call failed: %s", ret.description().c_str());
        std::lock_guard<std::mutex> lock(gIrLock);
        gConsumerIrHal = nullptr; // Reset on IPC failure
        return JNI_FALSE;
    }

    return ret ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jintArray JNICALL
Java_com_motionsalt_kl5irsysapp_NativeIrBridge_nativeGetCarrierFrequencies(JNIEnv *env, jobject thiz) {
    sp<IConsumerIr> hal = getHalService();
    if (hal == nullptr) {
        LOGW("nativeGetCarrierFrequencies: HAL service unavailable, returning fallback 30k-60k");
        jintArray fallback = env->NewIntArray(2);
        jint ranges[2] = {30000, 60000};
        env->SetIntArrayRegion(fallback, 0, 2, ranges);
        return fallback;
    }

    std::vector<jint> flatRanges;
    Return<void> ret = hal->getCarrierFreqs([&flatRanges](bool result, const hidl_vec<ConsumerIrFreqRange>& ranges) {
        if (result) {
            for (const auto& r : ranges) {
                flatRanges.push_back(r.min);
                flatRanges.push_back(r.max);
            }
        }
    });

    if (!ret.isOk() || flatRanges.empty()) {
        LOGW("nativeGetCarrierFrequencies: HIDL failed or empty (%s), returning fallback 30k-60k", ret.description().c_str());
        flatRanges = {30000, 60000};
    }

    jsize size = (jsize)flatRanges.size();
    jintArray result = env->NewIntArray(size);
    env->SetIntArrayRegion(result, 0, size, flatRanges.data());
    return result;
}

} // extern "C"
