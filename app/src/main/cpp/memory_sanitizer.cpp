#include <jni.h>
#include <string.h>
#include <sys/mman.h>

extern "C"
JNIEXPORT void JNICALL
Java_com_arlix_svault_crypto_MemorySanitizer_wipeNative(JNIEnv *env, jobject thiz, jbyteArray array) {
    if (array == nullptr) return;

    // Get the length of the array
    jsize len = env->GetArrayLength(array);
    if (len == 0) return;

    // Pin the byte array in memory so the Kotlin Garbage Collector doesn't move it
    jbyte *buffer = env->GetByteArrayElements(array, nullptr);
    if (buffer != nullptr) {

        // 1. Lock the memory so Android doesn't page it to the ZRAM/SSD (Threat #22)
        mlock(buffer, len);

        // 2. Surgically annihilate the bytes.
        // explicit_bzero guarantees the compiler won't optimize this away.
        explicit_bzero(buffer, len);

        // 3. Unlock the memory
        munlock(buffer, len);

        // Release the array back to Kotlin (JNI_COMMIT forces the wiped bytes to save)
        env->ReleaseByteArrayElements(array, buffer, JNI_COMMIT);
    }
}
