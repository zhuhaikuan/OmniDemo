package com.lenovo.omnidemo.traditional.fragment.online

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lenovo.omnidemo.traditional.net.ApiService
import com.lenovo.omnidemo.traditional.net.RetrofitClient
import com.lenovo.omnidemo.traditional.net.UploadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader

class OnlineViewModel : ViewModel() {
    private val apiService = RetrofitClient.createService(ApiService::class.java)

    suspend fun generate(text: String, image: String, audio: String, video: String): Response<UploadResponse> =
        withContext(Dispatchers.IO) {
            var textRequestBody: RequestBody? = null
            var imagePart: MultipartBody.Part? = null
            var audioPart: MultipartBody.Part? = null
            var videoPart: MultipartBody.Part? = null
            try {
                if (text.isNotEmpty()) {
                    textRequestBody = text.toRequestBody("text/plain".toMediaType())
                }
                if (image.isNotEmpty()) {
                    val imageFile = File(image)
                    val imageRequestBody = imageFile.asRequestBody("multipart/form-data".toMediaTypeOrNull())
                    imagePart = MultipartBody.Part.createFormData("image", imageFile.name, imageRequestBody)
                }
                if (audio.isNotEmpty()) {
                    val audioFile = File(audio)
                    val audioRequestBody = audioFile.asRequestBody("multipart/form-data".toMediaTypeOrNull())
                    audioPart = MultipartBody.Part.createFormData("audio", audioFile.name, audioRequestBody)
                }
                if (video.isNotEmpty()) {
                    val videoFile = File(video)
                    val videoRequestBody = videoFile.asRequestBody("multipart/form-data".toMediaTypeOrNull())
                    videoPart = MultipartBody.Part.createFormData("video", videoFile.name, videoRequestBody)
                }
            } catch (e: IOException) {
                Log.e("VideoViewModel", "occur an IOException")
            } catch (e: FileNotFoundException) {
                Log.e("VideoViewModel", "occur a FileNotFoundException")
            } catch (e: NullPointerException) {
                Log.e("VideoViewModel", "occur a NullPointerException")
            }

            apiService.generateWithUpload(textRequestBody, imagePart, audioPart, videoPart)
        }

    suspend fun download(context: Context): File? = withContext(Dispatchers.IO) {
        var file:  File? = null
        try {
            val responseBody = apiService.download()
            val inputStream = responseBody.byteStream()

            // 指定文件保存路径和名称
            file = File(context.getExternalFilesDir(null), "output.wav")

            // 创建文件输出流
            val fos = FileOutputStream(file)
            val buffer = ByteArray(4 * 1024)
            var read: Int

            // 读取输入流并写入文件
            while (inputStream.read(buffer).also { read = it } != -1) {
                fos.write(buffer, 0, read)
            }
            fos.flush()
            fos.close()
            inputStream.close()
        } catch (e: Exception) {
            Log.e("VideoViewModel", "下载文件出错：${e.message}")
        }
        file
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
}