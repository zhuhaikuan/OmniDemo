package com.lenovo.omnidemo.traditional.activity.online

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lenovo.omnidemo.R
import com.lenovo.omnidemo.databinding.ActivityOnlineBinding
import com.lenovo.omnidemo.traditional.activity.base.BaseActivity

class OnlineActivity : BaseActivity<ActivityOnlineBinding>() {

    companion object  {
        const val TAG = "OnlineActivity"

        fun startOnlineActivity(context: AppCompatActivity) {
            context.startActivity(Intent(context, OnlineActivity::class.java))
        }
    }

    override fun initViewBinding(): ActivityOnlineBinding {
        return ActivityOnlineBinding.inflate(layoutInflater)
    }

    override fun initData() {

    }
}