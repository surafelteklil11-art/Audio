package com.surafel.audio

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.InputType
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
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

class VaultActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val vaultRoot by lazy { File(filesDir, "vault").apply { mkdirs() } }
    private val audioDir by lazy { File(vaultRoot, "audio").apply { mkdirs() } }
    private val videoDir by lazy { File(vaultRoot, "video").apply { mkdirs() } }
    private val photoDir by lazy { File(vaultRoot, "photo").apply { mkdirs() } }
    private val fileDir by lazy { File(vaultRoot, "file").apply { mkdirs() } }

    private var pendingDeleteUris: List<Uri> = emptyList()
    private val deleteRequest = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        val count = pendingDeleteUris.size
        pendingDeleteUris = emptyList()
        if (it.resultCode == RESULT_OK) {
            Toast.makeText(this, "$count original item(s) removed from device storage", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Vault copy is safe, but the original item(s) were not removed", Toast.LENGTH_LONG).show()
        }
        showVaultHome()
    }

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { copyAndHide(it, audioDir) }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { copyAndHide(it, videoDir) }
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { copyAndHide(it, photoDir) }
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { copyAndHide(it, fileDir) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isLocked()) showLockedScreen() else if (!isConfigured()) showSetupChoice() else showUnlock()
    }

    private fun isConfigured() = prefs.getString(KEY_TYPE, null) != null && prefs.getString(KEY_HASH, null) != null
    private fun isLocked() = prefs.getLong(KEY_LOCK_UNTIL, 0L) > System.currentTimeMillis()

    private fun showSetupChoice() {
        val root = panelRoot()
        root.addView(title("Create Hidden Vault"))
        root.addView(text("Create a private, device-local Safe Folder. Imported items are copied into Audio's private storage and can then be removed from their original location."))
        root.addView(option("PATTERN", "3×3 secure pattern", "Draw a pattern to unlock") { setupPattern() })
        root.addView(option("PASSWORD", "Alphabetic password", "Use 6+ characters") { setupTextCredential(false) })
        root.addView(option("PIN", "Numeric PIN", "Use 4+ digits") { setupTextCredential(true) })
        root.addView(button("CANCEL") { finish() })
        setContentView(root)
    }

    private fun option(label: String, sub: String, detail: String, click: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(10), dp(20), dp(10))
        background = rounded(Color.rgb(25, 35, 61), Color.rgb(83, 113, 168), 16, 1)
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(82)).apply { setMargins(0, dp(7), 0, dp(7)) }
        addView(TextView(this@VaultActivity).apply {
            text = label
            textSize = 13f
            setTextColor(Color.rgb(196, 169, 255))
            setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(this@VaultActivity).apply {
            text = "$sub  •  $detail"
            textSize = 15f
            setTextColor(Color.WHITE)
        })
    }

    private fun setupTextCredential(pin: Boolean) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        val first = EditText(this).apply {
            hint = if (pin) "Enter PIN" else "Enter password"
            inputType = if (pin) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val second = EditText(this).apply {
            hint = "Confirm"
            inputType = if (pin) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        box.addView(first); box.addView(second)
        val dialog = AlertDialog.Builder(this).setTitle(if (pin) "Create PIN" else "Create Password")
            .setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val a = first.text.toString(); val b = second.text.toString()
                val min = if (pin) 4 else 6
                if (a.length < min || (pin && !a.all(Char::isDigit))) { first.error = if (pin) "Use at least 4 digits" else "Use at least 6 characters"; return@setOnClickListener }
                if (a != b) { second.error = "Does not match"; return@setOnClickListener }
                saveCredential(if (pin) TYPE_PIN else TYPE_PASSWORD, a)
                dialog.dismiss(); showVaultHome()
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

    private fun showUnlock() {
        when (prefs.getString(KEY_TYPE, TYPE_PASSWORD)) {
            TYPE_PATTERN -> showPatternDialog("Unlock Hidden Vault") { verify(it) }
            TYPE_PIN -> showCredentialDialog(true)
            else -> showCredentialDialog(false)
        }
    }

    private fun showCredentialDialog(pin: Boolean) {
        val input = EditText(this).apply {
            hint = if (pin) "PIN" else "Password"
            inputType = if (pin) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("Unlock Hidden Vault").setView(input)
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setPositiveButton("Unlock") { _, _ -> verify(input.text.toString()) }
            .show()
    }

    private fun showPatternDialog(title: String, onDone: (String) -> Unit) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(18), dp(6), dp(18), dp(6)) }
        val state = TextView(this).apply { text = "Tap 4–9 points in order"; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER }
        root.addView(state, LinearLayout.LayoutParams(-1, dp(42)))
        val grid = GridLayout(this).apply { columnCount = 3; rowCount = 3 }
        val sequence = StringBuilder()
        for (i in 1..9) {
            val b = Button(this).apply {
                text = i.toString(); textSize = 18f
                setOnClickListener { if (!sequence.contains(i.toString())) { sequence.append(i); state.text = "Pattern: ${"• ".repeat(sequence.length)}" } }
            }
            grid.addView(b, GridLayout.LayoutParams().apply { width = dp(76); height = dp(58); setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        root.addView(grid)
        root.addView(Button(this).apply { text = "Clear"; setOnClickListener { sequence.clear(); state.text = "Tap 4–9 points in order" } })
        root.addView(Button(this).apply { text = "Continue"; setOnClickListener { onDone(sequence.toString()) } })
        AlertDialog.Builder(this).setTitle(title).setView(root).setNegativeButton("Cancel") { _, _ -> finish() }.show()
    }

    private fun saveCredential(type: String, value: String) {
        prefs.edit().putString(KEY_TYPE, type).putString(KEY_HASH, hash(value)).putInt(KEY_FAILED, 0).putLong(KEY_LOCK_UNTIL, 0L).apply()
    }

    private fun verify(value: String) {
        if (isLocked()) { showLockedScreen(); return }
        if (hash(value) == prefs.getString(KEY_HASH, "")) {
            prefs.edit().putInt(KEY_FAILED, 0).apply(); showVaultHome()
        } else {
            val failed = prefs.getInt(KEY_FAILED, 0) + 1
            if (failed >= 2) {
                prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + 24L * 60L * 60L * 1000L).apply(); showLockedScreen()
            } else { prefs.edit().putInt(KEY_FAILED, failed).apply(); Toast.makeText(this, "Wrong credential. 1 attempt left.", Toast.LENGTH_LONG).show(); showUnlock() }
        }
    }

    private fun showLockedScreen() {
        val hours = ((prefs.getLong(KEY_LOCK_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L) / 3600000L) + 1
        val root = panelRoot(); root.addView(title("Vault Locked")); root.addView(text("Two incorrect attempts were entered. Try again in about $hours hour(s).")); root.addView(button("CLOSE") { finish() }); setContentView(root)
    }

    private fun showVaultHome() {
        val root = panelRoot()
        root.addView(title("Hidden Vault"))
        root.addView(text("PRIVATE • DEVICE LOCAL • LOCKED\nYour imported items stay inside Audio's private app storage. Use MOVE TO VAULT to remove the original after Android confirmation."))
        root.addView(secureCard("🎵", "AUDIO", count(audioDir), "Move music into Safe Folder") { pickAudio.launch(arrayOf("audio/*")) })
        root.addView(secureCard("🎬", "VIDEO", count(videoDir), "Move videos into Safe Folder") { pickVideo.launch(arrayOf("video/*")) })
        root.addView(secureCard("🖼", "PHOTO", count(photoDir), "Move photos into Safe Folder") { pickPhoto.launch(arrayOf("image/*")) })
        root.addView(secureCard("📁", "FILE", count(fileDir), "Move documents into Safe Folder") { pickFile.launch(arrayOf("*/*")) })
        root.addView(secureCard("📱", "APP HIDDEN", 0, "Private list • system hiding requires special Android privileges") { showHiddenApps() })
        root.addView(button("LOCK NOW") { finish() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun secureCard(icon: String, label: String, items: Int, detail: String, click: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12)); background = rounded(Color.rgb(21, 28, 51), Color.rgb(70, 88, 135), 18, 1)
        setOnClickListener { click() }; layoutParams = LinearLayout.LayoutParams(-1, dp(92)).apply { setMargins(0, dp(7), 0, dp(7)) }
        addView(TextView(this@VaultActivity).apply { text = "$icon  $label"; textSize = 18f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD) })
        addView(TextView(this@VaultActivity).apply { text = "$items ITEMS   •   $detail"; textSize = 13f; setTextColor(Color.rgb(171, 184, 215)); setPadding(0, dp(4), 0, 0) })
    }

    private fun count(dir: File) = dir.listFiles()?.count { it.isFile } ?: 0

    private fun showHiddenApps() {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL).filter { it.activityInfo.packageName != packageName }.distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(pm).toString().lowercase(Locale.getDefault()) }
        val hidden = prefs.getStringSet(KEY_HIDDEN_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        val checks = mutableMapOf<String, CheckBox>()
        apps.forEach { info ->
            val pkg = info.activityInfo.packageName
            val cb = CheckBox(this).apply { text = info.loadLabel(pm); isChecked = hidden.contains(pkg) }
            checks[pkg] = cb; box.addView(cb)
        }
        AlertDialog.Builder(this).setTitle("App Hidden").setMessage("This private list is saved inside the vault. Android does not allow a normal app to silently remove another app from the launcher; true system-level app hiding requires device-owner, managed-device, or root privileges.").setView(ScrollView(this).apply { addView(box) }).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
            hidden.clear(); checks.filterValues { it.isChecked }.keys.forEach { hidden.add(it) }; prefs.edit().putStringSet(KEY_HIDDEN_APPS, hidden).apply()
        }.show()
    }

    /** Copies selected content into private storage, then asks Android to remove the originals. */
    private fun copyAndHide(uris: List<Uri>?, destination: File) {
        if (uris.isNullOrEmpty()) return
        val successfulOriginals = mutableListOf<Uri>()
        var copied = 0
        uris.forEach { uri ->
            try {
                val name = queryDisplayName(uri) ?: "item_${System.currentTimeMillis()}"
                val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val target = uniqueFile(destination, safe)
                contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } } ?: throw IllegalStateException("Cannot read source")
                copied++; successfulOriginals.add(uri)
            } catch (_: Exception) { }
        }
        if (copied == 0) { Toast.makeText(this, "Nothing was imported", Toast.LENGTH_SHORT).show(); return }
        prefs.edit().putLong(KEY_LAST_IMPORT, System.currentTimeMillis()).apply()
        askRemoveOriginals(successfulOriginals, copied)
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name); var n = 1
        while (f.exists()) { f = File(dir, "${n++}_$name") }
        return f
    }

    private fun askRemoveOriginals(uris: List<Uri>, copied: Int) {
        AlertDialog.Builder(this)
            .setTitle("Secure Move Complete")
            .setMessage("$copied item(s) are now copied into the private Hidden Vault.\n\nRemove the original item(s) from their normal Music / Gallery / Files location so they are no longer visible there?")
            .setNegativeButton("KEEP ORIGINAL") { _, _ -> Toast.makeText(this, "Saved in Vault • original kept", Toast.LENGTH_LONG).show(); showVaultHome() }
            .setPositiveButton("HIDE ORIGINAL") { _, _ -> removeOriginals(uris) }
            .setCancelable(false)
            .show()
    }

    private fun removeOriginals(uris: List<Uri>) {
        if (uris.isEmpty()) { showVaultHome(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                pendingDeleteUris = uris
                val request = MediaStore.createDeleteRequest(contentResolver, uris)
                deleteRequest.launch(IntentSenderRequest.Builder(request.intentSender).build())
                return
            } catch (_: Exception) { }
        }
        var removed = 0
        uris.forEach { uri ->
            try {
                if (DocumentsContract.isDocumentUri(this, uri)) {
                    if (DocumentsContract.deleteDocument(contentResolver, uri)) removed++
                } else if (contentResolver.delete(uri, null, null) > 0) removed++
            } catch (_: Exception) { }
        }
        Toast.makeText(this, if (removed == uris.size) "Originals hidden successfully" else "Vault saved, but Android kept some originals", Toast.LENGTH_LONG).show()
        showVaultHome()
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return uri.lastPathSegment
    }

    private fun panelRoot() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(28), dp(34), dp(28), dp(28)); background = GradientDrawable().apply { setColor(Color.rgb(9, 9, 25)) } }
    private fun title(value: String) = TextView(this).apply { text = value; textSize = 29f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); setPadding(0, 0, 0, dp(18)) }
    private fun text(value: String) = TextView(this).apply { text = value; textSize = 15f; setTextColor(Color.rgb(174, 184, 210)); setPadding(0, 0, 0, dp(18)) }
    private fun button(value: String, click: () -> Unit) = Button(this).apply { text = value; setTextColor(Color.WHITE); setOnClickListener { click() }; background = rounded(Color.rgb(40, 62, 104), Color.rgb(92, 132, 205), 16, 1); layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, dp(14), 0, 0) } }
    private fun rounded(fill: Int, stroke: Int, radius: Int, width: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(width), stroke) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        const val PREFS = "audio_vault"
        const val KEY_TYPE = "credential_type"
        const val KEY_HASH = "credential_hash"
        const val KEY_FAILED = "failed_attempts"
        const val KEY_LOCK_UNTIL = "lock_until"
        const val KEY_HIDDEN_APPS = "hidden_apps"
        const val KEY_LAST_IMPORT = "last_import"
        const val TYPE_PATTERN = "pattern"
        const val TYPE_PASSWORD = "password"
        const val TYPE_PIN = "pin"
    }
}
