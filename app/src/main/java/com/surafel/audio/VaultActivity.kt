package com.surafel.audio

import android.app.AlertDialog
import android.content.Intent
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
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
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

    private var currentCategory = CATEGORY_HOME
    private var currentDir: File? = null
    private var pendingDeleteUris: List<Uri> = emptyList()
    private var pendingRestoreFile: File? = null

    private val deleteRequest = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        val count = pendingDeleteUris.size
        pendingDeleteUris = emptyList()
        if (it.resultCode == RESULT_OK) {
            Toast.makeText(this, "$count original item(s) hidden successfully", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Vault copy is safe, but the original item(s) were kept", Toast.LENGTH_LONG).show()
        }
        refreshCurrentPage()
    }

    private val restoreRequest = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val source = pendingRestoreFile
        pendingRestoreFile = null
        if (uri == null || source == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cannot write destination")
            if (!source.delete()) throw IllegalStateException("Restore succeeded but private copy could not be removed")
            Toast.makeText(this, "Restored from Hidden Vault", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Restore failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
        refreshCurrentPage()
    }

    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { importItems(it, audioDir, CATEGORY_AUDIO) }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { importItems(it, videoDir, CATEGORY_VIDEO) }
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { importItems(it, photoDir, CATEGORY_PHOTO) }
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importItems(it, fileDir, CATEGORY_FILE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when {
            isLocked() -> showLockedScreen()
            !isConfigured() -> showSetupChoice()
            else -> showUnlock()
        }
    }

    @Deprecated("Deprecated by Android, retained for project compatibility")
    override fun onBackPressed() {
        if (currentCategory != CATEGORY_HOME) {
            val root = categoryDir(currentCategory)
            val dir = currentDir
            if (dir != null && dir.absolutePath != root.absolutePath) {
                currentDir = dir.parentFile ?: root
                showVaultCategoryPage(currentCategory, currentDir!!)
            } else {
                showVaultHome()
            }
        } else super.onBackPressed()
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
        root.addView(luxButton("CANCEL") { finish() })
        setContentView(root)
    }

    private fun option(label: String, sub: String, detail: String, click: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(10), dp(20), dp(10))
        background = rounded(Color.rgb(24, 34, 60), Color.rgb(82, 113, 171), 18, 1)
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(82)).apply { setMargins(0, dp(7), 0, dp(7)) }
        addView(TextView(this@VaultActivity).apply {
            text = label
            textSize = 13f
            setTextColor(Color.rgb(198, 167, 255))
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
        box.addView(first)
        box.addView(second)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (pin) "Create PIN" else "Create Password")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val a = first.text.toString()
                val b = second.text.toString()
                val min = if (pin) 4 else 6
                if (a.length < min || (pin && !a.all(Char::isDigit))) {
                    first.error = if (pin) "Use at least 4 digits" else "Use at least 6 characters"
                    return@setOnClickListener
                }
                if (a != b) {
                    second.error = "Does not match"
                    return@setOnClickListener
                }
                saveCredential(if (pin) TYPE_PIN else TYPE_PASSWORD, a)
                dialog.dismiss()
                showVaultHome()
            }
        }
        dialog.show()
    }

    private fun setupPattern() {
        showPatternDialog("Create Pattern") { pattern ->
            if (pattern.length < 4) {
                Toast.makeText(this, "Use at least 4 points", Toast.LENGTH_SHORT).show()
                return@showPatternDialog
            }
            showPatternDialog("Confirm Pattern") { confirm ->
                if (confirm != pattern) {
                    Toast.makeText(this, "Pattern does not match", Toast.LENGTH_SHORT).show()
                } else {
                    saveCredential(TYPE_PATTERN, pattern)
                    showVaultHome()
                }
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
        AlertDialog.Builder(this)
            .setTitle("Unlock Hidden Vault")
            .setView(input)
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
            val b = TextView(this).apply {
                text = i.toString()
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = rounded(Color.rgb(29, 41, 70), Color.rgb(98, 127, 184), 14, 1)
                setOnClickListener {
                    if (!sequence.contains(i.toString())) {
                        sequence.append(i)
                        state.text = "Pattern: ${"• ".repeat(sequence.length)}"
                    }
                }
            }
            grid.addView(b, GridLayout.LayoutParams().apply { width = dp(76); height = dp(58); setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        root.addView(grid)
        root.addView(luxButton("CLEAR") { sequence.clear(); state.text = "Tap 4–9 points in order" })
        root.addView(luxButton("CONTINUE") { onDone(sequence.toString()) })
        AlertDialog.Builder(this).setTitle(title).setView(root).setNegativeButton("Cancel") { _, _ -> finish() }.show()
    }

    private fun saveCredential(type: String, value: String) {
        prefs.edit()
            .putString(KEY_TYPE, type)
            .putString(KEY_HASH, hash(value))
            .putInt(KEY_FAILED, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()
    }

    private fun verify(value: String) {
        if (isLocked()) {
            showLockedScreen()
            return
        }
        if (hash(value) == prefs.getString(KEY_HASH, "")) {
            prefs.edit().putInt(KEY_FAILED, 0).apply()
            showVaultHome()
            return
        }
        val failed = prefs.getInt(KEY_FAILED, 0) + 1
        if (failed >= 2) {
            prefs.edit()
                .putInt(KEY_FAILED, 0)
                .putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + 24L * 60L * 60L * 1000L)
                .apply()
            showLockedScreen()
        } else {
            prefs.edit().putInt(KEY_FAILED, failed).apply()
            Toast.makeText(this, "Wrong credential. 1 attempt left.", Toast.LENGTH_LONG).show()
            showUnlock()
        }
    }

    private fun showLockedScreen() {
        val hours = ((prefs.getLong(KEY_LOCK_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L) / 3600000L) + 1
        val root = panelRoot()
        root.addView(title("Vault Locked"))
        root.addView(text("Two incorrect attempts were entered. Try again in about $hours hour(s)."))
        root.addView(luxButton("CLOSE") { finish() })
        setContentView(root)
    }
    /** Compact, icon-first luxury vault home. */
    /** Compact, icon-first luxury vault home. */
    /** Compact, icon-first luxury vault home. */
    /** Compact, icon-first luxury vault home. */
    private fun showVaultHome() {
        currentCategory = CATEGORY_HOME
        currentDir = null
        val root = panelRoot().apply { setPadding(dp(22), dp(24), dp(22), dp(18)) }
        root.addView(title("Hidden Vault"))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(iconTile("🎵") { showVaultCategoryPage(CATEGORY_AUDIO) }, compactRowParams())
        row.addView(iconTile("🎬") { showVaultCategoryPage(CATEGORY_VIDEO) }, compactRowParams())
        row.addView(iconTile("🖼") { showVaultCategoryPage(CATEGORY_PHOTO) }, compactRowParams())
        row.addView(iconTile("📁") { showVaultCategoryPage(CATEGORY_FILE) }, compactRowParams())
        root.addView(row, LinearLayout.LayoutParams(-1, dp(92)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(16)
            bottomMargin = dp(16)
        })
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(9, 9, 25))
            addView(root)
        })
    }
    private fun iconTile(icon: String, click: () -> Unit) = TextView(this).apply {
        text = icon
        textSize = 31f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setOnClickListener { click() }
        background = GradientDrawable().apply {
            setColor(Color.rgb(18, 27, 51))
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.rgb(81, 111, 172))
        }
        elevation = dp(4).toFloat()
        contentDescription = "Private vault category"
    }
    private fun compactRowParams() = LinearLayout.LayoutParams(0, dp(86), 1f).apply {
        setMargins(dp(4), 0, dp(4), 0)
    }

    private fun showVaultCategoryPage(category: String, dir: File = categoryDir(category)) {
        currentCategory = category
        currentDir = dir
        val root = categoryDir(category)
        val label = categoryLabel(category)
        val vaultBackground = Color.rgb(9, 9, 25)
        val page = FrameLayout(this).apply { setBackgroundColor(vaultBackground) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(vaultBackground)
            setPadding(dp(22), dp(24), dp(22), dp(96))
        }
        content.addView(topBar(label) {
            if (dir.absolutePath == root.absolutePath) showVaultHome()
            else showVaultCategoryPage(category, dir.parentFile ?: root)
        })
        val entries = dir.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.getDefault()) })
            ?: emptyList()
        entries.forEach { entry -> content.addView(vaultEntryCard(category, entry)) }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(vaultBackground)
            addView(content)
            isFillViewport = true
        }
        page.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val add = TextView(this).apply {
            text = "+"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = "Add $label"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(45, 72, 128))
                setStroke(dp(2), Color.rgb(105, 145, 225))
            }
            elevation = dp(12).toFloat()
            setOnClickListener { launchPicker(category) }
        }
        page.addView(add, FrameLayout.LayoutParams(dp(62), dp(62), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dp(22), dp(22))
        })
        setContentView(page)
    }
    private fun vaultEntryCard(category: String, entry: File) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(9), dp(8), dp(9))
        background = rounded(Color.rgb(16, 24, 48), Color.rgb(67, 87, 137), 18, 1)
        layoutParams = LinearLayout.LayoutParams(-1, dp(78)).apply { setMargins(0, dp(5), 0, dp(5)) }
        setOnClickListener {
            if (entry.isDirectory) showVaultCategoryPage(category, entry) else viewEntry(category, entry)
        }
        if (entry.isDirectory) {
            addView(TextView(this@VaultActivity).apply { text = "📂"; textSize = 27f; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(60), -1))
            addView(TextView(this@VaultActivity).apply {
                text = entry.name
                textSize = 16f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            })
        } else {
            if (category == CATEGORY_PHOTO) {
                addView(ImageView(this@VaultActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageURI(Uri.fromFile(entry))
                }, LinearLayout.LayoutParams(dp(62), dp(62)).apply { rightMargin = dp(10) })
            } else {
                addView(TextView(this@VaultActivity).apply { text = categoryIcon(category); textSize = 25f; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(58), -1))
            }
            addView(LinearLayout(this@VaultActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
                addView(TextView(this@VaultActivity).apply {
                    text = entry.name
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                addView(TextView(this@VaultActivity).apply {
                    text = formatBytes(entry.length())
                    textSize = 12f
                    setTextColor(Color.rgb(157, 174, 208))
                })
            })
        }
        addView(TextView(this@VaultActivity).apply {
            text = "⋮"
            textSize = 26f
            setTextColor(Color.rgb(196, 166, 255))
            gravity = Gravity.CENTER
            contentDescription = "More options"
            setOnClickListener { showEntryMenu(category, entry) }
        }, LinearLayout.LayoutParams(dp(42), -1))
    }

    private fun showEntryMenu(category: String, entry: File) {
        val actions = if (entry.isDirectory) {
            arrayOf("CREATE FOLDER", "MOVE TO OTHER FOLDER", "DELETE")
        } else {
            arrayOf("CREATE FOLDER", "MOVE OUT HIDDEN", "MOVE TO OTHER FOLDER", "DELETE")
        }
        AlertDialog.Builder(this).setTitle(entry.name).setItems(actions) { _, which ->
            when {
                which == 0 -> createFolder(currentDir ?: categoryDir(category), category)
                !entry.isDirectory && which == 1 -> startRestore(entry)
                entry.isDirectory && which == 1 -> moveEntry(category, entry)
                !entry.isDirectory && which == 2 -> moveEntry(category, entry)
                else -> deletePrivateFile(entry)
            }
        }.show()
    }

    private fun createFolder(parent: File, category: String) {
        val input = EditText(this).apply { hint = "Folder name"; inputType = InputType.TYPE_CLASS_TEXT }
        AlertDialog.Builder(this).setTitle("Create Folder").setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CREATE") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty() || name.contains("/") || name.contains("\\")) {
                    Toast.makeText(this, "Invalid folder name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val folder = File(parent, name)
                if (folder.exists() || !folder.mkdirs()) {
                    Toast.makeText(this, "Could not create folder", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Folder created", Toast.LENGTH_SHORT).show()
                    showVaultCategoryPage(category, parent)
                }
            }.show()
    }

    private fun moveEntry(category: String, entry: File) {
        val root = categoryDir(category)
        val dirs = allFolders(root).filter {
            it.absolutePath != entry.absolutePath &&
                !it.absolutePath.startsWith(entry.absolutePath + File.separator) &&
                it.absolutePath != entry.parentFile?.absolutePath
        }
        if (dirs.isEmpty()) {
            Toast.makeText(this, "Create another folder first", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = dirs.map { root.toPath().relativize(it.toPath()).toString().replace(File.separator, "/") }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Move to other folder").setItems(labels) { _, which ->
            val dest = File(dirs[which], entry.name)
            if (dest.exists()) Toast.makeText(this, "An item with this name already exists", Toast.LENGTH_LONG).show()
            else if (entry.renameTo(dest)) {
                Toast.makeText(this, "Moved successfully", Toast.LENGTH_SHORT).show()
                refreshCurrentPage()
            } else Toast.makeText(this, "Move failed", Toast.LENGTH_LONG).show()
        }.setNegativeButton("CANCEL", null).show()
    }

    private fun allFolders(root: File): List<File> = buildList {
        root.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                add(child)
                addAll(allFolders(child))
            }
        }
    }

    private fun viewEntry(category: String, file: File) {
        when (category) {
            CATEGORY_PHOTO -> previewPhoto(file)
            CATEGORY_AUDIO -> previewAudio(file)
            CATEGORY_VIDEO -> previewVideo(file)
            else -> showFileInfo(file)
        }
    }

    private fun previewAudio(file: File) {
        val player = android.media.MediaPlayer()
        val play = TextView(this).apply {
            text = "▶  PLAY"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(42, 65, 111), Color.rgb(94, 135, 208), 16, 1)
            setPadding(0, dp(16), 0, dp(16))
        }
        try {
            player.setDataSource(file.absolutePath)
            player.prepare()
        } catch (_: Exception) { }
        play.setOnClickListener {
            try {
                if (player.isPlaying) {
                    player.pause()
                    play.text = "▶  PLAY"
                } else {
                    player.start()
                    play.text = "Ⅱ  PAUSE"
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Cannot play this audio", Toast.LENGTH_SHORT).show()
            }
        }
        AlertDialog.Builder(this).setTitle(file.name).setView(play)
            .setPositiveButton("CLOSE") { _, _ -> try { player.release() } catch (_: Exception) { } }
            .setOnDismissListener { try { player.release() } catch (_: Exception) { } }
            .show()
    }

    private fun previewVideo(file: File) {
        val video = android.widget.VideoView(this).apply {
            setVideoURI(Uri.fromFile(file))
            setOnPreparedListener { it.start() }
        }
        AlertDialog.Builder(this).setTitle(file.name).setView(video)
            .setPositiveButton("CLOSE") { _, _ -> video.stopPlayback() }
            .setOnDismissListener { video.stopPlayback() }
            .show()
    }

    private fun showFileInfo(file: File) {
        AlertDialog.Builder(this).setTitle(file.name)
            .setMessage("${formatBytes(file.length())}\nPrivate file")
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun previewPhoto(file: File) {
        val image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageURI(Uri.fromFile(file))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        AlertDialog.Builder(this).setTitle("Private Photo").setView(image)
            .setPositiveButton("CLOSE", null).show()
    }

    private fun startRestore(file: File) {
        pendingRestoreFile = file
        restoreRequest.launch(file.name)
    }

    private fun deletePrivateFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete from Vault?")
            .setMessage("This permanently deletes the private vault item.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                if (file.deleteRecursively()) {
                    Toast.makeText(this, "Deleted from Hidden Vault", Toast.LENGTH_SHORT).show()
                    refreshCurrentPage()
                } else {
                    Toast.makeText(this, "Could not delete this item", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun refreshCurrentPage() {
        when (currentCategory) {
            CATEGORY_AUDIO, CATEGORY_VIDEO, CATEGORY_PHOTO, CATEGORY_FILE -> showVaultCategoryPage(currentCategory, currentDir ?: categoryDir(currentCategory))
            else -> showVaultHome()
        }
    }

    private fun topBar(label: String, back: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@VaultActivity).apply {
            text = "‹"
            textSize = 38f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener { back() }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(56))
        })
        addView(TextView(this@VaultActivity).apply {
            text = label
            textSize = 27f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
        })
    }

    private fun emptyState(icon: String, heading: String, detail: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(30), dp(24), dp(30))
        background = rounded(Color.rgb(15, 23, 45), Color.rgb(66, 87, 136), 20, 1)
        layoutParams = LinearLayout.LayoutParams(-1, dp(190)).apply { setMargins(0, dp(10), 0, dp(10)) }
        addView(TextView(this@VaultActivity).apply { text = icon; textSize = 34f; gravity = Gravity.CENTER })
        addView(TextView(this@VaultActivity).apply { text = heading; textSize = 19f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(4)) })
        addView(TextView(this@VaultActivity).apply { text = detail; textSize = 14f; setTextColor(Color.rgb(170, 183, 214)); gravity = Gravity.CENTER })
    }

    private fun launchPicker(category: String) {
        when (category) {
            CATEGORY_AUDIO -> pickAudio.launch(arrayOf("audio/*"))
            CATEGORY_VIDEO -> pickVideo.launch(arrayOf("video/*"))
            CATEGORY_PHOTO -> pickPhoto.launch(arrayOf("image/*"))
            CATEGORY_FILE -> pickFile.launch(arrayOf("*/*"))
        }
    }

    private fun importItems(uris: List<Uri>?, destination: File, category: String) {
        if (uris.isNullOrEmpty()) return
        currentCategory = category
        val originals = mutableListOf<Uri>()
        var copied = 0
        for (uri in uris) {
            try {
                val name = queryDisplayName(uri) ?: "item_${System.currentTimeMillis()}"
                val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val target = uniqueFile(destination, safe)
                contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    ?: throw IllegalStateException("Cannot read source")
                originals.add(uri)
                copied++
            } catch (_: Exception) {
            }
        }
        if (copied == 0) {
            Toast.makeText(this, "Nothing was imported", Toast.LENGTH_SHORT).show()
            showVaultCategoryPage(category)
            return
        }
        askRemoveOriginals(originals, copied, category)
    }

    private fun askRemoveOriginals(uris: List<Uri>, copied: Int, category: String) {
        currentCategory = category
        AlertDialog.Builder(this)
            .setTitle("Secure Move Complete")
            .setMessage("$copied item(s) copied to the private vault. Hide the original now?")
            .setNegativeButton("CANCEL") { _, _ -> showVaultCategoryPage(category, currentDir ?: categoryDir(category)) }
            .setPositiveButton("HIDE") { _, _ -> removeOriginals(uris) }
            .setCancelable(false)
            .show()
    }

    private fun removeOriginals(uris: List<Uri>) {
        if (uris.isEmpty()) { refreshCurrentPage(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                pendingDeleteUris = uris
                val request = MediaStore.createDeleteRequest(contentResolver, uris)
                deleteRequest.launch(IntentSenderRequest.Builder(request.intentSender).build())
                return
            } catch (_: Exception) {
            }
        }
        var removed = 0
        for (uri in uris) {
            try {
                if (DocumentsContract.isDocumentUri(this, uri)) {
                    if (DocumentsContract.deleteDocument(contentResolver, uri)) removed++
                } else if (contentResolver.delete(uri, null, null) > 0) {
                    removed++
                }
            } catch (_: Exception) {
            }
        }
        Toast.makeText(this, if (removed == uris.size) "Originals hidden successfully" else "Vault saved, but Android kept some originals", Toast.LENGTH_LONG).show()
        refreshCurrentPage()
    }

    private fun categoryDir(category: String): File = when (category) {
        CATEGORY_AUDIO -> audioDir
        CATEGORY_VIDEO -> videoDir
        CATEGORY_PHOTO -> photoDir
        CATEGORY_FILE -> fileDir
        else -> vaultRoot
    }

    private fun categoryLabel(category: String): String = when (category) {
        CATEGORY_AUDIO -> "AUDIO"
        CATEGORY_VIDEO -> "VIDEO"
        CATEGORY_PHOTO -> "PHOTO"
        CATEGORY_FILE -> "FILE"
        else -> "HIDDEN"
    }

    private fun categoryIcon(category: String): String = when (category) {
        CATEGORY_AUDIO -> "🎵"
        CATEGORY_VIDEO -> "🎬"
        CATEGORY_PHOTO -> "🖼"
        CATEGORY_FILE -> "📁"
        else -> "🔒"
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var n = 1
        while (f.exists()) f = File(dir, "${n++}_$name")
        return f
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
    }

    private fun panelRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(28), dp(34), dp(28), dp(28))
        background = GradientDrawable().apply { setColor(Color.rgb(9, 9, 25)) }
    }

    private fun title(value: String) = TextView(this).apply {
        text = value
        textSize = 29f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(18))
    }

    private fun text(value: String) = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(Color.rgb(174, 184, 210))
        setPadding(0, 0, 0, dp(18))
    }

    private fun luxButton(value: String, click: () -> Unit) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(Color.rgb(42, 65, 111), Color.rgb(94, 135, 208), 18, 1)
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, dp(12), 0, 0) }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int, width: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(width), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val PREFS = "audio_vault"
        const val KEY_TYPE = "credential_type"
        const val KEY_HASH = "credential_hash"
        const val KEY_FAILED = "failed_attempts"
        const val KEY_LOCK_UNTIL = "lock_until"
        const val KEY_LAST_IMPORT = "last_import"
        const val TYPE_PATTERN = "pattern"
        const val TYPE_PASSWORD = "password"
        const val TYPE_PIN = "pin"
        const val CATEGORY_HOME = "home"
        const val CATEGORY_AUDIO = "audio"
        const val CATEGORY_VIDEO = "video"
        const val CATEGORY_PHOTO = "photo"
        const val CATEGORY_FILE = "file"
    }
}
