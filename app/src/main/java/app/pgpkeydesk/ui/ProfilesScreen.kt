package app.pgpkeydesk.ui

import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.pgpkeydesk.BuildConfig
import app.pgpkeydesk.R
import app.pgpkeydesk.core.KeyValidationResult
import app.pgpkeydesk.core.KeyValidator
import app.pgpkeydesk.core.PgpService
import app.pgpkeydesk.data.Clipboard
import app.pgpkeydesk.data.Files
import app.pgpkeydesk.data.PgpProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

private enum class Sub { LIST, ADD, GENERATE }

private class PendingExport(val text: String, val name: String)

@Composable
fun ProfilesScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var sub by remember { mutableStateOf(Sub.LIST) }
    var prefill by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<PgpProfile?>(null) }
    var exportPrivate by remember { mutableStateOf<PgpProfile?>(null) }
    var pending by remember { mutableStateOf<PendingExport?>(null) }
    var status by remember { mutableStateOf<Status?>(null) }
    val now = remember { Instant.now() }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pgp-keys")) { uri ->
        val p = pending
        if (uri != null && p != null) {
            status = runCatching { Files.writeText(ctx, uri, p.text) }
                .map { Status(ctx.getString(R.string.exported), Kind.OK) }
                .getOrElse { Status(ctx.errorText(it), Kind.ERROR) }
        }
        pending = null
    }

    LaunchedEffect(vm.incoming) {
        vm.incoming?.text?.takeIf { it.contains(KeyValidator.PRIVATE_HEADER) }?.let {
            prefill = it; sub = Sub.ADD; vm.incoming = null
        }
    }
    LaunchedEffect(vm.profilesAction) {
        when (vm.profilesAction) {
            ProfilesAction.GENERATE -> sub = Sub.GENERATE
            ProfilesAction.IMPORT -> sub = Sub.ADD
            null -> {}
        }
        vm.profilesAction = null
    }
    BackHandler(enabled = sub != Sub.LIST) { sub = Sub.LIST }

    when (sub) {
        Sub.ADD -> AddProfilePane(vm, prefill, onDone = { sub = Sub.LIST; prefill = "" })
        Sub.GENERATE -> GeneratePane(vm, onDone = { sub = Sub.LIST })
        Sub.LIST -> Box(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    vm.storageWarning?.let {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(stringResource(R.string.storage_warning, it), color = MaterialTheme.colorScheme.onErrorContainer)
                                TextButton(onClick = vm::dismissStorageWarning) { Text(stringResource(R.string.dismiss)) }
                            }
                        }
                    }
                    StatusLine(status)
                    if (vm.profiles.isEmpty()) {
                        Text(stringResource(R.string.profiles_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                items(vm.profiles, key = { it.id }) { p ->
                    val v = remember(p.privateKey) { KeyValidator.validatePrivateKey(p.privateKey) }
                    var open by remember { mutableStateOf(false) }
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.selectable(p.id == vm.selectedProfileId, role = Role.RadioButton, onClick = { vm.select(p.id) }).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = p.id == vm.selectedProfileId, onClick = null)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                if (v.isValid) {
                                    Text(formatFingerprint(v.fingerprint.takeLast(16)), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(v.keyInfo, style = MaterialTheme.typography.bodySmall)
                                    StatusLine(Status(ctx.describeExpiry(v.expiresAt, now), expiryKind(v.expiryStatus(now))))
                                } else StatusLine(Status(v.errorMessage, Kind.ERROR))
                            }
                            Box {
                                IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                                DropdownMenu(open, { open = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.copy_public_key)) }, onClick = {
                                        open = false; Clipboard.copy(ctx, vm.publicKeyOf(p), sensitive = false)
                                        status = Status(ctx.getString(R.string.copied), Kind.OK)
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.export_public_key)) }, onClick = {
                                        open = false; pending = PendingExport(vm.publicKeyOf(p), "${p.name}-public.asc"); export.launch("${p.name}-public.asc")
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.export_private_key)) }, onClick = { open = false; exportPrivate = p })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { open = false; delete = p })
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.about_line, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                ExtendedFloatingActionButton(onClick = { menu = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.new_profile)) })
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.generate_key_pair)) }, onClick = { menu = false; sub = Sub.GENERATE })
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_profile)) }, onClick = { menu = false; sub = Sub.ADD })
                }
            }
        }
    }

    delete?.let { p ->
        AlertDialog(
            onDismissRequest = { delete = null },
            title = { Text(stringResource(R.string.delete_profile_title)) },
            text = { Text(stringResource(R.string.delete_profile_text, p.name)) },
            confirmButton = { TextButton(onClick = { vm.removeProfile(p.id); delete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    exportPrivate?.let { p ->
        AlertDialog(
            onDismissRequest = { exportPrivate = null },
            title = { Text(stringResource(R.string.export_private_title)) },
            text = { Text(stringResource(R.string.export_private_text)) },
            confirmButton = {
                TextButton(onClick = {
                    exportPrivate = null
                    pending = PendingExport(p.privateKey, "${p.name}-private.asc"); export.launch("${p.name}-private.asc")
                }) { Text(stringResource(R.string.export)) }
            },
            dismissButton = { TextButton(onClick = { exportPrivate = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun PaneHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun AddProfilePane(vm: AppViewModel, prefill: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var priv by remember { mutableStateOf(prefill) }
    var pub by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pv = remember(priv) { if (priv.isBlank()) null else KeyValidator.validatePrivateKey(priv.trim()) }
    val bv = remember(pub) { if (pub.isBlank()) null else KeyValidator.validatePublicKey(pub.trim()) }
    LaunchedEffect(pv) { if (name.isBlank() && pv?.isValid == true) name = pv.userId.ifBlank { name } }

    val loadPriv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { Files.readText(ctx, it) }?.let { priv = it } ?: uri?.let { error = ctx.getString(R.string.err_file_too_large_key) }
    }
    val loadPub = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { Files.readText(ctx, it) }?.let { pub = it } ?: uri?.let { error = ctx.getString(R.string.err_file_too_large_key) }
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        PaneHeader(stringResource(R.string.import_profile), onDone)
        Column16(Modifier.padding(16.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            KeyTextField(priv, { priv = it; error = null }, stringResource(R.string.private_key), minLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { priv = Clipboard.paste(ctx) }) { Text(stringResource(R.string.paste)) }
                OutlinedButton(onClick = { loadPriv.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.import_file)) }
            }
            keyStatus(ctx, pv)
            KeyTextField(pub, { pub = it; error = null }, stringResource(R.string.public_key_optional), minLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pub = Clipboard.paste(ctx) }) { Text(stringResource(R.string.paste)) }
                OutlinedButton(onClick = { loadPub.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.import_file)) }
            }
            keyStatus(ctx, bv)
            StatusLine(error?.let { Status(it, Kind.ERROR) })
            Button(
                onClick = { runCatching { vm.addProfile(name, priv, pub) }.onSuccess { onDone() }.onFailure { error = ctx.errorText(it) } },
                enabled = name.isNotBlank() && pv?.isValid == true && (pub.isBlank() || bv?.isValid == true),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun keyStatus(ctx: android.content.Context, v: KeyValidationResult?) {
    if (v == null) return
    if (v.isValid) StatusLine(Status(v.keyInfo + "  ·  " + ctx.describeExpiry(v.expiresAt), expiryKind(v.expiryStatus(Instant.now()))))
    else StatusLine(Status(v.errorMessage, Kind.ERROR))
}

@Composable
private fun GeneratePane(vm: AppViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var identity by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var bits by remember { mutableStateOf(2048) }
    var years by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var keys by remember { mutableStateOf<app.pgpkeydesk.core.GeneratedKeys?>(null) }
    var status by remember { mutableStateOf<Status?>(null) }
    var pending by remember { mutableStateOf<PendingExport?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pgp-keys")) { uri ->
        val p = pending
        if (uri != null && p != null) {
            status = runCatching { Files.writeText(ctx, uri, p.text) }
                .map { Status(ctx.getString(R.string.exported), Kind.OK) }.getOrElse { Status(ctx.errorText(it), Kind.ERROR) }
        }
        pending = null
    }
    val mismatch = confirm.isNotEmpty() && pass != confirm
    val scroll = rememberScrollState()
    LaunchedEffect(keys) { if (keys != null) scroll.animateScrollTo(0) }
    Column(Modifier.verticalScroll(scroll)) {
        PaneHeader(stringResource(R.string.generate_key_pair), onDone)
        Column16(Modifier.padding(16.dp)) {
            if (keys == null) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(identity, { identity = it }, label = { Text(stringResource(R.string.identity_hint)) }, singleLine = true, enabled = !busy && keys == null, modifier = Modifier.fillMaxWidth())
            PassphraseField(pass, { pass = it }, stringResource(R.string.passphrase), imeAction = androidx.compose.ui.text.input.ImeAction.Next)
            PassphraseField(confirm, { confirm = it }, stringResource(R.string.passphrase_confirm))
            if (mismatch) StatusLine(Status(stringResource(R.string.passphrase_mismatch), Kind.ERROR))
            SectionTitle(stringResource(R.string.key_size))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2048, 4096).forEach { b -> FilterChip(bits == b, { bits = b }, label = { Text("RSA $b") }, enabled = !busy && keys == null) }
            }
            SectionTitle(stringResource(R.string.key_validity))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 2, 5).forEach { y ->
                    FilterChip(years == y, { years = y }, enabled = !busy && keys == null,
                        label = { Text(if (y == 0) stringResource(R.string.validity_never) else stringResource(R.string.validity_years, y)) })
                }
            }
            }
            if (keys == null) {
                Button(
                    enabled = !busy && name.isNotBlank() && identity.isNotBlank() && pass.isNotEmpty() && pass == confirm,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true; status = null
                        scope.launch {
                            try {
                                Log.d("GeneratePane", "generate bits=$bits thread=${Thread.currentThread().name}")
                                keys = withContext(Dispatchers.Default) { PgpService.generateKeyPair(identity.trim(), pass, bits, years * 365) }
                            } catch (e: Throwable) {
                                status = Status(ctx.errorText(e), Kind.ERROR)
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(stringResource(R.string.generate)) }
                if (busy) Busy(stringResource(R.string.generating, bits))
            }
            StatusLine(status)
            keys?.let { k ->
                StatusLine(Status(stringResource(R.string.key_generated), Kind.OK))
                Card(Modifier.fillMaxWidth()) {
                    SelectionContainer { Text(k.publicKey, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Clipboard.copy(ctx, k.publicKey, sensitive = false); status = Status(ctx.getString(R.string.copied), Kind.OK) }) { Text(stringResource(R.string.copy_public_key)) }
                    OutlinedButton(onClick = { pending = PendingExport(k.publicKey, "${name.trim()}-public.asc"); export.launch("${name.trim()}-public.asc") }) { Text(stringResource(R.string.export_public_key)) }
                }
                OutlinedButton(onClick = { pending = PendingExport(k.privateKey, "${name.trim()}-private.asc"); export.launch("${name.trim()}-private.asc") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_private_key))
                }
                Button(
                    onClick = { runCatching { vm.addProfile(name, k.privateKey, k.publicKey) }.onSuccess { onDone() }.onFailure { status = Status(ctx.errorText(it), Kind.ERROR) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.save_profile)) }
            }
        }
    }
}
