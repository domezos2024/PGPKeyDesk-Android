package app.pgpkeydesk.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.pgpkeydesk.R
import app.pgpkeydesk.core.KeyValidator
import app.pgpkeydesk.core.PgpService
import app.pgpkeydesk.core.KeyCheckLevel
import app.pgpkeydesk.data.Clipboard
import app.pgpkeydesk.data.Files
import app.pgpkeydesk.data.RecipientKey
import java.time.Instant

@Composable
fun KeyringScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<RecipientKey?>(null) }
    val now = remember { Instant.now() }

    LaunchedEffect(vm.incoming) {
        vm.incoming?.text?.takeIf { it.contains(KeyValidator.PUBLIC_HEADER) }?.let {
            prefill = it; adding = true; vm.incoming = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (vm.keys.isEmpty()) {
            Text(stringResource(R.string.keyring_empty_long), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(vm.keys, key = { it.id }) { k ->
                val v = remember(k.publicKey) { KeyValidator.validatePublicKey(k.publicKey) }
                val check = remember(v) { PgpService.checkRecipientKey(v, now) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(k.name, style = MaterialTheme.typography.titleMedium)
                        Text(formatFingerprint(k.fingerprint), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (v.isValid) Text(v.keyInfo, style = MaterialTheme.typography.bodySmall)
                        val kind = if (check.level == KeyCheckLevel.BLOCKED) Kind.ERROR else if (check.level == KeyCheckLevel.WARNING) Kind.WARN else Kind.OK
                        StatusLine(Status(if (check.level == KeyCheckLevel.BLOCKED && !v.isValid) ctx.checkText(check) else ctx.describeExpiry(v.expiresAt, now).let { d -> if (v.isRevoked) ctx.getString(R.string.check_revoked) else d }, kind))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { Clipboard.copy(ctx, k.publicKey, sensitive = false) }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy_public_key)) }
                            IconButton(onClick = { delete = k }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { prefill = ""; adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, stringResource(R.string.add_key))
        }
    }

    if (adding) AddKeyDialog(vm, prefill, onDismiss = { adding = false })
    delete?.let { k ->
        AlertDialog(
            onDismissRequest = { delete = null },
            title = { Text(stringResource(R.string.delete_key_title)) },
            text = { Text(stringResource(R.string.delete_key_text, k.name)) },
            confirmButton = { TextButton(onClick = { vm.removeKey(k.id); delete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun AddKeyDialog(vm: AppViewModel, prefill: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf(prefill) }
    var error by remember { mutableStateOf<String?>(null) }
    val trimmed = text.trim()
    val v = remember(trimmed) { if (trimmed.isEmpty()) null else KeyValidator.validatePublicKey(trimmed) }
    val check = remember(v) { v?.let { PgpService.checkRecipientKey(it, Instant.now()) } }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val t = runCatching { Files.readText(ctx, uri) }.getOrNull()
            if (t == null) error = ctx.getString(R.string.err_file_too_large_key) else { text = t; error = null }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_key)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                KeyTextField(text, { text = it; error = null }, stringResource(R.string.public_key), minLines = 5)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { text = Clipboard.paste(ctx); error = null }) { Text(stringResource(R.string.paste)) }
                    OutlinedButton(onClick = { load.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.import_file)) }
                }
                if (v != null && check != null) {
                    if (check.level == KeyCheckLevel.BLOCKED && check.reason != app.pgpkeydesk.core.KeyCheckReason.EXPIRED) StatusLine(Status(ctx.checkText(check), Kind.ERROR))
                    else StatusLine(Status(v.keyInfo + "  ·  " + ctx.describeExpiry(v.expiresAt), expiryKind(v.expiryStatus(Instant.now()))))
                }
                StatusLine(error?.let { Status(it, Kind.ERROR) })
            }
        },
        confirmButton = {
            TextButton(
                enabled = v?.isValid == true && !v.isRevoked && v.hasEncryptionKey,
                onClick = {
                    runCatching { vm.addKey(name, trimmed) }
                        .onSuccess { onDismiss() }
                        .onFailure { error = ctx.errorText(it) }
                }
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
