package com.lenovo.omnidemo.traditional.fragment.offline

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.lenovo.omnidemo.R
import com.lenovo.omnidemo.databinding.FragmentOfflineBinding
import com.lenovo.omnidemo.traditional.activity.base.BaseFragment
import com.lenovo.omnidemo.traditional.fragment.online.OnlineFragment
import com.lenovo.omnidemo.traditional.fragment.online.OnlineViewModel
import com.lenovo.omnidemo.traditional.tools.ParseUriToRealPathUtil
import kotlinx.coroutines.launch
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException

class OfflineFragment : BaseFragment<FragmentOfflineBinding>() {
    private lateinit var mediaPlayer: MediaPlayer
    private var audioFilePath = ""

    private var lastAudioPath = ""
    private var lastVideoPath = ""
    private var lastImagePath = ""

    private var isAudio = false
    private var isVideo = false
    private var isImage = false
    private var isMediaPlayerInitialized = false

    companion object {
        const val TAG = "OfflineFragment"
        fun newInstance() = OfflineFragment()
    }

    private val viewModel: OnlineViewModel by viewModels()

    override fun initViewBinding(): FragmentOfflineBinding {
        return FragmentOfflineBinding.inflate(layoutInflater)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun initData() {
        binding.play.isEnabled = false
        binding.play.setOnClickListener {
            if(!isMediaPlayerInitialized) {
                audioFilePath = File(requireContext().getExternalFilesDir(null), "output.wav").path
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

        binding.uploadAudio.setOnClickListener {
            isAudio = true
            isImage = false
            isVideo  = false

            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
                val audioDir = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, audioDir)
                activityResultContract.launch(Intent.createChooser(this, "Select a audio file"))
            }
        }
        binding.uploadImage.setOnClickListener {
            isAudio = false
            isImage = true
            isVideo  = false

            Intent(Intent.ACTION_OPEN_DOCUMENT).run {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                val imagesDir = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, imagesDir)
                activityResultContract.launch(Intent.createChooser(this, "Select a image file"))
            }
        }
        binding.uploadVideo.setOnClickListener {
            isAudio = false
            isImage = false
            isVideo  = true

            Intent(Intent.ACTION_OPEN_DOCUMENT).run {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
                val movieDir = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, movieDir)
                activityResultContract.launch(Intent.createChooser(this, "Select a video file"))
            }
        }
        binding.submit.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            binding.submit.isEnabled = false

            val text = binding.question.text.toString()
            lifecycleScope.launch {
                try {
                    Log.e(TAG, "text: $text, lastImagePath:  $lastImagePath, lastAudioPath: $lastAudioPath, lastVideoPath: $lastVideoPath")
                    val result = viewModel.generate(text, lastImagePath, lastAudioPath, lastVideoPath)
                    if (result.isSuccessful) {
                        val file = viewModel.download(requireActivity())
                        binding.result.text = result.body()?.text.toString()
                        binding.progressBar.visibility = View.GONE
                        if (file != null) {
                            binding.play.isEnabled = true
                            Toast.makeText(requireActivity(), "Generate succeed", Toast.LENGTH_LONG).show()
                            Log.e(TAG, "Generate succeed")
                        } else {
                            Toast.makeText(requireActivity(), "Generate succeed,  but download failed!!!", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(requireActivity(), "Generate failed", Toast.LENGTH_LONG).show()
                        Log.e(OnlineFragment.TAG, "Generate failed")
                    }
                } catch (e: SocketTimeoutException) {
                    Toast.makeText(requireActivity(), "SocketTimeoutException, Please check your network", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "SocketTimeoutException, Please check your network")
                    return@launch
                } catch (e: ConnectException){
                    Toast.makeText(requireActivity(), "Network ConnectException, Please check your network", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Network ConnectException, Please check your network")
                    return@launch
                } catch (e: Exception) {
                    Toast.makeText(requireActivity(), "An Exception happen: e = ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "An Exception happen: e = ${e.message}")
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
            setDataSource(audioFilePath)
            prepare() // 或者使用 prepareAsync()
            isLooping = false
        }
    }

    private var selectedUri: Uri = Uri.parse("")
    @RequiresApi(Build.VERSION_CODES.Q)
    private val activityResultContract = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (result.data?.clipData != null) {
                val count = result.data?.clipData?.itemCount?:0
                for (i in 0 until count) {
                    selectedUri = result.data?.clipData?.getItemAt(i)?.uri ?: Uri.parse("")
                }
            } else {
                selectedUri = result.data?.data?: Uri.parse("")
            }

            when  {
                isAudio -> {
                    lastAudioPath = ParseUriToRealPathUtil.getRealPathFromUri(requireContext(), selectedUri).toString()
//                    Toast.makeText(requireActivity(), "selectedUri= $selectedUri, lastAudioPath: $lastAudioPath", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "selectedUri= $selectedUri, lastAudioPath: $lastAudioPath")
                }
                isImage -> {
                    lastImagePath = ParseUriToRealPathUtil.getRealPathFromUri(requireContext(), selectedUri).toString()
//                    Toast.makeText(requireActivity(), "selectedUri= $selectedUri, lastImagePath: $lastImagePath", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "selectedUri= $selectedUri, lastImagePath: $lastImagePath")
                }
                isVideo -> {
                    lastVideoPath = ParseUriToRealPathUtil.getRealPathFromUri(requireContext(), selectedUri).toString()
//                    Toast.makeText(requireActivity(), "selectedUri= $selectedUri, lastVideoPath: $lastVideoPath", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "selectedUri= $selectedUri, lastVideoPath: $lastVideoPath")
                }
            }
        }
    }

}