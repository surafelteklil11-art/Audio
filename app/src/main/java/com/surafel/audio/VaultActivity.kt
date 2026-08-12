package com.surafel.audio

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class VaultActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val audioDir by lazy { File(filesDir, "vault/audio").apply { mkdirs() } }
    private val videoDir by lazy { File(filesDir, "vault/video").apply { mkdirs() } }
    private val photoDir by lazy { File(filesDir, "vault/photo").apply { mkdirs() } }
    private val fileDir by lazy { File(filesDir, "vault/file").apply { mkdirs() } }

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { copyPicked(it, audioDir) }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { copyPicked(it, videoDir) }
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { copyPicked(it, photoDir) }
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { copyPicked(it, fileDir) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isLocked()) showLockedScreen() else if (!isConfigured()) showSetupChoice() else showUnlock()
    }

    private fun isConfigured() = prefs.getString(KEY_TYPE, null) != null && prefs.getString(KEY_HASH, null) != null
    private fun isLocked() = prefs.getLong(KEY_LOCK_UNTIL, 0L) > System.currentTimeMillis()

    private fun showSetupChoice() {
        val options = arrayOf("Pattern", "Alphabetic password", "PIN")
        AlertDialog.Builder(this).setTitle("Create Hidden Vault")
            .setMessage("Choose how you want to protect the hidden area.")
            .setItems(options) { _, which -> when (which) { 0 -> setupPattern(); 1 -> setupTextCredential(false); 2 -> setupTextCredential(true) } }
            .setNegativeButton("Cancel") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
    }

    private fun setupTextCredential(pin: Boolean) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 0, 32, 0) }
        val first = EditText(this).apply { hint = if (pin) "Enter PIN" else "Enter password"; if (pin) inputType = 2 else inputType = 0x00000001 }
        val second = EditText(this).apply { hint = "Confirm"; if (pin) inputType = 2 else inputType = 0x00000001 }
        box.addView(first); box.addView(second)
        val dialog = AlertDialog.Builder(this).setTitle(if (pin) "Create PIN" else "Create password").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val a = first.text.toString(); val b = second.text.toString()
                if (a.length < if (pin) 4 else 6) { first.error = if (pin) "Use at least 4 digits" else "Use at least 6 characters"; return@setOnClickListener }
                if (a != b) { second.error = "Does not match"; return@setOnClickListener }
                if (pin && !a.all { it.isDigit() }) { first.error = "PIN must contain digits only"; return@setOnClickListener }
                saveCredential(if (pin) TYPE_PIN else TYPE_PASSWORD, a); dialog.dismiss(); showVaultHome()
            }
        }
        dialog.show()
    }

    private fun setupPattern() {
        showPatternDialog("Create Pattern") { pattern ->
            if (pattern.length < 4) { Toast.makeText(this, "Use at least 4 points", Toast.LENGTH_SHORT).show(); return@showPatternDialog }
            showPatternDialog("Confirm Pattern") { confirm ->
                if (confirm != pattern) Toast.makeText(this, "Pattern does not match", Toast.LENGTH_SHORT).show()
                else { saveCredential(TYPE_PATTERN, pattern); showVaultHome() }
            }
        }
    }

    private fun showUnlock() { when (prefs.getString(KEY_TYPE, TYPE_PASSWORD)) { TYPE_PATTERN -> showPatternDialog("Unlock Hidden Vault") { verify(it) }; TYPE_PIN -> showCredentialDialog(true); else -> showCredentialDialog(false) } }

    private fun showCredentialDialog(pin: Boolean) {
        val input = EditText(this).apply { hint = if (pin) "PIN" else "Password"; if (pin) inputType = 2 else inputType = 0x00000001 }
        AlertDialog.Builder(this).setTitle("Unlock Hidden Vault").setView(input).setNegativeButton("Cancel") { _, _ -> finish() }.setPositiveButton("Unlock") { _, _ -> verify(input.text.toString()) }.setOnCancelListener { finish() }.show()
    }

    private fun showPatternDialog(title: String, onDone: (String) -> Unit) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(30, 8, 30, 8) }
        val state = TextView(this).apply { text = "Tap 4–9 points in order"; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER }
        root.addView(state, LinearLayout.LayoutParams(-1, 48))
        val grid = GridLayout(this).apply { columnCount = 3; rowCount = 3 }
        val sequence = StringBuilder()
        for (i in 1..9) {
            val b = Button(this).apply { text = i.toString(); textSize = 18f; setOnClickListener { if (!sequence.contains(i.toString())) sequence.append(i); state.text = "Pattern: ${"• ".repeat(sequence.length)}" } }
            val p = GridLayout.LayoutParams().apply { width = 82; height = 62; setMargins(5, 5, 5, 5) }
            grid.addView(b, p)
        }
        root.addView(grid)
        root.addView(Button(this).apply { text = "Clear"; setOnClickListener { sequence.clear(); state.text = "Tap 4–9 points in order" } })
        root.addView(Button(this).apply { text = "Continue"; setOnClickListener { onDone(sequence.toString()) } })
        AlertDialog.Builder(this).setTitle(title).setView(root).setNegativeButton("Cancel") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
    }

    private fun saveCredential(type: String, value: String) { prefs.edit().putString(KEY_TYPE, type).putString(KEY_HASH, hash(value)).putInt(KEY_FAILED, 0).putLong(KEY_LOCK_UNTIL, 0L).apply() }

    private fun verify(value: String) {
        if (isLocked()) { showLockedScreen(); return }
        if (hash(value) == prefs.getString(KEY_HASH, "")) { prefs.edit().putInt(KEY_FAILED, 0).apply(); showVaultHome() }
        else {
            val failed = prefs.getInt(KEY_FAILED, 0) + 1
            if (failed >= 2) { prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + 24L * 60L * 60L * 1000L).apply(); showLockedScreen() }
            else { prefs.edit().putInt(KEY_FAILED, failed).apply(); Toast.makeText(this, "Wrong credential. 1 attempt left.", Toast.LENGTH_LONG).show(); showUnlock() }
        }
    }

    private fun showLockedScreen() {
        val remaining = ((prefs.getLong(KEY_LOCK_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L) / 3600000L) + 1
        val root = baseRoot(); root.addView(title("Vault Locked")); root.addView(text("Two incorrect attempts were entered. Try again in about $remaining hour(s).")); root.addView(button("Close") { finish() }); setContentView(root)
    }

    private fun showVaultHome() {
        val root = baseRoot(); root.addView(title("Hidden Vault")); root.addView(text("Protected on this device. Imported files are copied into Audio's private storage."))
        root.addView(card("🎵  Audio", "${audioDir.listFiles()?.size ?: 0} items") { pickAudio.launch(arrayOf("audio/*")) })
        root.addView(card("🎬  Video", "${videoDir.listFiles()?.size ?: 0} items") { pickVideo.launch(arrayOf("video/*")) })
        root.addView(card("🖼  Photo", "${photoDir.listFiles()?.size ?: 0} items") { pickPhoto.launch(arrayOf("image/*")) })
        root.addView(card("📁  File", "${fileDir.listFiles()?.size ?: 0} items") { pickFile.launch(arrayOf("*/*")) })
        root.addView(card("📱  App Hidden", "Private list of apps") { showHiddenApps() })
        root.addView(button("Lock now") { finish() }); setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showHiddenApps() {
        val pm = packageManager; val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL).filter { it.activityInfo.packageName != packageName }.distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(pm).toString().lowercase(Locale.getDefault()) }
        val hidden = prefs.getStringSet(KEY_HIDDEN_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 0, 24, 0) }; val checks = mutableMapOf<String, CheckBox>()
        apps.forEach { info -> val pkg = info.activityInfo.packageName; val cb = CheckBox(this).apply { text = info.loadLabel(pm); isChecked = hidden.contains(pkg) }; checks[pkg] = cb; box.addView(cb) }
        AlertDialog.Builder(this).setTitle("App Hidden").setMessage("This saves a private hidden-app list. Android normally does not let a regular app disable another app's launcher icon.").setView(ScrollView(this).apply { addView(box) }).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> hidden.clear(); checks.filterValues { it.isChecked }.keys.forEach { hidden.add(it) }; prefs.edit().putStringSet(KEY_HIDDEN_APPS, hidden).apply() }.show()
    }

    private fun copyPicked(uris: List<Uri>?, destination: File) {
        if (uris.isNullOrEmpty()) return; var copied = 0
        uris.forEach { uri -> try { val name = queryDisplayName(uri) ?: "item_${System.currentTimeMillis()}"; val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_"); val target = File(destination, "${System.currentTimeMillis()}_$safe"); contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }; copied++ } catch (_: Exception) {} }
        Toast.makeText(this, "$copied item(s) saved in the hidden vault", Toast.LENGTH_SHORT).show(); showVaultHome()
    }

    private fun queryDisplayName(uri: Uri): String? { contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c -> if (c.moveToFirst()) return c.getString(0) }; return uri.lastPathSegment }
    private fun baseRoot() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 34, 28, 28); setBackgroundColor(Color.rgb(12, 12, 30)) }
    private fun title(value: String) = TextView(this).apply { text = value; textSize = 28f; setTextColor(Color.WHITE); setTypeface(typeface, 1); setPadding(0, 0, 0, 18) }
    private fun text(value: String) = TextView(this).apply { text = value; textSize = 15f; setTextColor(Color.rgb(170, 180, 205)); setPadding(0, 0, 0, 18) }
    private fun card(head: String, sub: String, click: () -> Unit) = Button(this).apply { text = "$head\n$sub"; textSize = 17f; gravity = Gravity.CENTER_VERTICAL; setOnClickListener { click() }; layoutParams = LinearLayout.LayoutParams(-1, 82).apply { setMargins(0, 7, 0, 7) } }
    private fun button(value: String, click: () -> Unit) = Button(this).apply { text = value; setOnClickListener { click() }; layoutParams = LinearLayout.LayoutParams(-1, 54).apply { setMargins(0, 14, 0, 0) } }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        const val PREFS = "audio_vault"; const val KEY_TYPE = "credential_type"; const val KEY_HASH = "credential_hash"; const val KEY_FAILED = "failed_attempts"; const val KEY_LOCK_UNTIL = "lock_until"; const val KEY_HIDDEN_APPS = "hidden_apps"; const val TYPE_PATTERN = "pattern"; const val TYPE_PASSWORD = "password"; const val TYPE_PIN = "pin"
    }
}
