package com.lenovo.omnidemo.traditional.fragment.online

import android.Manifest
import android.content.ContentValues
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import com.lenovo.omnidemo.R
import com.lenovo.omnidemo.databinding.FragmentOnlineBinding
import com.lenovo.omnidemo.traditional.activity.base.BaseFragment
import com.lenovo.omnidemo.traditional.fragment.offline.OfflineFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Response
import java.io.BufferedReader
import java.io.File
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor

class OnlineFragment : BaseFragment<FragmentOnlineBinding>() {
    private val viewModel: OnlineViewModel by viewModels()

    private val mediaPlayer: MediaPlayer = MediaPlayer()
    private lateinit var mediaRecorder: MediaRecorder
    private var parcelFileDescriptor: android.os.ParcelFileDescriptor? = null

    private var recording: Recording? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private lateinit var cameraExecutor: Executor

    private var isTakePhoto = false
    private var isAudioRecording = false
    private var isVideoCapturing = false

    private var lastAudioPath = ""
    private var lastVideoPath = ""
    private var lastImagePath = ""

    private val audioFileList = ArrayList<File>()
    private var audioNum = 0
    private var audioIndex = 0

    companion object {
        const val TAG = "OnlineFragment"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        fun newInstance() = OnlineFragment()
    }

    override fun initViewBinding(): FragmentOnlineBinding {
        return FragmentOnlineBinding.inflate(layoutInflater)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun initData() {
        cameraExecutor = ContextCompat.getMainExecutor(requireContext())

        viewModel.outputPath.observe(this, Observer{ outputPath ->
            lastVideoPath = outputPath
            Log.e(TAG, "after compress videoPath = $lastVideoPath")
        })

        binding.play.isEnabled = false
        binding.play.setOnClickListener {
            playAudio()
        }

        mediaPlayer.setOnCompletionListener(object : MediaPlayer.OnCompletionListener {
            override fun onCompletion(mp: MediaPlayer?) {
                audioIndex++
                if (audioIndex >= audioFileList.size) {
                    mediaPlayer.reset()
                    binding.play.setImageResource(R.drawable.baseline_play_circle_24)
                } else {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(audioFileList[audioIndex].path)
                    mediaPlayer.prepare()
                    mediaPlayer.start()
                }
            }
        })

        binding.record.setOnClickListener {
            if (isAudioRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        binding.openCamera.setOnClickListener {
            binding.openCamera.visibility = View.GONE
            openCamera()
        }

        binding.takePhoto.setOnClickListener {
            takePicture()
        }

        binding.recordVideo.setOnClickListener {
            if (isVideoCapturing) {
                stopCaptureVideo()
            } else {
                startCaptureVideo()
            }
        }

        binding.submit.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            binding.submit.isEnabled = false
            audioNum = 0
            audioIndex = 0
            audioFileList.clear()

            var sb = StringBuilder()
            lifecycleScope.launch {
                try {
                    Log.e(TAG, "lastImagePath:  $lastImagePath, lastAudioPath: $lastAudioPath, lastVideoPath: $lastVideoPath")
                    val call = viewModel.processFormStream("hi", lastImagePath, lastAudioPath, lastVideoPath)
                    call.enqueue(object : retrofit2.Callback<ResponseBody> {
                        override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                            Log.e(TAG, "############### onResponse has been called!!!")
                            Log.e(TAG, "success = ${response.isSuccessful}")
                            if (response.isSuccessful && response.body() != null) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val responseBody = response.body()
                                    val inputStream = responseBody?.byteStream()
                                    val reader = BufferedReader(InputStreamReader(inputStream))
                                    var line: String?
                                    while (reader.readLine().also { line = it } != null) {
                                        val json = JSONObject(line!!)
                                        Log.e("VideoViewModel", "line: $line, json: $json")
                                        if (json.getString("type") == "text") {
                                            sb = sb.append(json.getString("content"))
                                            Log.e("VideoViewModel", "text: $sb")
                                            withContext (Dispatchers.Main){
                                                binding.result.text = sb.toString()
                                                binding.result.invalidate()
                                            }
                                        }
                                        if (json.getString("type") == "audio") {
                                            val audioBase64 = json.getString("content")
                                            val audioFile = Base64.decode(audioBase64, Base64.DEFAULT)
                                            val file = File(requireContext().getExternalFilesDir(null), "$audioNum.wav")
                                            val fileOutputStream = FileOutputStream(file)
                                            fileOutputStream.write(audioFile)
                                            audioFileList.add(file)
                                            audioNum++
                                        }
                                    }
                                    withContext (Dispatchers.Main){
                                        binding.play.isEnabled = true
                                        playAudio()
                                    }
                                }
                            }
                        }

                        override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                            Log.e("VideoViewModel", "请求失败：${t.message}")
                        }
                    })
                } catch (e: SocketTimeoutException) {
                    Toast.makeText(requireActivity(), "SocketTimeoutException, Please check your network", Toast.LENGTH_LONG).show()
                    Log.e(OfflineFragment.TAG, "SocketTimeoutException, Please check your network")
                    return@launch
                } catch (e: ConnectException){
                    Toast.makeText(requireActivity(), "Network ConnectException, Please check your network", Toast.LENGTH_LONG).show()
                    Log.e(OfflineFragment.TAG, "Network ConnectException, Please check your network")
                    return@launch
                } catch (e: Exception) {
                    Toast.makeText(requireActivity(), "An Exception happen: e = ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e(OfflineFragment.TAG, "An Exception happen: e = ${e.message}")
                    return@launch
                } finally {
                    binding.progressBar.visibility = View.GONE
                    binding.submit.isEnabled = true
                    lastImagePath = ""
                    lastAudioPath = ""
                    lastVideoPath = ""
                }
            }
        }

