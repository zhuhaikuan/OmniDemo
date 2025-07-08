package com.lenovo.omnidemo.traditional.fragment.offline

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.lenovo.omnidemo.R
import com.lenovo.omnidemo.databinding.FragmentOfflineBinding
import com.lenovo.omnidemo.traditional.activity.base.BaseFragment
import com.lenovo.omnidemo.traditional.fragment.online.OnlineViewModel
import com.lenovo.omnidemo.traditional.tools.ParseUriToRealPathUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Response
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException

class OfflineFragment : BaseFragment<FragmentOfflineBinding>() {
    private val mediaPlayer: MediaPlayer = MediaPlayer()

    private var lastAudioPath = ""
    private var lastVideoPath = ""
    private var lastImagePath = ""

    private var isAudio = false
    private var isVideo = false
    private var isImage = false

    val audioFileList = ArrayList<File>()
    var audioNum = 0
    var audioIndex = 0

    companion object {
        const val TAG = "OfflineFragment"
        fun newInstance() = OfflineFragment()
    }

    private val viewModel: OnlineViewModel by viewModels()

    override fun initViewBinding(): FragmentOfflineBinding {
        return FragmentOfflineBinding.inflate(layoutInflater)
    }

    override fun initData() {
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
            audioNum = 0
            audioIndex = 0
            audioFileList.clear()

            val text = binding.question.text.toString()
            var sb = StringBuilder()
            lifecycleScope.launch {
                try {
                    Log.e(TAG, "text: $text, lastImagePath:  $lastImagePath, lastAudioPath: $lastAudioPath, lastVideoPath: $lastVideoPath")
                    val call = viewModel.processFormStream(text, lastImagePath, lastAudioPath, lastVideoPath)
                    call.enqueue(object : retrofit2.Callback<ResponseBody> {
                        override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                            Log.e(TAG, "############### onResponse has been called!!!")
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
                                }
                            }
                            binding.play.isEnabled = true
                            playAudio()
                        }

                        override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                            Log.e("VideoViewModel", "请求失败：${t.message}")
                        }
                    })
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

    private var selectedUri: Uri = "".toUri()
    private val activityResultContract = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (result.data?.clipData != null) {
                val count = result.data?.clipData?.itemCount?:0
                for (i in 0 until count) {
                    selectedUri = result.data?.clipData?.getItemAt(i)?.uri ?: "".toUri()
                }
            } else {
                selectedUri = result.data?.data?: "".toUri()
            }

            when  {
                isAudio -> {
                    lastAudioPath = ParseUriToRealPathUtil.getRealPathFromUri(requireContext(), selectedUri).toString()
                    Log.e(TAG, "selectedUri= $selectedUri, lastAudioPath: $lastAudioPath")
                }
                isImage -> {
                    lastImagePath = ParseUriToRealPathUtil.getRealPathFromUri(requireContext(), selectedUri).toString()
                    Log.e(TAG, "selectedUri= $selectedUri, lastImagePath: $lastImagePath")
                }
                isVideo -> {
                    lastVideoPath = ParseUriToRealPathUtil.getRealPathFromUri(requireContext(), selectedUri).toString()
                    Log.e(TAG, "selectedUri= $selectedUri, lastVideoPath: $lastVideoPath")
                }
            }
        }
    }

}