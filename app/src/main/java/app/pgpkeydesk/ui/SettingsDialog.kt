package app.pgpkeydesk.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pgpkeydesk.R
import app.pgpkeydesk.data.AppSettings
import app.pgpkeydesk.data.ThemeMode

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var theme by remember { mutableStateOf(AppSettings.theme(ctx)) }
    var language by remember { mutableStateOf(AppSettings.language()) }

    @Composable
    fun Option(selected: Boolean, label: String, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected, onClick = null)
            Text(label, Modifier.padding(start = 12.dp))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Option(theme == ThemeMode.SYSTEM, stringResource(R.string.theme_system)) { theme = ThemeMode.SYSTEM; AppSettings.setTheme(ctx, theme) }
                Option(theme == ThemeMode.LIGHT, stringResource(R.string.theme_light)) { theme = ThemeMode.LIGHT; AppSettings.setTheme(ctx, theme) }
                Option(theme == ThemeMode.DARK, stringResource(R.string.theme_dark)) { theme = ThemeMode.DARK; AppSettings.setTheme(ctx, theme) }
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
                Option(language.isEmpty(), stringResource(R.string.language_system)) { language = ""; AppSettings.setLanguage("") }
                Option(language == "en", "English") { language = "en"; AppSettings.setLanguage("en") }
                Option(language == "de", "Deutsch") { language = "de"; AppSettings.setLanguage("de") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } }
    )
}
