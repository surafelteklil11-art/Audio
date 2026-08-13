from pathlib import Path

p = Path("app/src/main/java/com/surafel/audio/VaultActivity.kt")
s = p.read_text()

# Fix malformed folder-name backslash validation from the previous refinement pass.
s = s.replace('name.contains("\\")', 'name.contains("\\\\")')

# Restore handlers removed by the previous source rewrite.
if 'private fun previewPhoto(file: File)' not in s:
    marker = '    private fun refreshCurrentPage()'
    pos = s.find(marker)
    if pos < 0:
        raise SystemExit('missing refreshCurrentPage')
    helpers = '''    private fun previewPhoto(file: File) {
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

'''
    s = s[:pos] + helpers + s[pos:]

# Fix ActivityResultContracts picker signatures. GetMultipleContents.launch() expects
# one MIME-type String; OpenMultipleDocuments.launch() correctly expects String[].
s = s.replace('pickAudio.launch(arrayOf("audio/*"))', 'pickAudio.launch("audio/*")')
s = s.replace('pickVideo.launch(arrayOf("video/*"))', 'pickVideo.launch("video/*")')
s = s.replace('pickPhoto.launch(arrayOf("image/*"))', 'pickPhoto.launch("image/*")')

p.write_text(s)
print('VaultActivity source repaired')
