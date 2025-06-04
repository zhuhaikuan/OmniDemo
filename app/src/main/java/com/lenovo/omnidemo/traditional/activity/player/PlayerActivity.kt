package com.lenovo.omnidemo.traditional.activity.player

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.lenovo.omnidemo.databinding.ActivityPlayerBinding
import com.lenovo.omnidemo.traditional.activity.base.BaseActivity

class PlayerActivity : BaseActivity<ActivityPlayerBinding>() {
    private lateinit var player: ExoPlayer

    companion object {
        const val TAG = "PlayerActivity"

        fun startPlayerActivity(context: AppCompatActivity) {
            context.startActivity(context.intent.setClass(context, PlayerActivity::class.java))
        }
    }

    override fun initViewBinding(): ActivityPlayerBinding {
        return ActivityPlayerBinding.inflate(layoutInflater)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun initData() {
        initPlayer()
        binding.playerView.player = player
        play()
    }

    private fun initPlayer() {
        player = ExoPlayer.Builder(this).build()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun play() {
        val moviesPaths = getMusicPaths(this)

        val videoUri = moviesPaths[moviesPaths.size-1]

        // Build the media item.
        val mediaItem = MediaItem.fromUri(videoUri)
        // Set the media item to be played.
        player.setMediaItem(mediaItem)
        // Prepare the player.
        player.prepare()
        // Start the playback.
        player.play()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun getMoviesPaths(context: Context): List<String> {
        val moviesPaths = mutableListOf<String>()
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(MediaStore.Video.Media.DATA)

        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            while (cursor.moveToNext()) {
                moviesPaths.add(cursor.getString(dataIndex))
            }
        }

        return moviesPaths
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun getMusicPaths(context: Context): List<String> {
        val musicPaths = mutableListOf<String>()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(MediaStore.Audio.Media.DATA)

        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                musicPaths.add(cursor.getString(dataIndex))
            }
        }

        return musicPaths
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun getPicturesPaths(context: Context): List<String> {
        val picturesPaths = mutableListOf<String>()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(MediaStore.Images.Media.DATA)

        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (cursor.moveToNext()) {
                picturesPaths.add(cursor.getString(dataIndex))
            }
        }

        return picturesPaths
    }

}