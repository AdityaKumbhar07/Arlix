#include <jni.h>
#include <string.h>
#include <sys/mman.h>
#include <android/log.h>
#include <errno.h>

#define LOG_TAG "ShadowVault_Sanitizer"

extern "C"
JNIEXPORT void JNICALL
Java_com_arlix_svault_crypto_MemorySanitizer_wipeNative___3B(JNIEnv *env, jobject thiz, jbyteArray array) {
    if (array == nullptr) return;

    jsize len = env->GetArrayLength(array);
    if (len == 0) return;

    // Pin the byte array in memory using GetPrimitiveArrayCritical to guarantee a direct pointer
    // without copying the array, ensuring the actual JVM heap memory is wiped.
    //
    // CRITICAL CONSTRAINT: Nothing between GetPrimitiveArrayCritical and ReleasePrimitiveArrayCritical
    // may call back into JNI, allocate memory, or block. Doing so could suspend the GC and cause
    // a deadlock or application freeze.
    void *buffer = env->GetPrimitiveArrayCritical(array, nullptr);
    if (buffer == nullptr) return;

    int mlock_result = mlock(buffer, static_cast<size_t>(len));
    int mlock_errno = (mlock_result != 0) ? errno : 0;   // capture, don't log yet

    volatile unsigned char* v_ptr = static_cast<volatile unsigned char*>(buffer);
    for (jsize i = 0; i < len; ++i) {
        v_ptr[i] = 0x00;
    }
    asm volatile("" : : "r"(buffer) : "memory");

    if (mlock_result == 0) {
        munlock(buffer, static_cast<size_t>(len));
    }

    env->ReleasePrimitiveArrayCritical(array, buffer, 0);

    // Logging happens AFTER the critical section ends — GetPrimitiveArrayCritical's
    // contract forbids calling back into JNI/allocating/blocking while pinned.
    if (mlock_result != 0) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
            "mlock() failed (errno=%d) during wipe. Page may have been swappable for the "
            "duration of this call. Volatile wipe still completed.", mlock_errno);
    }
}
