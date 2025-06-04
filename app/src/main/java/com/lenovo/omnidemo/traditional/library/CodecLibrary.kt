package com.lenovo.omnidemo.traditional.library

import com.sun.jna.Library
import com.sun.jna.Native


/**
 * @date 2025/5/27 15:23
 * @author zhk
 */
interface AvCodecLibrary: Library {

    companion object {
        @JvmStatic
        val INSTANCE = Native.load("avcodec", AvCodecLibrary::class.java)
    }

    fun execute(cmd: String?): Int


}