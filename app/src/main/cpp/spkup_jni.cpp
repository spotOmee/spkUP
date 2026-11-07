#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define TAG "SpkUpJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

/**
 * JNI Binding: Cactus Needle Speech-to-Text inference (whistle.cact).
 */
JNIEXPORT jstring JNICALL
Java_com_spkup_app_ai_CactusWhistleAsrEngine_nativeTranscribe(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jfloatArray pcm_floats
) {
    if (model_path == nullptr || pcm_floats == nullptr) {
        return env->NewStringUTF("");
    }

    const char* c_model_path = env->GetStringUTFChars(model_path, nullptr);
    jsize sample_count = env->GetArrayLength(pcm_floats);
    jfloat* samples = env->GetFloatArrayElements(pcm_floats, nullptr);

    LOGI("CactusWhistle transcribe invoked with %d samples on model %s", sample_count, c_model_path);

    // Cactus Needle C++ engine transcription hook:
    // needle_model_t* model = needle_load(c_model_path);
    // needle_result_t result = needle_transcribe(model, samples, sample_count);
    // std::string transcription = result.text;
    // needle_free(model);

    std::string transcription = "";

    env->ReleaseFloatArrayElements(pcm_floats, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(model_path, c_model_path);

    return env->NewStringUTF(transcription.c_str());
}

/**
 * JNI Binding: SmolLM2-135M GGUF SLM inference.
 */
JNIEXPORT jstring JNICALL
Java_com_spkup_app_ai_SmolCommandRouter_nativeComplete(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jstring prompt_chatml
) {
    if (model_path == nullptr || prompt_chatml == nullptr) {
        return env->NewStringUTF("");
    }

    const char* c_model_path = env->GetStringUTFChars(model_path, nullptr);
    const char* c_prompt = env->GetStringUTFChars(prompt_chatml, nullptr);

    LOGI("SmolLM2 completion invoked on model %s", c_model_path);

    // llama.cpp / SmolLM2-135M forward pass hook:
    // Returns strictly structured JSON: {"cleaned_text": "...", "action": "NONE", "action_args": {}}
    std::string json_result = "";

    env->ReleaseStringUTFChars(prompt_chatml, c_prompt);
    env->ReleaseStringUTFChars(model_path, c_model_path);

    return env->NewStringUTF(json_result.c_str());
}

} // extern "C"
