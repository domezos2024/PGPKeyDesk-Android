package app.pgpkeydesk.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import app.pgpkeydesk.R
import app.pgpkeydesk.core.KeyCheckLevel
import app.pgpkeydesk.core.PgpService
import app.pgpkeydesk.core.SignatureStatus
import app.pgpkeydesk.data.Files
import app.pgpkeydesk.data.RecipientKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

private const val TAG = "FilesScreen"

private enum class Op { ENCRYPT, DECRYPT, SIGN, VERIFY }

@Composable
fun FilesScreen(vm: AppViewModel, onOpenProfiles: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf<Uri?>(null) }
    var info by remember { mutableStateOf<Files.Info?>(null) }
    var recipient by remember { mutableStateOf<RecipientKey?>(null) }
    var senderOrSigner by remember { mutableStateOf<RecipientKey?>(null) }
    var pickFor by remember { mutableStateOf<Int?>(null) } // 0 = recipient, 1 = sender / signer
    var sign by remember { mutableStateOf(false) }
    var armor by remember { mutableStateOf(false) }
    var pass by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Status?>(null) }
    var busy by remember { mutableStateOf(false) }
    var outFile by remember { mutableStateOf<File?>(null) }
    var outName by remember { mutableStateOf("") }
    val profile = vm.selectedProfile

    fun setInput(uri: Uri) {
        val i = Files.info(ctx, uri)
        if (i.size > Files.MAX_FILE_BYTES) {
            status = Status(ctx.getString(R.string.err_file_too_large, Files.MAX_FILE_BYTES / (1024 * 1024)), Kind.ERROR)
            return
        }
        input = uri; info = i; status = null; outFile?.delete(); outFile = null
    }

    LaunchedEffect(vm.incoming) {
        vm.incoming?.file?.let { setInput(it); vm.incoming = null }
    }
    DisposableEffect(Unit) { onDispose { Files.clearTemp(ctx) } }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::setInput) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val f = outFile
        if (uri != null && f != null) {
            status = runCatching { Files.copyTo(ctx, f, uri) }
                .map { f.delete(); outFile = null; Status(ctx.getString(R.string.file_saved), Kind.OK) }
                .getOrElse { Status(ctx.errorText(it), Kind.ERROR) }
        }
    }

    fun run(op: Op) {
        val uri = input ?: return
        val baseName = info?.name.orEmpty().ifEmpty { "file" }
        val priv = profile?.privateKey
        val pw = pass
        val rec = recipient
        val other = senderOrSigner
        if (op == Op.ENCRYPT && rec != null) {
            val c = PgpService.checkRecipientKey(rec.publicKey, Instant.now())
            if (c.level == KeyCheckLevel.BLOCKED) { status = Status(ctx.checkText(c), Kind.ERROR); return }
        }
        busy = true; status = Status(ctx.getString(R.string.working), Kind.INFO)
        outFile?.delete(); outFile = null
        scope.launch {
            val tmp = Files.newTemp(ctx)
            try {
                Log.d(TAG, "op=$op size=${info?.size} thread=${Thread.currentThread().name}")
                val msg: Status = withContext(Dispatchers.Default) {
                    ctx.contentResolver.openInputStream(uri)!!.use { ins ->
                        when (op) {
                            Op.ENCRYPT -> {
                                tmp.outputStream().buffered().use { out ->
                                    PgpService.encrypt(ins, out, rec!!.publicKey, if (sign) priv else null, if (sign) pw else null, armor, baseName)
                                }
                                Status(ctx.getString(if (sign) R.string.file_encrypted_signed else R.string.file_encrypted), Kind.OK)
                            }
                            Op.DECRYPT -> {
                                val st = tmp.outputStream().buffered().use { out ->
                                    PgpService.decrypt(ins, out, priv!!, pw, other?.publicKey)
                                }
                                when (st) {
                                    SignatureStatus.VALID -> Status(ctx.getString(R.string.file_decrypted) + " " + ctx.getString(R.string.sig_valid, other?.name.orEmpty()), Kind.OK)
                                    SignatureStatus.INVALID -> Status(ctx.getString(R.string.file_decrypted) + " " + ctx.getString(R.string.sig_invalid, other?.name.orEmpty()), Kind.ERROR)
                                    SignatureStatus.NOT_SIGNED -> Status(ctx.getString(R.string.file_decrypted) + " " + ctx.getString(R.string.sig_not_signed), Kind.WARN)
                                    SignatureStatus.NOT_CHECKED -> Status(ctx.getString(R.string.file_decrypted), Kind.OK)
                                }
                            }
                            Op.SIGN -> {
                                tmp.outputStream().buffered().use { out -> PgpService.sign(ins, out, priv!!, pw, armor = true, fileName = baseName) }
                                Status(ctx.getString(R.string.file_signed), Kind.OK)
                            }
                            Op.VERIFY -> {
                                val ok = PgpService.verify(ins, other!!.publicKey)
                                Status(
                                    if (ok) ctx.getString(R.string.sig_valid, other.name) else ctx.getString(R.string.sig_invalid, other.name),
                                    if (ok) Kind.OK else Kind.ERROR
                                )
                            }
                        }
                    }
                }
                status = msg
                if (op != Op.VERIFY && msg.kind != Kind.ERROR) {
                    outFile = tmp
                    outName = when (op) {
                        Op.ENCRYPT -> baseName + if (armor) ".asc" else ".pgp"
                        Op.DECRYPT -> baseName.removeSuffix(".pgp").removeSuffix(".gpg").removeSuffix(".asc").let { if (it == baseName) "$baseName.decrypted" else it }
                        else -> "$baseName.asc"
                    }
                    if (op != Op.DECRYPT || msg.kind != Kind.ERROR) pass = ""
                } else tmp.delete()
            } catch (e: Throwable) {
                Log.w(TAG, "op failed: ${e.javaClass.simpleName}")
                tmp.delete()
                status = Status(ctx.errorText(e), Kind.ERROR)
            } finally {
                busy = false
            }
        }
    }

    val hasFile = input != null
    val hasPriv = profile != null
    Column16(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text(info?.let { "${it.name}  (${formatSize(it.size)})" } ?: stringResource(R.string.select_file))
        }
        SectionTitle(stringResource(R.string.recipient))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickFor = 0 }) { Text(recipient?.name ?: stringResource(R.string.pick_from_keyring)) }
            if (recipient != null) TextButton(onClick = { recipient = null }) { Text(stringResource(R.string.remove)) }
        }
        SectionTitle(stringResource(R.string.sender_or_signer))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickFor = 1 }) { Text(senderOrSigner?.name ?: stringResource(R.string.pick_from_keyring)) }
            if (senderOrSigner != null) TextButton(onClick = { senderOrSigner = null }) { Text(stringResource(R.string.remove)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(armor, { armor = it }); Text(stringResource(R.string.ascii_armor))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(sign, { sign = it }, enabled = hasPriv); Text(stringResource(R.string.also_sign))
        }
        if (!hasPriv) {
            StatusLine(Status(stringResource(R.string.no_profile_hint), Kind.INFO))
            TextButton(onClick = onOpenProfiles) { Text(stringResource(R.string.open_profiles)) }
        } else {
            PassphraseField(pass, { pass = it }, stringResource(R.string.passphrase_profile_named, profile!!.name))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = { run(Op.ENCRYPT) }, enabled = !busy && hasFile && recipient != null && (!sign || pass.isNotEmpty()), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.encrypt)) }
            FilledTonalButton(onClick = { run(Op.DECRYPT) }, enabled = !busy && hasFile && hasPriv && pass.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.decrypt)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = { run(Op.SIGN) }, enabled = !busy && hasFile && hasPriv && pass.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sign)) }
            FilledTonalButton(onClick = { run(Op.VERIFY) }, enabled = !busy && hasFile && senderOrSigner != null, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.verify)) }
        }
        if (busy) Busy(stringResource(R.string.working)) else StatusLine(status)
        outFile?.let {
            Button(onClick = { save.launch(outName) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_result, outName)) }
        }
    }
    pickFor?.let { which ->
        KeyPickerDialog(vm.keys, onPick = { if (which == 0) recipient = it else senderOrSigner = it; pickFor = null }, onDismiss = { pickFor = null })
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 0 -> "?"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
