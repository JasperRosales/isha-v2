package dev.jasper.ishav2.ui.mail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jasper.ishav2.data.MailItem
import dev.jasper.ishav2.ui.components.MarkdownText
import dev.jasper.ishav2.ui.components.NeuSurface
import dev.jasper.ishav2.ui.components.ScreenTopBar
import dev.jasper.ishav2.ui.components.SectionLabel
import dev.jasper.ishav2.ui.components.SyncButton
import dev.jasper.ishav2.ui.components.syncLine
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaCream
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaSurfaceHi
import dev.jasper.ishav2.ui.theme.IshaWarn
import dev.jasper.ishav2.util.relativeTime

/**
 * Recent inbox mail and isha's summary of it, saved on the phone at each sync
 * so it can be read offline.
 */
@Composable
fun MailDigestScreen(vm: MailDigestViewModel, onBack: () -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = IshaCream,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ScreenTopBar(
                title = "Mail digest",
                subtitle = syncLine(data.lastSyncedAt, online) + " · last 2 days",
                onBack = onBack
            ) {
                SyncButton(busy = syncing, onClick = { vm.sync() })
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            data.lastSyncError?.let { error ->
                item(key = "error") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(IshaWarn.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = IshaWarn, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(error, color = IshaInk, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (data.items.isEmpty() && data.summary.isBlank()) {
                item(key = "empty") { EmptyMail(synced = data.lastSyncedAt > 0) }
            }

            if (data.summary.isNotBlank()) {
                item(key = "summary") {
                    NeuSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = IshaAccent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "isha's summary · ${relativeTime(data.summarizedAt)}",
                                    color = IshaMuted,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            if (data.summaryStale && data.items.isNotEmpty()) {
                                Text(
                                    text = "Some newer emails aren't in this summary yet.",
                                    color = IshaMuted,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            MarkdownText(text = data.summary)
                        }
                    }
                }
            }

            if (data.items.isNotEmpty()) {
                item(key = "label") { SectionLabel("Emails · ${data.items.size}") }
                items(data.items, key = { it.id }) { mail ->
                    MailRow(
                        mail = mail,
                        onOpen = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://mail.google.com/mail/u/0/#all/${mail.threadId ?: mail.id}")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MailRow(mail: MailItem, onOpen: () -> Unit) {
    var expanded by rememberSaveable(mail.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(IshaSurfaceHi)
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mail.unread) {
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(IshaAccent)
                )
            }
            Text(
                text = mail.from.ifBlank { "Unknown sender" },
                color = IshaInk,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (mail.unread) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (mail.important) {
                Icon(Icons.Rounded.Star, contentDescription = "Important", tint = IshaWarn, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
            }
            if (mail.receivedAt > 0) {
                Text(relativeTime(mail.receivedAt), color = IshaMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = mail.subject,
            color = IshaInk,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (mail.unread) FontWeight.Medium else FontWeight.Normal,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis
        )
        if (mail.snippet.isNotBlank()) {
            Text(
                text = mail.snippet,
                color = IshaMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Open in Gmail",
                color = IshaAccent,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpen)
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptyMail(synced: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(IshaCreamDeep),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MarkEmailRead, contentDescription = null, tint = IshaMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = if (synced) "No recent mail" else "No digest yet",
            color = IshaInk,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = "Connect Gmail in Apps & connections. isha syncs every couple of hours and keeps a copy here " +
                "you can read offline.",
            color = IshaMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
