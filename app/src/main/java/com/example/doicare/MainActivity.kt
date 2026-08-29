package com.example.doicare

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Basic layout programmatically for MVP simplicity
        val layout = androidx.appcompat.widget.LinearLayoutCompat(this).apply {
            orientation = androidx.appcompat.widget.LinearLayoutCompat.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Do I Care"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)

        val statusText = TextView(this).apply {
            text = "Check notification access..."
            setPadding(0, 0, 0, 32)
        }
        layout.addView(statusText)

        val settingsBtn = Button(this).apply {
            text = "Enable Notification Access"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        layout.addView(settingsBtn)

        setContentView(layout)
    }
}
