package com.surafel.audio

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat

class MediaActionActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ACTION = "media_action"
        const val EXTRA_URI = "media_uri"
        const val EXTRA_NAME = "media_name"
        const val ACTION_RENAME = "rename"
        const val ACTION_DELETE = "delete"
    }

    private lateinit var uri: Uri
    private lateinit var action: String
    private var newName: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) applyAction()
        else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uri = Uri.parse(intent.getStringExtra(EXTRA_URI) ?: run { finish(); return })
        action = intent.getStringExtra(EXTRA_ACTION) ?: run { finish(); return }
        newName = intent.getStringExtra(EXTRA_NAME)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = when (action) {
                ACTION_RENAME -> MediaStore.createWriteRequest(contentResolver, listOf(uri))
                ACTION_DELETE -> MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                else -> null
            }
            if (request == null) {
                finish()
                return
            }
            permissionLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            applyAction()
        }
    }

    private fun applyAction() {
        try {
            when (action) {
                ACTION_RENAME -> {
                    val name = newName ?: return finish()
                    val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
                    contentResolver.update(uri, values, null, null)
                }
                ACTION_DELETE -> contentResolver.delete(uri, null, null)
            }
        } catch (_: SecurityException) {
            // The system approval flow is the normal path on modern Android.
        } finally {
            finish()
        }
    }
}
