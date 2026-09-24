#include <jni.h>
#include <string.h>
#include <sys/mman.h>
#include <android/log.h>
#include <errno.h>

#define LOG_TAG "ShadowVault_Sanitizer"

extern "C"
JNIEXPORT void JNICALL
Java_com_arlix_svault_crypto_MemorySanitizer_wipeNative(JNIEnv *env, jobject thiz, jbyteArray array) {
    if (array == nullptr) return;

    jsize len = env->GetArrayLength(array);
    if (len == 0) return;

    // Pin the byte array in memory so the Kotlin GC doesn't move it during the wipe
    jbyte *buffer = env->GetByteArrayElements(array, nullptr);
    if (buffer == nullptr) return;

    // [T13/T15] Best-effort: ask the kernel to pin these pages out of ZRAM/swap.
    // On stock non-root Android, RLIMIT_MEMLOCK is often as low as 64KB for the whole process.
    // mlock() will return EPERM or ENOMEM when that limit is exceeded — this is expected and
    // non-fatal. We log it so you can see it in logcat, but we always proceed with the wipe.
    // The volatile-write wipe below is the real defense; mlock is a best-effort bonus.
    int mlock_result = mlock(buffer, static_cast<size_t>(len));
    if (mlock_result != 0) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
            "mlock() failed (errno=%d). Memory page may be swappable. "
            "This is expected on most non-root Android devices due to RLIMIT_MEMLOCK. "
            "Volatile wipe will still run.", errno);
    }

    // [T13] Silicon-level annihilation:
    // Volatile pointer write loop + asm compiler barrier prevents LLVM/Clang from
    // treating this as a dead store and optimising it away (a real, documented CVE class
    // — see OpenSSL historical memset() elision issues).
    volatile unsigned char* v_ptr = static_cast<volatile unsigned char*>(static_cast<void*>(buffer));
    for (jsize i = 0; i < len; ++i) {
        v_ptr[i] = 0x00;
    }
    // Memory clobber barrier: forbids the compiler from reordering or removing the writes above
    asm volatile("" : : "r"(buffer) : "memory");

    // Only unlock if we successfully locked (don't log spurious munlock failures)
    if (mlock_result == 0) {
        munlock(buffer, static_cast<size_t>(len));
    }

    // Release back to Kotlin JVM. The second arg '0' means: copy changes back AND free the buffer.
    env->ReleaseByteArrayElements(array, buffer, 0);
}
