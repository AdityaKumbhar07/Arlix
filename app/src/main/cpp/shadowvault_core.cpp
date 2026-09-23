#include <jni.h>
#include <cstddef>
#include <android/log.h>
#include <sys/mman.h>

#define LOG_TAG "ShadowVault_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/**
 * Volatile hardware memory sanitizer.
 * Uses a volatile pointer write loop and an inline assembly compiler barrier
 * to prevent dead-store elimination (DSE) optimization by LLVM/Clang.
 */
extern "C" void sv_mem_volatile_wipe(volatile void* v_ptr, size_t v_len)
{
    if (!v_ptr || v_len == 0) return;

    volatile unsigned char* v_byte_ptr = static_cast<volatile unsigned char*>(v_ptr);

    while (v_len--) {
        *v_byte_ptr++ = 0x00;
    }

    // Memory clobber barrier: forbids compiler reordering or optimizing away zeroing
    asm volatile("" : : "r"(v_ptr) : "memory");
}

/**
 * Securely zeroes a JVM byte array using critical primitive pinning.
 * GetPrimitiveArrayCritical disables GC thread relocation during the wipe operation.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_arlix_svault_mem_C_1mem_1NativeBridge_f_1mem_1wipeByteArray(
        JNIEnv *env,
        jobject,
        jbyteArray v_array
) {
    if (!v_array) return;

    jsize v_len = env->GetArrayLength(v_array);
    if (v_len <= 0) return;

    jbyte* v_raw_bytes = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(v_array, nullptr));
    if (v_raw_bytes) {
        sv_mem_volatile_wipe(v_raw_bytes, static_cast<size_t>(v_len));
        env->ReleasePrimitiveArrayCritical(v_array, v_raw_bytes, 0);
    }
}

/**
 * Best-effort POSIX mlock memory pinning hint.
 * NOTE: On stock non-root Android, RLIMIT_MEMLOCK limits locked pages, and JVM
 * GC may allocate copies. This provides a kernel anti-paging advisory hint where permitted.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_arlix_svault_mem_C_1mem_1NativeBridge_f_1mem_1lockByteArray(
    JNIEnv *env,
    jobject,
    jbyteArray v_array
) {
    if (!v_array) return JNI_FALSE;

    jsize v_len = env->GetArrayLength(v_array);
    if (v_len <= 0) return JNI_FALSE;

    jbyte* v_raw_bytes = env->GetByteArrayElements(v_array, nullptr);
    if (!v_raw_bytes) return JNI_FALSE;

    int v_result = mlock(v_raw_bytes, static_cast<size_t>(v_len));
    env->ReleaseByteArrayElements(v_array, v_raw_bytes, JNI_ABORT);

    return (v_result == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_arlix_svault_mem_C_1mem_1NativeBridge_f_1mem_1unlockByteArray(
    JNIEnv *env,
    jobject,
    jbyteArray v_array
) {
    if (!v_array) return JNI_FALSE;

    jsize v_len = env->GetArrayLength(v_array);
    if (v_len <= 0) return JNI_FALSE;

    jbyte* v_raw_bytes = env->GetByteArrayElements(v_array, nullptr);
    if (!v_raw_bytes) return JNI_FALSE;

    int v_result = munlock(v_raw_bytes, static_cast<size_t>(v_len));
    env->ReleaseByteArrayElements(v_array, v_raw_bytes, JNI_ABORT);

    return (v_result == 0) ? JNI_TRUE : JNI_FALSE;
}