        binding.clearHistory.setOnClickListener {
            Toast.makeText(requireActivity(), "Clear history success", Toast.LENGTH_LONG).show()
        }
    }

    private fun playAudio() {
        initMediaPlayer()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            binding.play.setImageResource(R.drawable.baseline_play_circle_24)
        } else {
            mediaPlayer.start()
            binding.play.setImageResource(R.drawable.baseline_pause_circle_24)
        }
    }

    private fun initMediaPlayer() {
        audioIndex = 0
        mediaPlayer.setDataSource(audioFileList[audioIndex].path)
        mediaPlayer.prepare()
        mediaPlayer.isLooping = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startRecording() {
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.CHINA).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "recording_$name")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp3")
            put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Audio.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Recordings/") // 指定存储的相对路径
        }

        val audioUri = context?.contentResolver?.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (audioUri == null) {
            Toast.makeText(requireActivity(), "Failed to create audio file", Toast.LENGTH_SHORT).show()
            return
        }

        parcelFileDescriptor = context?.contentResolver?.openFileDescriptor(audioUri, "w")
        val fileDescriptor: FileDescriptor = parcelFileDescriptor!!.fileDescriptor

        mediaRecorder = MediaRecorder(requireContext()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(fileDescriptor)
        }

        try {
            isAudioRecording = true
            binding.record.setText(R.string.stop_audio)

            mediaRecorder.prepare()
            mediaRecorder.start()
            lastAudioPath = viewModel.getRealPathFromUri(requireContext(), audioUri).toString()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        try {
            isAudioRecording = false
            binding.record.setText(R.string.start_audio)

            mediaRecorder.apply {
                stop()
                release()
            }
            mediaRecorder = MediaRecorder()
            parcelFileDescriptor?.close()
            parcelFileDescriptor = null

            Toast.makeText(requireActivity(), "Audio recording succeed, path = $lastAudioPath", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Audio recording succeed, path = $lastAudioPath")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun takePicture() {
        val imageCapture = imageCapture ?: return
        imageCapture.takePicture(
            createImageOutputOptions(),
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        lastImagePath = viewModel.getRealPathFromUri(requireContext(), output.savedUri).toString()
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    }

                    val msg = "Photo capture succeeded: $lastImagePath"
                    Toast.makeText(requireActivity(), msg, Toast.LENGTH_LONG).show()
                    Log.e(TAG, msg)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }
            }
        )
    }

    private fun startCaptureVideo() {
        val videoCapture = this.videoCapture ?: return
        isVideoCapturing = true

        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            return
        }

        val mediaStoreOutputOptions = mediaStoreOutputOptions()
        recording = videoCapture.output
            .prepareRecording(requireContext(), mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(requireActivity(),
                        Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(cameraExecutor) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        binding.recordVideo.apply {
                            text = getString(R.string.stop_video)
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            lastVideoPath = viewModel.getRealPathFromUri(requireContext(), recordEvent.outputResults.outputUri).toString()
                            var msg = "Video capture succeeded: $lastVideoPath"
                            Toast.makeText(requireActivity(), msg, Toast.LENGTH_LONG).show()
                            viewModel.compressVideoSili(requireContext(), lastVideoPath)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: ${recordEvent.error}")
                        }
                    }
                }
            }
    }

    private fun stopCaptureVideo() {
        binding.recordVideo.apply {
            text = getString(R.string.start_video)
            isEnabled = true
        }
        isVideoCapturing = false
        recording?.stop()
        recording = null
    }

    private fun createImageOutputOptions(): ImageCapture.OutputFileOptions {
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }
        return ImageCapture.OutputFileOptions
            .Builder(requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()
    }

    private fun mediaStoreOutputOptions(): MediaStoreOutputOptions {
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.CHINA).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(requireContext().contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        return mediaStoreOutputOptions
    }

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    private fun openCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({ bindCameraUseCases() }, cameraExecutor)
    }

    private fun bindCameraUseCases() {
        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = binding.viewFinder.surfaceProvider
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()

        imageCapture = ImageCapture.Builder().build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            cameraProvider.unbindAll() // Unbind use cases before rebinding
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture ) // Bind use cases to camera
            binding.viewFinder.visibility = View.VISIBLE
        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun stopCaptureCamera() {
        if (isTakePhoto) {
            cameraProviderFuture.get().unbindAll()
        }
        binding.viewFinder.visibility = View.GONE
        binding.openCamera.setText(R.string.open_camera)
        isTakePhoto = false
    }

}