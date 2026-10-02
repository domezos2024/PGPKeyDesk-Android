package app.pgpkeydesk.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object Files {
    const val MAX_KEY_FILE_BYTES = 1_000_000L
    const val MAX_FILE_BYTES = 200L * 1024 * 1024

    data class Info(val name: String, val size: Long)

    fun info(context: Context, uri: Uri): Info {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0) ?: uri.lastPathSegment.orEmpty()
                val size = if (c.isNull(1)) -1L else c.getLong(1)
                return Info(name, size)
            }
        }
        return Info(uri.lastPathSegment.orEmpty(), -1L)
    }

    /** Reads a small text file (keys, armored messages); null if it exceeds [MAX_KEY_FILE_BYTES]. */
    fun readText(context: Context, uri: Uri): String? {
        val size = info(context, uri).size
        if (size > MAX_KEY_FILE_BYTES) return null
        context.contentResolver.openInputStream(uri)?.use { s ->
            val bytes = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                bytes.write(buf, 0, n)
                if (bytes.size() > MAX_KEY_FILE_BYTES) return null
            }
            return bytes.toString(Charsets.UTF_8.name())
        }
        return null
    }

    fun writeText(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("cannot open output")
    }

    fun copyTo(context: Context, source: File, uri: Uri) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out -> source.inputStream().use { it.copyTo(out) } }
            ?: error("cannot open output")
    }

    fun newTemp(context: Context): File {
        val dir = File(context.cacheDir, "work").apply { mkdirs() }
        return File.createTempFile("op", ".tmp", dir)
    }

    fun clearTemp(context: Context) {
        File(context.cacheDir, "work").listFiles()?.forEach { it.delete() }
    }
}
