package com.lenovo.omnidemo.traditional.activity.video

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lenovo.omnidemo.traditional.User
import com.lenovo.omnidemo.traditional.net.ApiService
import com.lenovo.omnidemo.traditional.net.RetrofitClient
import com.lenovo.omnidemo.traditional.net.UploadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader


/**
 * @date 2025/5/13 17:34
 * @author zhk
 */
class VideoViewModel: ViewModel() {

    private val apiService = RetrofitClient.createService(ApiService::class.java)

//    suspend fun getResult(userId: String): User {
//        return apiService.getResult(userId)
//    }

    suspend fun generate(text: String, image: String, audio: String, video: String): Response<UploadResponse> =
        withContext(Dispatchers.IO) {
            val textRequestBody = text.toRequestBody("text/plain".toMediaType())
            var imageRequestBody: RequestBody? = null
            var audioRequestBody: RequestBody? = null
            var videoRequestBody: RequestBody? = null
            try {
                if (image.isNotEmpty()) {
                    imageRequestBody = File(image).asRequestBody("image/jpeg".toMediaType())
                }
                if (audio.isNotEmpty()) {
                    audioRequestBody = File(audio).asRequestBody("audio/mpeg".toMediaType())
                }
                if (video.isNotEmpty()) {
                    videoRequestBody = File(video).asRequestBody("video/mp4".toMediaType())
                }
            } catch (e: IOException) {
                Log.e("VideoViewModel", "occur an IOException")
            } catch (e: FileNotFoundException) {
                Log.e("VideoViewModel", "occur a FileNotFoundException")
            } catch (e: NullPointerException) {
                Log.e("VideoViewModel", "occur a NullPointerException")
            }

            apiService.generate(textRequestBody, imageRequestBody, audioRequestBody, videoRequestBody)
    }

    fun compressVideo(inputPath: String, outputPath: String, bitrate: String = "1024k") {
        Log.e("VideoViewModel","inputPath = $inputPath, outputPath = $outputPath, bitrate = $bitrate")
        val cmd = arrayOf(
            "ffmpeg",
            "-i", inputPath,
            "-b:v", bitrate,
            "-vcodec", "h264",
            "-acodec", "aac",
            "-strict", "-2",
            outputPath
        )

        viewModelScope.launch {
            try {
                val processBuilder = ProcessBuilder(*cmd)
                processBuilder.redirectErrorStream(true)
                val process = processBuilder.start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.e("VideoViewModel","line: $line")
                }
                process.waitFor()


//                val process = Runtime.getRuntime().exec(cmd)
//                val exitCode = process.waitFor()
//                Log.e("VideoViewModel","exitCode: $exitCode")
//                if (exitCode == 0) {
//                    Log.e("VideoViewModel","压缩成功")
//                } else {
//                    Log.e("VideoViewModel","压缩失败")
//                }
            } catch (e: Exception) {
                Log.e("VideoViewModel", "压缩视频出错：${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun getRealPathFromUri(context: Context, uri: Uri?): String? {
        return if (uri != null) {
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            context.contentResolver
                .query(uri, projection, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                        cursor.getString(columnIndex)
                    } else {
                        null
                    }
                }
        } else {
            null
        }
    }

}