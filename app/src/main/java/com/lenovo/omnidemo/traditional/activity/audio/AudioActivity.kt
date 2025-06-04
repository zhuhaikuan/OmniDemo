package com.lenovo.omnidemo.traditional.activity.audio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.lenovo.omnidemo.R
import com.lenovo.omnidemo.databinding.ActivityAudioBinding
import com.lenovo.omnidemo.traditional.activity.base.BaseActivity
import java.io.FileDescriptor
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class AudioActivity : BaseActivity<ActivityAudioBinding>() {

    private lateinit var mediaRecorder: MediaRecorder
    private var isRecording = false
    private var parcelFileDescriptor: android.os.ParcelFileDescriptor? = null


    companion object {
        const val TAG = "AudioActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        fun startAudioActivity(context: Context) {
            val intent = Intent(context, AudioActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun initViewBinding(): ActivityAudioBinding {
        return ActivityAudioBinding.inflate(layoutInflater)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun initData() {
        binding.startAudio.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording() }
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

        val audioUri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (audioUri == null) {
            Toast.makeText(this@AudioActivity, "Failed to create audio file", Toast.LENGTH_SHORT).show()
            return
        }

        parcelFileDescriptor = contentResolver.openFileDescriptor(audioUri, "w")
        val fileDescriptor: FileDescriptor = parcelFileDescriptor!!.fileDescriptor

        mediaRecorder = MediaRecorder(this).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(fileDescriptor)
        }

        try {
            mediaRecorder.prepare()
            mediaRecorder.start()
            isRecording = true
            binding.startAudio.setText(R.string.stop_audio)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder.apply {
                stop()
                release()
            }
            mediaRecorder = MediaRecorder()
            parcelFileDescriptor?.close()
            parcelFileDescriptor = null
            isRecording = false
            binding.startAudio.setText(R.string.start_audio)
            Toast.makeText(this@AudioActivity, "Audio recording succeed", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}