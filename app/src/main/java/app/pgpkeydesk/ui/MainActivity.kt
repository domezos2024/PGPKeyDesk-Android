package app.pgpkeydesk.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.os.BundleCompat
import app.pgpkeydesk.BuildConfig
import app.pgpkeydesk.data.AppSettings
import app.pgpkeydesk.data.Files

class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keys and plaintext must not show up in screenshots or the recent-apps preview.
        if (!BuildConfig.DEBUG) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { PgpKeyDeskTheme { AppRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.incoming = Incoming(text = it); return }
                intent.extras?.let { BundleCompat.getParcelable(it, Intent.EXTRA_STREAM, Uri::class.java) }
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        uri ?: return
        val text = runCatching { Files.readText(this, uri) }.getOrNull()
        vm.incoming = if (text != null && text.trimStart().startsWith("-----BEGIN PGP")) Incoming(text = text) else Incoming(file = uri)
    }
}
