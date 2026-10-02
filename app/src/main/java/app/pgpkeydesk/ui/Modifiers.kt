package app.pgpkeydesk.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

fun Modifier.clickableItem(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
