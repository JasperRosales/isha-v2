package dev.jasper.ishav2.ui.inbox

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jasper.ishav2.data.InboxItem
import dev.jasper.ishav2.ui.components.IshaLogo
import dev.jasper.ishav2.ui.components.MarkdownText
import dev.jasper.ishav2.ui.components.NeuIconButton
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaCream
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaDanger
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaSuccess
import dev.jasper.ishav2.ui.theme.IshaSurfaceHi
import dev.jasper.ishav2.ui.theme.IshaWarn
import dev.jasper.ishav2.util.relativeTime
import dev.jasper.ishav2.worker.BriefingWorker

@Composable
fun InboxScreen(vm: InboxViewModel, onBack: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = IshaCream,
        topBar = {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeuIconButton(
                        icon = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        size = 40.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    IshaLogo(28.dp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Inbox",
                            color = IshaInk,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (unread > 0) "$unread unread" else "All caught up",
                            color = IshaMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    NeuIconButton(
                        icon = Icons.Rounded.DoneAll,
                        contentDescription = "Mark all read",
                        onClick = vm::markAllRead,
                        size = 40.dp
                    )
                }
                HorizontalDivider(color = IshaInk.copy(alpha = 0.06f))
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            EmptyInbox(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    InboxRow(
                        item = item,
                        onRead = { vm.markRead(item.id) },
                        onDelete = { vm.delete(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InboxRow(item: InboxItem, onRead: () -> Unit, onDelete: () -> Unit) {
    val color = levelColor(item.level)
    // Long items (like the daily briefing) start clipped; tap to read them in full.
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (item.read) IshaSurfaceHi.copy(alpha = 0.55f) else IshaSurfaceHi)
            .clickable {
                onRead()
                expanded = !expanded
            }
            .animateContentSize()
            .padding(13.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = levelIcon(item.level),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title.ifBlank { "isha" },
                    color = IshaInk,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!item.read) {
                    Box(
                        Modifier
                            .padding(start = 6.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(IshaAccent)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            val textColor = if (item.read) IshaMuted else IshaInk
            if (expanded && item.source == BriefingWorker.SOURCE) {
                MarkdownText(text = item.message, color = textColor)
            } else {
                Text(
                    text = if (item.source == BriefingWorker.SOURCE) stripMarkdown(item.message) else item.message,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = relativeTime(item.createdAt),
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Box(
            Modifier
                .padding(start = 4.dp)
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete",
                tint = IshaMuted,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun EmptyInbox(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(IshaCreamDeep),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Inbox,
                contentDescription = null,
                tint = IshaMuted,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Nothing here yet",
            color = IshaInk,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Ask isha to remind you about something and it will show up here.",
            color = IshaMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Collapsed previews show plain text, not raw `**bold**` / `# heading` markers. */
private fun stripMarkdown(text: String): String = text
    .replace(Regex("""(?m)^\s{0,3}#{1,6}\s*"""), "")
    .replace(Regex("""\*\*|__|`"""), "")

@Composable
@ReadOnlyComposable
private fun levelColor(level: String): Color = when (level.lowercase()) {
    "success" -> IshaSuccess
    "warn", "warning" -> IshaWarn
    "error" -> IshaDanger
    else -> IshaAccent
}

private fun levelIcon(level: String): ImageVector = when (level.lowercase()) {
    "success" -> Icons.Rounded.CheckCircle
    "warn", "warning" -> Icons.Rounded.WarningAmber
    else -> Icons.Rounded.Notifications
}
