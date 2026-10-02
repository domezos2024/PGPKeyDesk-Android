package app.pgpkeydesk.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pgpkeydesk.R
import app.pgpkeydesk.core.DecryptResult
import app.pgpkeydesk.core.PgpService
import app.pgpkeydesk.core.SignatureStatus
import app.pgpkeydesk.data.Clipboard
import app.pgpkeydesk.data.Files
import app.pgpkeydesk.data.RecipientKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DecryptScreen"

@Composable
fun DecryptScreen(vm: AppViewModel, onOpenProfiles: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cipher by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf<RecipientKey?>(null) }
    var result by remember { mutableStateOf<DecryptResult?>(null) }
    var status by remember { mutableStateOf<Status?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    val snack = LocalSnackbar.current
    val scroll = rememberScrollState()
    LaunchedEffect(result) { if (result != null) { delay(150); scroll.animateScrollTo(scroll.maxValue) } }
    val profile = vm.selectedProfile
    val incoming = vm.incoming

    LaunchedEffect(incoming) {
        incoming?.text?.let {
            cipher = it; result = null; status = null
            vm.incoming = null
        }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { Files.readText(ctx, uri) }.getOrNull()
            if (text == null) status = Status(ctx.getString(R.string.err_file_too_large_key), Kind.ERROR)
            else { cipher = text; result = null; status = null }
        }
    }

    fun decrypt() {
        val p = profile ?: return
        busy = true; status = Status(ctx.getString(R.string.decrypting), Kind.INFO); result = null
        scope.launch {
            try {
                val pw = pass
                Log.d(TAG, "decrypt start thread=${Thread.currentThread().name}")
                val r = withContext(Dispatchers.Default) {
                    PgpService.decryptText(cipher.trim(), p.privateKey, pw, sender?.publicKey)
                }
                result = r
                pass = ""
                status = when (r.signature) {
                    SignatureStatus.VALID -> Status(ctx.getString(R.string.sig_valid, sender?.name.orEmpty()), Kind.OK)
                    SignatureStatus.INVALID -> Status(ctx.getString(R.string.sig_invalid, sender?.name.orEmpty()), Kind.ERROR)
                    SignatureStatus.NOT_SIGNED -> Status(ctx.getString(R.string.sig_not_signed), Kind.WARN)
                    SignatureStatus.NOT_CHECKED -> Status(ctx.getString(R.string.sig_not_checked), Kind.INFO)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "decrypt failed: ${e.javaClass.simpleName}")
                status = Status(ctx.errorText(e), Kind.ERROR)
            } finally {
                busy = false
            }
        }
    }

    Column16(Modifier.verticalScroll(scroll).padding(16.dp)) {
        if (profile == null) WelcomeCard(
            onGenerate = { vm.profilesAction = ProfilesAction.GENERATE },
            onImport = { vm.profilesAction = ProfilesAction.IMPORT }
        )
        if (profile != null) SectionCard(stringResource(R.string.step_message)) {
            KeyTextField(cipher, { cipher = it; result = null }, stringResource(R.string.pgp_message), minLines = 6)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { cipher = Clipboard.paste(ctx); result = null }) { Text(stringResource(R.string.paste)) }
                OutlinedButton(onClick = { openFile.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.open_file)) }
                if (cipher.isNotEmpty()) TextButton(onClick = { cipher = ""; result = null; status = null }) { Text(stringResource(R.string.clear)) }
            }
        }
        if (profile != null) SectionCard(stringResource(R.string.step_unlock)) {
            Text(stringResource(R.string.sender_key_optional), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker = true }) { Text(sender?.name ?: stringResource(R.string.pick_from_keyring)) }
                if (sender != null) TextButton(onClick = { sender = null }) { Text(stringResource(R.string.remove)) }
            }
            PassphraseField(pass, { pass = it }, stringResource(R.string.passphrase), onDone = { if (!busy && profile != null && cipher.isNotBlank()) decrypt() })
            Button(
                onClick = ::decrypt, enabled = !busy && profile != null && cipher.isNotBlank() && pass.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.decrypt)) }
            if (busy) Busy(stringResource(R.string.decrypting)) else StatusLine(status)
        }

        result?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(r.text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        Clipboard.copy(ctx, r.text)
                        scope.launch { snack?.showSnackbar(ctx.getString(R.string.copied_clears, Clipboard.CLEAR_SECONDS)) }
                    }) { Text(stringResource(R.string.copy)) }
                    TextButton(onClick = { result = null }) { Text(stringResource(R.string.clear)) }
                }
            }
        }
    }
    if (picker) KeyPickerDialog(vm.keys, onPick = { sender = it; picker = false; result = null }, onDismiss = { picker = false })
}
