package com.focusflow.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.focusflow.app.BuildConfig

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        try {
            val tvVersion = findViewById<android.widget.TextView>(R.id.tvSplashVersion)
            tvVersion.text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
        } catch (_: Exception) {}

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1200)
    }
}
