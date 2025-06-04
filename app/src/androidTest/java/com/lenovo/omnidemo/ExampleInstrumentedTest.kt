package com.lenovo.omnidemo

import android.net.Uri
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lenovo.omnidemo.traditional.tools.ParseUriToRealPathUtil.getRealPathFromUri

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.lenovo.omnidemo", appContext.packageName)
    }

    @Test
    fun test() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = Uri.parse("content://com.android.providers.media.documents/document/image%3A1000000179")
        val filePath = getRealPathFromUri(appContext, uri)
        Log.e("filePath", filePath!!)
        if (filePath != null) {
//            val file = File(filePath)
            // 使用文件对象进行操作
        }
    }
}