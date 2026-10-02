package app.pgpkeydesk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pgpkeydesk.R
import app.pgpkeydesk.core.KeyExpiryStatus
import app.pgpkeydesk.core.KeyValidator

private enum class Tab(val label: Int, val icon: ImageVector) {
    DECRYPT(R.string.tab_decrypt, Icons.Filled.LockOpen),
    ENCRYPT(R.string.tab_encrypt, Icons.Filled.Lock),
    FILES(R.string.tab_files, Icons.Filled.Folder),
    KEYRING(R.string.tab_keyring, Icons.Filled.Key),
    PROFILES(R.string.tab_profiles, Icons.Filled.AccountCircle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.DECRYPT) }
    val holder = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm.profilesAction) { if (vm.profilesAction != null) tab = Tab.PROFILES }

    LaunchedEffect(vm.incoming) {
        val inc = vm.incoming ?: return@LaunchedEffect
        tab = when {
            inc.file != null -> Tab.FILES
            inc.text?.contains(KeyValidator.PUBLIC_HEADER) == true -> Tab.KEYRING
            inc.text?.contains(KeyValidator.PRIVATE_HEADER) == true -> Tab.PROFILES
            else -> Tab.DECRYPT
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(stringResource(R.string.app_name))
                    Text(
                        vm.selectedProfile?.let { stringResource(R.string.active_profile, it.name) } ?: stringResource(R.string.no_active_profile),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }, actions = {
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(stringResource(t.label), maxLines = 1) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).imePadding().fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            CompositionLocalProvider(LocalSnackbar provides snackbar) {
                Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                    holder.SaveableStateProvider(tab.name) {
                        when (tab) {
                            Tab.DECRYPT -> DecryptScreen(vm, onOpenProfiles = { tab = Tab.PROFILES })
                            Tab.ENCRYPT -> EncryptScreen(vm)
                            Tab.FILES -> FilesScreen(vm, onOpenProfiles = { tab = Tab.PROFILES })
                            Tab.KEYRING -> KeyringScreen(vm)
                            Tab.PROFILES -> ProfilesScreen(vm)
                        }
                    }
                }
            }
        }
    }

    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })

    if (vm.expiryWarnings.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = vm::dismissExpiryWarnings,
            title = { Text(stringResource(R.string.expiry_title)) },
            text = {
                Column {
                    vm.expiryWarnings.forEach {
                        val line = if (it.status == KeyExpiryStatus.EXPIRED) ctx.getString(R.string.key_expired_on, it.expiresAt.toString().take(10))
                        else ctx.describeExpiry(it.expiresAt)
                        Text("${it.name}: $line", modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissExpiryWarnings) { Text(stringResource(R.string.ok)) } }
        )
    }
}
