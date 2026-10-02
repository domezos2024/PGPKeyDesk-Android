package app.pgpkeydesk.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pgpkeydesk.R
import app.pgpkeydesk.core.KeyCheckReason
import app.pgpkeydesk.core.KeyCheckResult
import app.pgpkeydesk.core.KeyExpiry
import app.pgpkeydesk.core.KeyExpiryStatus
import app.pgpkeydesk.core.PgpError
import app.pgpkeydesk.core.PgpException
import app.pgpkeydesk.data.RecipientKey
import java.time.Instant
import java.time.ZoneOffset

enum class Kind { OK, WARN, ERROR, INFO }

data class Status(val text: String, val kind: Kind)

@Composable
fun kindColor(kind: Kind): Color {
    val c = LocalStatusColors.current
    return when (kind) {
        Kind.OK -> c.ok
        Kind.WARN -> c.warn
        Kind.ERROR -> c.error
        Kind.INFO -> c.subtle
    }
}

@Composable
fun StatusLine(status: Status?, modifier: Modifier = Modifier) {
    if (status == null) return
    val color = kindColor(status.kind)
    val icon: ImageVector = when (status.kind) {
        Kind.OK -> Icons.Filled.CheckCircle
        Kind.WARN -> Icons.Filled.Warning
        Kind.ERROR -> Icons.Filled.Error
        Kind.INFO -> Icons.Filled.Info
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp).padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Text(status.text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Busy(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 8.dp))
}

@Composable
fun PassphraseField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, imeAction: ImeAction = ImeAction.Done, onDone: () -> Unit = {}) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, stringResource(R.string.show_hide))
            }
        }
    )
}

@Composable
fun KeyTextField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, minLines: Int = 3, maxLines: Int = 5) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, minLines = minLines, maxLines = maxLines,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
    )
}

/** Groups of four hex digits; a 40-digit fingerprint is split into two lines of five groups. */
fun formatFingerprint(fp: String): String {
    val groups = fp.chunked(4)
    return if (groups.size == 10) groups.take(5).joinToString(" ") + "\n" + groups.drop(5).joinToString(" ") else groups.joinToString(" ")
}

fun Context.describeExpiry(expiresAt: Instant?, now: Instant = Instant.now()): String {
    val date = expiresAt?.atZone(ZoneOffset.UTC)?.toLocalDate()?.toString().orEmpty()
    return when (KeyExpiry.evaluate(expiresAt, now)) {
        KeyExpiryStatus.NEVER_EXPIRES -> getString(R.string.key_never_expires)
        KeyExpiryStatus.EXPIRED -> getString(R.string.key_expired_on, date)
        KeyExpiryStatus.EXPIRING_SOON -> getString(R.string.key_expires_in, KeyExpiry.daysLeft(expiresAt!!, now).toInt(), date)
        KeyExpiryStatus.VALID -> getString(R.string.key_valid_until, date)
    }
}

fun expiryKind(status: KeyExpiryStatus) = when (status) {
    KeyExpiryStatus.EXPIRED -> Kind.ERROR
    KeyExpiryStatus.EXPIRING_SOON -> Kind.WARN
    else -> Kind.OK
}

fun Context.checkText(c: KeyCheckResult, now: Instant = Instant.now()): String = when (c.reason) {
    KeyCheckReason.INVALID -> getString(R.string.check_invalid, c.detail)
    KeyCheckReason.REVOKED -> getString(R.string.check_revoked)
    KeyCheckReason.NO_ENCRYPTION_KEY -> getString(R.string.check_no_enc_key)
    KeyCheckReason.EXPIRED, KeyCheckReason.EXPIRING_SOON -> getString(R.string.check_recipient_expiry, describeExpiry(c.expiresAt, now))
    KeyCheckReason.NONE -> ""
}

fun Context.errorText(e: Throwable): String = when ((e as? PgpException)?.error) {
    PgpError.WRONG_PASSPHRASE -> getString(R.string.err_wrong_pass)
    PgpError.NO_MATCHING_KEY -> getString(R.string.err_no_key)
    PgpError.NOT_ENCRYPTED -> getString(R.string.err_not_encrypted)
    PgpError.INTEGRITY -> getString(R.string.err_integrity)
    PgpError.KEY_UNUSABLE -> getString(R.string.err_key_unusable)
    PgpError.NO_SIGNING_KEY -> getString(R.string.err_no_signing)
    else -> e.message ?: e.javaClass.simpleName
}

@Composable
fun KeyPickerDialog(keys: List<RecipientKey>, onPick: (RecipientKey) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_from_keyring)) },
        text = {
            if (keys.isEmpty()) {
                Text(stringResource(R.string.keyring_empty))
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(keys, key = { it.id }) { k ->
                        ListItem(
                            headlineContent = { Text(k.name) },
                            supportingContent = { Text(formatFingerprint(k.fingerprint.takeLast(16)), fontFamily = FontFamily.Monospace) },
                            modifier = Modifier.clickableItem { onPick(k) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun Column16(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}


/** Shows short feedback ("Copied") in the app-wide snackbar. */
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** First-run hint: explains why a profile is needed and offers the two ways to get one. */
@Composable
fun WelcomeCard(onGenerate: () -> Unit, onImport: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.welcome_text), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGenerate) { Text(stringResource(R.string.generate_key_pair)) }
                OutlinedButton(onClick = onImport) { Text(stringResource(R.string.import_profile)) }
            }
        }
    }
}
