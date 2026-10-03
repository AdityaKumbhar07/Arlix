#include <jni.h>

extern "C"
JNIEXPORT void JNICALL
Java_com_arlix_shadowvault_crypto_MemorySanitizer_wipeNative(JNIEnv *env, jobject /*thiz*/, jbyteArray array) {
    if (array == nullptr) return;

    const jsize len = env->GetArrayLength(array);
    if (len == 0) return;

    // Between Get...Critical and Release...Critical nothing may call back into JNI,
    // allocate, or block. Only the plain write loop below runs here.
    void *buffer = env->GetPrimitiveArrayCritical(array, nullptr);
    if (buffer == nullptr) return;

    // Volatile writes cannot be removed by the optimiser as "dead stores".
    volatile unsigned char *p = static_cast<volatile unsigned char *>(buffer);
    for (jsize i = 0; i < len; ++i) {
        p[i] = 0x00;
    }
    // Compiler barrier: the zeroing above must finish before we release the array.
    asm volatile("" : : "r"(buffer) : "memory");

    // Mode 0: if the runtime gave us a copy, copy the zeros back as well.
    env->ReleasePrimitiveArrayCritical(array, buffer, 0);
}
