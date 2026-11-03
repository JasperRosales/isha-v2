package dev.jasper.ishav2.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted

/** Back arrow, logo, title + subtitle, and optional actions: the header used by isha's tool screens. */
@Composable
fun ScreenTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeuIconButton(icon = Icons.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            IshaLogo(28.dp)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(text = title, color = IshaInk, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = subtitle,
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            actions()
        }
        HorizontalDivider(color = IshaInk.copy(alpha = 0.06f))
    }
}

/** Refresh button that turns into a spinner while [busy]. */
@Composable
fun SyncButton(busy: Boolean, onClick: () -> Unit) {
    if (busy) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = IshaAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
    } else {
        NeuIconButton(icon = Icons.Rounded.Refresh, contentDescription = "Sync now", onClick = onClick, size = 40.dp)
    }
}

/** "Synced 2h ago", "Never synced", with an offline note when it applies. */
fun syncLine(lastSyncedAt: Long, online: Boolean): String {
    val base = if (lastSyncedAt > 0) "Synced ${dev.jasper.ishav2.util.relativeTime(lastSyncedAt)}" else "Never synced"
    return if (online) base else "Offline · $base"
}
