package com.lenovo.omnidemo.traditional.activity.video

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import com.lenovo.omnidemo.R
import com.lenovo.omnidemo.databinding.ActivityVideoBinding
import com.lenovo.omnidemo.traditional.activity.base.BaseActivity
import com.lenovo.omnidemo.traditional.tools.setUri
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class VideoActivity : BaseActivity<ActivityVideoBinding>() {
    private val viewModel: VideoViewModel by viewModels()

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService

    private var isTakePhoto = false
    private var isCaptureVideo = false

    companion object  {
        const val TAG = "VideoActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private var mType = 0

        fun startVideoActivity(context: Context, type: Int) {
            mType = type
            val intent = Intent(context, VideoActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun initViewBinding(): ActivityVideoBinding {
        return ActivityVideoBinding.inflate(layoutInflater)
    }

    override fun initData() {
        if (mType == 1) {
            binding.recordVideo.visibility = View.GONE
            binding.openCamera.visibility = View.VISIBLE
            binding.openCamera.setOnClickListener {
                if (isTakePhoto) {
                    takePhoto()
                } else {
                    binding.viewFinder.visibility = View.VISIBLE
                    binding.openCamera.setText(R.string.take_photo)
                    isTakePhoto = true
                    startCamera()
                }
            }
        } else if (mType == 2) {
            binding.recordVideo.visibility = View.VISIBLE
            binding.openCamera.visibility = View.GONE
            binding.recordVideo.setOnClickListener {
                if (isCaptureVideo) {
                    captureVideo()
                } else {
                    binding.viewFinder.visibility = View.VISIBLE
                    binding.openCamera.setText(R.string.start_video)
                    isCaptureVideo = true
                    startCamera()
                }
            }
        }
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.selectFile.setOnClickListener {
            stopCamera()
            Intent(Intent.ACTION_GET_CONTENT).run {
                setType("*/*")
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                activityResultContract.launch(Intent.createChooser(this, "Select a file"))
            }
        }
    }

    private var selectedUri: Uri = Uri.parse("")
    private var documentFile: DocumentFile? = null
    private val sb: StringBuilder = StringBuilder()
    private val uriList = ArrayList<Uri>()
    private val activityResultContract = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (result.data?.clipData != null) {
                sb.clear()
                val count = result.data?.clipData?.itemCount?:0
                for (i in 0 until count) {
                    selectedUri = result.data?.clipData?.getItemAt(i)?.uri ?: Uri.parse("")
                    uriList.add(selectedUri)
                    setUri(selectedUri.toString())
                    documentFile = DocumentFile.fromSingleUri(this, selectedUri)
                    sb.append(documentFile?.name).append(";\n")
                    Log.i(TAG, "file name: ${documentFile?.name}; Uri: $selectedUri")
                }
            } else {
                sb.clear()
                selectedUri = result.data?.data?: Uri.parse("")
                uriList.add(selectedUri)
                setUri(selectedUri.toString())
                Log.i(TAG, selectedUri.toString())
                documentFile = DocumentFile.fromSingleUri(this, selectedUri)
                sb.append(documentFile?.name)
            }
            binding.docName.setText(sb)
        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        val text = "test for text"

                        lifecycleScope.launch {
                            viewModel.generate(text, viewModel.getRealPathFromUri(this@VideoActivity, output.savedUri)!!, "", "")
                        }
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    }

                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        binding.recordVideo.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        val mediaStoreOutputOptions = mediaStoreOutputOptions()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@VideoActivity,
                        Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        binding.recordVideo.apply {
                            text = getString(R.string.stop_video)
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            viewModel.compressVideo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).path + "/CameraX-Video/1.mp4", filesDir.path + "/1.mp4", "512k")
                            val msg = "Video capture succeeded: ${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: ${recordEvent.error}")
                        }
                        binding.recordVideo.apply {
                            text = getString(R.string.start_video)
                            isEnabled = true
                        }
                    }
                }
            }
    }

    private fun mediaStoreOutputOptions(): MediaStoreOutputOptions {
        // create and start a new recording session
        val name =
            SimpleDateFormat(FILENAME_FORMAT, Locale.CHINA).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        return mediaStoreOutputOptions
    }

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.viewFinder.surfaceProvider
                }

            imageCapture = ImageCapture.Builder().build()

//           val imageAnalyzer = ImageAnalysis.Builder()
//               .build()
//               .also {
//                   it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
//                       Log.d(TAG, "Average luminosity: $luma")
//                   })
//               }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture )
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        if (isTakePhoto) {
            cameraProviderFuture.get().unbindAll()
        }
        binding.viewFinder.visibility = View.GONE
        binding.openCamera.setText(R.string.open_camera)
        isTakePhoto = false
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

//    private val cameraLauncher = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result ->
//        if (result.resultCode == RESULT_OK) {
//            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
//            val exif = ExifInterface(contentResolver.openInputStream(photoUri!!)!!)
//            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
//
//            // 根据方向码调整图片方向
//            val rotatedBitmap = rotateBitmap(bitmap, orientation)
//            binding.imageView.setImageBitmap(rotatedBitmap)
//        }
//    }
//
//    // 根据方向码旋转图片
//    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
//        val matrix = Matrix()
//        when (orientation) {
//            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
//            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
//            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
//            else -> return bitmap
//        }
//        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
//        if (rotatedBitmap != bitmap) {
//            bitmap.recycle()
//        }
//        return rotatedBitmap
//    }
//
//    private fun startRecordVideo() {
//        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
//
//        val contentValues = ContentValues()
//        contentValues.put(MediaStore.Images.Media.TITLE, "MyVideo")
//        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
//        photoUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
//        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
//        cameraLauncher.launch(intent)
//    }

}