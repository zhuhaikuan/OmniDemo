package com.lenovo.omnidemo.traditional.fragment.online

import android.Manifest
import android.content.ContentValues
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.provider.MediaStore
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
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import com.lenovo.omnidemo.R
import com.lenovo.omnidemo.databinding.FragmentOnlineBinding
import com.lenovo.omnidemo.traditional.activity.base.BaseFragment
import com.lenovo.omnidemo.traditional.fragment.offline.OfflineFragment
import com.lenovo.omnidemo.traditional.fragment.offline.OfflineFragment.Companion
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor

class OnlineFragment : BaseFragment<FragmentOnlineBinding>() {
    private val viewModel: OnlineViewModel by viewModels()

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var mediaRecorder: MediaRecorder
    private var parcelFileDescriptor: android.os.ParcelFileDescriptor? = null

    private var recording: Recording? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private lateinit var cameraExecutor: Executor

    private var isTakePhoto = false
    private var isAudioRecording = false
    private var isVideoCapturing = false
    private var isMediaPlayerInitialized = false

    private var lastAudioPath = ""
    private var lastVideoPath = ""
    private var lastImagePath = ""
    private var responseAudioFilePath = ""

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

        binding.play.isEnabled = false
        binding.play.setOnClickListener {
            if(!isMediaPlayerInitialized) {
                responseAudioFilePath = File(requireContext().getExternalFilesDir(null), "output.wav").path
                initMediaPlayer()
                isMediaPlayerInitialized = true
            }

            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                binding.play.setImageResource(R.drawable.baseline_play_circle_24)
            } else {
                mediaPlayer.start()
                binding.play.setImageResource(R.drawable.baseline_pause_circle_24)
            }
        }

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
            lifecycleScope.launch {
                try {
                    val result = viewModel.generate("", lastImagePath, lastAudioPath, lastVideoPath)
                    if (result.isSuccessful) {
                        val file = viewModel.download(requireActivity())
                        binding.result.text = result.body()?.text.toString()
                        if (file != null) {
                            binding.play.isEnabled = true
                            Toast.makeText(requireActivity(), "Generate succeed", Toast.LENGTH_LONG).show()
                            Log.e(TAG, "Generate succeed")
                        } else {
                            Toast.makeText(requireActivity(), "Generate succeed,  but download failed!!!", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(requireActivity(), "Generate failed", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Generate failed")

                    }
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

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setDataSource(responseAudioFilePath)
            prepare() // 或者使用 prepareAsync()
            isLooping = false
        }
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
//                            viewModel.compressVideo(
//                                Environment.getExternalStoragePublicDirectory(
//                                    Environment.DIRECTORY_MOVIES).path + "/CameraX-Video/draw.mp4", context?.filesDir?.path + "/1.mp4", "512k")
                            lastVideoPath = viewModel.getRealPathFromUri(requireContext(), recordEvent.outputResults.outputUri).toString()
                            val msg = "Video capture succeeded: $lastVideoPath"
                            Toast.makeText(requireActivity(), msg, Toast.LENGTH_LONG).show()
                            Log.e(TAG, msg)
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