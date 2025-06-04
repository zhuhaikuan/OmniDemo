package com.lenovo.omnidemo.traditional.activity.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lenovo.omnidemo.databinding.ActivityMainBinding
import com.lenovo.omnidemo.traditional.activity.audio.AudioActivity
import com.lenovo.omnidemo.traditional.activity.base.BaseActivity
import com.lenovo.omnidemo.traditional.activity.player.PlayerActivity
import com.lenovo.omnidemo.traditional.activity.video.VideoActivity
import com.lenovo.omnidemo.traditional.activity.video.VideoViewModel
import kotlinx.coroutines.launch


class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val viewModel: VideoViewModel by viewModels()

    companion object {
        const val TAG = "MainActivity"

        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 101
        private const val EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE = 102
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val PHOTO = 1
        private const val VIDEO = 2

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
    override fun initViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun initData() {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        binding.takePhoto.setOnClickListener { VideoActivity.startVideoActivity(this, PHOTO) }
        binding.startAudio.setOnClickListener { AudioActivity.startAudioActivity(this) }
        binding.startVideo.setOnClickListener { VideoActivity.startVideoActivity(this, VIDEO) }
        binding.startPlayVideo.setOnClickListener { PlayerActivity.startPlayerActivity(this) }

//        lifecycleScope.launch {
//
//            val user = viewModel.getResult("003")
//            Toast.makeText(this@MainActivity, user.name, Toast.LENGTH_SHORT).show()
//            when (val result = viewModel.getResult("002")) {
//                is NetworkResult.Success -> {
//                    Toast.makeText(this@MainActivity, result.data.name, Toast.LENGTH_SHORT).show()
//                }
//                is NetworkResult.Error -> {
//                    Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
//                }
//                is NetworkResult.Loading<*> -> {
//                    Toast.makeText(this@MainActivity, "加载中", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        } else {
//            openCamera()
        }
    }

    private fun requestRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun requestRecordVideoPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun requestExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestExternalStoragePermissionForAndroid13AndAbove()
        } else {
            requestExternalStoragePermissionForLegacy()
        }
    }

    private fun requestExternalStoragePermissionForLegacy() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE
            )
        } else {
            Toast.makeText(this, "外部存储权限已授予", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestExternalStoragePermissionForAndroid13AndAbove() {
        // 创建一个指向你要写入的文件的 URI
//        val directoryUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APictures")
        val directoryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        //这种方式是让用户选择目录
//        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
//            putExtra(Intent.EXTRA_INITIAL_INTENTS, true)
//            putExtra(Intent.EXTRA_TITLE, "选择目录")
//        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            // 设置初始 URI 为 Pictures 目录
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, directoryUri)
        }
        requestAccessExternalStoragePermissionLauncher.launch(intent)
    }

    private val requestAccessExternalStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        if (activityResult.resultCode == RESULT_OK) {
            val uri = activityResult.data?.data
            uri?.let {
                contentResolver.takePersistableUriPermission(it,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                Toast.makeText(this, "已授予对目录的访问权限", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "目录访问权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "相机权限已授予", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "用户拒绝了相机权限，可能导致相机相关功能无法正常使用", Toast.LENGTH_SHORT).show()
                }
            }
            RECORD_AUDIO_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "录制音频权限已授予", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "用户拒绝了录制音频权限，可能导致录制音频功能无法正常使用", Toast.LENGTH_SHORT).show()
                }
            }
            EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "外部存储权限已授予", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "用户拒绝了外部存储权限，可能导致某些功能无法正常使用", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_CODE_PERMISSIONS  -> {
                if (allPermissionsGranted()) {
//                    startCamera()
                } else {
                    Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

//    private fun openCamera() {
//        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//        val contentValues = ContentValues()
//        contentValues.put(MediaStore.Images.Media.TITLE, "MyPhoto")
//        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
//        photoUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
//        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
//        cameraLauncher.launch(intent)
//    }
}