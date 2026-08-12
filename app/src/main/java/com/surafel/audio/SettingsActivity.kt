package com.surafel.audio

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.settingsBack).setOnClickListener { finish() }
        findViewById<View>(R.id.refreshLibrarySetting).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.aboutSetting).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("About Audio")
                .setMessage("Audio\nA private local music and video player.\nYour library stays on your device.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
