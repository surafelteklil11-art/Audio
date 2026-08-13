from pathlib import Path
import subprocess


def commit_if_changed(paths, message):
    subprocess.run(["git", "config", "user.name", "github-actions[bot]"], check=True)
    subprocess.run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], check=True)
    subprocess.run(["git", "add", *paths], check=True)
    if subprocess.run(["git", "diff", "--cached", "--quiet"]).returncode == 0:
        return
    subprocess.run(["git", "commit", "-m", message], check=True)
    subprocess.run(["git", "push", "origin", "HEAD:main"], check=True)


# Preserve the existing MainActivity compatibility repair.
main = Path("app/src/main/java/com/surafel/audio/MainActivity.kt")
s = main.read_text()
if "import kotlin.math.roundToInt" not in s:
    package_line = next(line for line in s.splitlines(True) if line.startswith("package "))
    main.write_text(s.replace(package_line, package_line + "\nimport kotlin.math.roundToInt\n", 1))

# Hidden Vault pickers: use ACTION_OPEN_DOCUMENT with category-specific MIME types.
vault = Path("app/src/main/java/com/surafel/audio/VaultActivity.kt")
s = vault.read_text()
old = '''    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { importItems(it, audioDir, CATEGORY_AUDIO) }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { importItems(it, videoDir, CATEGORY_VIDEO) }
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { importItems(it, photoDir, CATEGORY_PHOTO) }
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importItems(it, fileDir, CATEGORY_FILE) }
'''
new = '''    private val pickAudio = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handlePickerResult(result.data, audioDir, CATEGORY_AUDIO)
    }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handlePickerResult(result.data, videoDir, CATEGORY_VIDEO)
    }
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handlePickerResult(result.data, photoDir, CATEGORY_PHOTO)
    }
    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handlePickerResult(result.data, fileDir, CATEGORY_FILE)
    }
'''
if old in s:
    s = s.replace(old, new, 1)

old = '''    private fun launchPicker(category: String) {
        when (category) {
            CATEGORY_AUDIO -> pickAudio.launch("audio/*")
            CATEGORY_VIDEO -> pickVideo.launch("video/*")
            CATEGORY_PHOTO -> pickPhoto.launch("image/*")
            CATEGORY_FILE -> pickFile.launch(arrayOf("*/*"))
        }
    }
'''
new = '''    private fun launchPicker(category: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            type = when (category) {
                CATEGORY_AUDIO -> "audio/*"
                CATEGORY_VIDEO -> "video/*"
                CATEGORY_PHOTO -> "image/*"
                CATEGORY_FILE -> "application/*"
                else -> "*/*"
            }
            if (category == CATEGORY_FILE) {
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/zip",
                    "text/plain",
                    "text/csv"
                ))
            }
        }
        when (category) {
            CATEGORY_AUDIO -> pickAudio.launch(intent)
            CATEGORY_VIDEO -> pickVideo.launch(intent)
            CATEGORY_PHOTO -> pickPhoto.launch(intent)
            CATEGORY_FILE -> pickFile.launch(intent)
        }
    }

    private fun handlePickerResult(data: Intent?, destination: File, category: String) {
        if (data == null) return
        val uris = buildList {
            data.data?.let(::add)
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri)
            }
        }.distinct()
        importItems(uris, destination, category)
    }
'''
if old in s:
    s = s.replace(old, new, 1)
vault.write_text(s)

# Hidden Vault entry: transparent, touchable, ten taps only on Mine.
hook = Path("app/src/main/java/com/surafel/audio/VaultHookProvider.kt")
h = hook.read_text()
old = '''        dot.setTag(TAG, GateState())
        dot.visibility = View.VISIBLE
        dot.setOnClickListener { view ->
            val state = view.getTag(TAG) as GateState
            state.count++
            if (state.count >= 10) {
                state.count = 0
                activity.startActivity(Intent(activity, VaultActivity::class.java))
            } else if (state.count >= 7) {
                Toast.makeText(activity, "${10 - state.count} more taps", Toast.LENGTH_SHORT).show()
            }
        }

        // MainActivity re-renders the Mine page and resets this gate's visibility.
        // Restore the existing gate after that render without changing its click wiring.
        dot.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (dot.visibility != View.VISIBLE) dot.visibility = View.VISIBLE
            }
        })
'''
new = '''        dot.setTag(TAG, GateState())
        dot.alpha = 0f
        dot.background = null
        dot.text = ""
        dot.contentDescription = null
        dot.setOnClickListener { view ->
            if (activity.findViewById<TextView>(R.id.screenTitle)?.text?.toString() != "Mine") return@setOnClickListener
            val state = view.getTag(TAG) as GateState
            state.count++
            if (state.count >= 10) {
                state.count = 0
                activity.startActivity(Intent(activity, VaultActivity::class.java))
            }
        }

        // Alpha 0 is invisible but remains a real touch target.
        dot.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val mine = activity.findViewById<TextView>(R.id.screenTitle)?.text?.toString() == "Mine"
                dot.visibility = if (mine) View.VISIBLE else View.GONE
                dot.alpha = 0f
            }
        })
'''
if old in h:
    h = h.replace(old, new, 1)
h = h.replace("import android.widget.Toast\n", "import android.widget.TextView\n", 1)
hook.write_text(h)

commit_if_changed(
    [str(main), str(vault), str(hook)],
    "Fix Hidden Vault picker destinations and invisible ten-tap entry [skip ci]",
)
print("Hidden Vault source repair complete")
