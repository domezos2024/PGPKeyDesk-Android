package app.pgpkeydesk.ui

import android.content.Intent
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pgpkeydesk.R
import app.pgpkeydesk.core.KeyCheckLevel
import app.pgpkeydesk.core.KeyValidator
import app.pgpkeydesk.core.PgpService
import app.pgpkeydesk.data.Clipboard
import app.pgpkeydesk.data.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

private const val TAG = "EncryptScreen"

@Composable
fun EncryptScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var recipient by rememberSaveable { mutableStateOf("") }
    var plain by rememberSaveable { mutableStateOf("") }
    var sign by rememberSaveable { mutableStateOf(true) }
    var pass by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Status?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    val profile = vm.selectedProfile
    val signing = profile != null && sign

    val trimmed = recipient.trim()
    val validation = remember(trimmed) { if (trimmed.contains("-----BEGIN PGP")) KeyValidator.validatePublicKey(trimmed) else null }
    val check = remember(validation) { validation?.let { PgpService.checkRecipientKey(it, Instant.now()) } }

    val importKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val t = runCatching { Files.readText(ctx, uri) }.getOrNull()
            if (t == null) status = Status(ctx.getString(R.string.err_file_too_large_key), Kind.ERROR) else { recipient = t; status = null }
        }
    }

    fun encrypt() {
        val c = PgpService.checkRecipientKey(trimmed, Instant.now())
        if (c.level == KeyCheckLevel.BLOCKED) { status = Status(ctx.checkText(c), Kind.ERROR); return }
        busy = true; status = Status(ctx.getString(R.string.encrypting), Kind.INFO)
        scope.launch {
            try {
                val pw = pass
                Log.d(TAG, "encrypt start signing=$signing thread=${Thread.currentThread().name}")
                output = withContext(Dispatchers.Default) {
                    PgpService.encryptText(plain, trimmed, if (signing) profile!!.privateKey else null, if (signing) pw else null)
                }
                if (signing) pass = ""
                val done = ctx.getString(if (signing) R.string.encrypted_signed else R.string.encrypted)
                status = if (c.level == KeyCheckLevel.WARNING) Status("$done ${ctx.checkText(c)}", Kind.WARN) else Status(done, Kind.OK)
            } catch (e: Throwable) {
                Log.w(TAG, "encrypt failed: ${e.javaClass.simpleName}")
                status = Status(ctx.errorText(e), Kind.ERROR)
            } finally {
                busy = false
            }
        }
    }

    Column16(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        KeyTextField(recipient, { recipient = it; output = "" }, stringResource(R.string.recipient_public_key), minLines = 4)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker = true }) { Text(stringResource(R.string.keyring)) }
            OutlinedButton(onClick = { recipient = Clipboard.paste(ctx); output = "" }) { Text(stringResource(R.string.paste)) }
            OutlinedButton(onClick = { importKey.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.import_file)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (profile != null) TextButton(onClick = { recipient = vm.publicKeyOf(profile); output = "" }) { Text(stringResource(R.string.use_own_key)) }
            if (check != null && check.level != KeyCheckLevel.BLOCKED) {
                TextButton(onClick = {
                    status = runCatching { vm.addKey(null, trimmed) }
                        .map { Status(ctx.getString(R.string.saved_to_keyring, it.name), Kind.OK) }
                        .getOrElse { Status(ctx.errorText(it), Kind.ERROR) }
                }) { Text(stringResource(R.string.save_to_keyring)) }
            }
        }
        if (validation != null && check != null) {
            if (check.level == KeyCheckLevel.BLOCKED) StatusLine(Status(ctx.checkText(check), Kind.ERROR))
            else StatusLine(Status(validation.keyInfo + "  ·  " + ctx.describeExpiry(validation.expiresAt), if (check.level == KeyCheckLevel.WARNING) Kind.WARN else Kind.OK))
        }

        OutlinedTextField(
            value = plain, onValueChange = { plain = it; output = "" }, label = { Text(stringResource(R.string.plaintext)) },
            minLines = 5, maxLines = 12, modifier = Modifier.fillMaxWidth()
        )
        if (profile != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = sign, onCheckedChange = { sign = it })
                Text(stringResource(R.string.sign_with, profile.name), Modifier.padding(start = 12.dp))
            }
            if (sign) PassphraseField(pass, { pass = it }, stringResource(R.string.passphrase_profile))
        } else {
            StatusLine(Status(stringResource(R.string.not_signed_no_profile), Kind.INFO))
        }
        Button(
            onClick = ::encrypt,
            enabled = !busy && trimmed.isNotEmpty() && plain.isNotEmpty() && check?.level != KeyCheckLevel.BLOCKED && (!signing || pass.isNotEmpty()),
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.encrypt)) }
        if (busy) Busy(stringResource(R.string.encrypting)) else StatusLine(status)

        if (output.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(output, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { Clipboard.copy(ctx, output, sensitive = false) }) { Text(stringResource(R.string.copy)) }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, output) }
                        ctx.startActivity(Intent.createChooser(send, null))
                    }) { Text(stringResource(R.string.share)) }
                    TextButton(onClick = { output = ""; plain = "" }) { Text(stringResource(R.string.clear)) }
                }
            }
        }
    }
    if (picker) KeyPickerDialog(vm.keys, onPick = { recipient = it.publicKey; output = ""; picker = false }, onDismiss = { picker = false })
}
