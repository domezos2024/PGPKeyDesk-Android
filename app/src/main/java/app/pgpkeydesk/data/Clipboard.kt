package app.pgpkeydesk.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Copies text flagged as sensitive and clears the clipboard again after [CLEAR_SECONDS] seconds. */
object Clipboard {
    const val CLEAR_SECONDS = 60L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pending: Job? = null

    fun copy(context: Context, text: String, label: String = "PGPKeyDesk", sensitive: Boolean = true) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (sensitive && Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        cm.setPrimaryClip(clip)
        pending?.cancel()
        if (!sensitive) return
        pending = scope.launch {
            delay(CLEAR_SECONDS * 1000)
            clearIfStillOurs(cm, text)
        }
    }

    private fun clearIfStillOurs(cm: ClipboardManager, text: String) {
        // From the background Android may hide the clip; it was set by us moments ago, so clearing is the safe default.
        val current = try {
            cm.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (_: Exception) {
            null
        }
        if (current != null && current != text) return
        if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
    }

    fun paste(context: Context): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    }
}
