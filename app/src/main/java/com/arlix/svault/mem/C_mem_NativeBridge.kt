package com.arlix.svault.mem

object C_mem_NativeBridge {

    init {
        System.loadLibrary("shadowvault_core")
    }

    external fun f_mem_wipeByteArray(v_array: ByteArray)

    external fun f_mem_lockByteArray(v_array: ByteArray): Boolean

    external fun f_mem_unlockByteArray(v_array: ByteArray): Boolean
}
