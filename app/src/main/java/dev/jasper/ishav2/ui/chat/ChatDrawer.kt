package dev.jasper.ishav2.ui.chat

import dev.jasper.ishav2.agent.Brain

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jasper.ishav2.data.ApprovalMode
import dev.jasper.ishav2.data.ComposioKeyMode
import dev.jasper.ishav2.data.ConversationSummary
import dev.jasper.ishav2.net.ComposioStatus
import dev.jasper.ishav2.ui.components.IshaLogo
import dev.jasper.ishav2.ui.components.NeuButton
import dev.jasper.ishav2.ui.components.SectionLabel
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaAccentSoft
import dev.jasper.ishav2.ui.theme.IshaCream
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaDanger
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaOnAccent
import dev.jasper.ishav2.ui.theme.IshaSuccess
import dev.jasper.ishav2.ui.theme.IshaSurfaceHi
import dev.jasper.ishav2.ui.theme.IshaWarn
import dev.jasper.ishav2.util.relativeTime

@Composable
fun ChatDrawer(
    conversations: List<ConversationSummary>,
    currentId: String?,
    approvalMode: ApprovalMode,
    composio: ComposioStatus,
    unread: Int,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSetApproval: (ApprovalMode) -> Unit,
    onOpenApps: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenBudget: () -> Unit,
    brain: Brain,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    var renaming by remember { mutableStateOf<ConversationSummary?>(null) }
    var deleting by remember { mutableStateOf<ConversationSummary?>(null) }
    var showAutoConfirm by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = IshaCream
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IshaLogo(42.dp)
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text(
                            text = "isha",
                            color = IshaInk,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "private assistant",
                            color = IshaMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.padding(top = 4.dp))
                NeuButton(
                    text = "New chat",
                    onClick = {
                        onNewChat()
                        onClose()
                    },
                    icon = Icons.Rounded.AddComment,
                    accent = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Chats", Modifier.weight(1f))
                    Text(
                        text = statusLine(composio),
                        color = when {
                            composio.connected -> IshaSuccess
                            composio.error != null -> IshaDanger
                            else -> IshaMuted
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (conversations.isEmpty()) {
                item {
                    Text(
                        text = "Nothing here yet. Start with a question or a task.",
                        color = IshaMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            }

            items(conversations, key = { it.id }) { chat ->
                ChatRow(
                    chat = chat,
                    selected = chat.id == currentId,
                    onOpen = {
                        onOpenChat(chat.id)
                        onClose()
                    },
                    onRename = { renaming = chat },
                    onDelete = { deleting = chat }
                )
            }

            item {
                Spacer(Modifier.padding(top = 6.dp))
                HorizontalDivider(color = IshaInk.copy(alpha = 0.08f))
                Spacer(Modifier.padding(top = 6.dp))
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(IshaSurfaceHi.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Auto-approve actions",
                            color = IshaInk,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = if (approvalMode == ApprovalMode.AUTO)
                                "isha can act without asking"
                            else "Ask me before sending or changing things",
                            color = if (approvalMode == ApprovalMode.AUTO) IshaWarn else IshaMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(
                        checked = approvalMode == ApprovalMode.AUTO,
                        onCheckedChange = { enabled ->
                            if (enabled) showAutoConfirm = true
                            else onSetApproval(ApprovalMode.MANUAL)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = IshaOnAccent,
                            checkedTrackColor = IshaAccent,
                            uncheckedThumbColor = IshaMuted,
                            uncheckedTrackColor = IshaCreamDeep
                        )
                    )
                }
            }

            item {
                DrawerAction(
                    icon = Icons.Rounded.Apps,
                    title = "Apps & connections",
                    subtitle = statusLine(composio),
                    onClick = {
                        onOpenApps()
                        onClose()
                    }
                )
            }
            item {
                DrawerAction(
                    icon = Icons.Rounded.Inbox,
                    title = "Inbox",
                    subtitle = if (unread > 0) "$unread unread" else "All caught up",
                    badge = unread,
                    onClick = {
                        onOpenInbox()
                        onClose()
                    }
                )
            }
            item {
                DrawerAction(
                    icon = Icons.Rounded.Savings,
                    title = "Budget",
                    subtitle = "Track spending · works offline",
                    onClick = {
                        onOpenBudget()
                        onClose()
                    }
                )
            }
            item {
                DrawerAction(
                    icon = Icons.Rounded.OfflineBolt,
                    title = "Offline brain",
                    subtitle = when (brain) {
                        is Brain.Local -> "Answering on-device · ${brain.model.name}"
                        Brain.Gemini -> "Online now · offline models ready to use"
                        is Brain.Missing -> "Set up a brain to start"
                    },
                    onClick = {
                        onOpenModels()
                        onClose()
                    }
                )
            }
            item {
                DrawerAction(
                    icon = Icons.Rounded.Settings,
                    title = "Settings",
                    subtitle = "Keys, model, approvals",
                    onClick = {
                        onOpenSettings()
                        onClose()
                    }
                )
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = IshaMuted,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Keys stay encrypted on this phone",
                        color = IshaMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }

    renaming?.let { chat ->
        RenameDialog(
            initial = chat.title,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                onRename(chat.id, title)
                renaming = null
            }
        )
    }

    deleting?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = IshaSurfaceHi,
            title = { Text("Delete chat?") },
            text = {
                Text(
                    text = "\"${chat.title}\" will be removed from this phone. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(chat.id)
                    deleting = null
                }) {
                    Text("Delete", color = IshaDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }

    if (showAutoConfirm) {
        AlertDialog(
            onDismissRequest = { showAutoConfirm = false },
            containerColor = IshaSurfaceHi,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = IshaWarn
                )
            },
            title = { Text("Let isha act without asking?") },
            text = {
                Text(
                    text = "Sending mail, creating events, posting, deleting and running code will happen without an approval card. Only enable this if you trust the connected apps.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetApproval(ApprovalMode.AUTO)
                    showAutoConfirm = false
                }) {
                    Text("Enable", color = IshaAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoConfirm = false }) { Text("Keep asking") }
            }
        )
    }
}

@Composable
private fun ChatRow(
    chat: ConversationSummary,
    selected: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) IshaAccentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onOpen)
            .padding(start = 11.dp, top = 9.dp, bottom = 9.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = chat.title,
                color = IshaInk,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = relativeTime(chat.updatedAt),
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Box {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { menu = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Chat options",
                    tint = IshaMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = IshaSurfaceHi
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = IshaInk) },
                    onClick = {
                        menu = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = IshaDanger) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = IshaDanger) },
                    onClick = {
                        menu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun DrawerAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: Int = 0,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IshaInk,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = IshaInk,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = subtitle,
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (badge > 0) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(IshaAccent)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge.toString(),
                    color = IshaOnAccent,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IshaSurfaceHi,
        title = { Text("Rename chat") },
        text = {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = { value = it.take(80) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = IshaInk),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(IshaAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(IshaCreamDeep)
                    .padding(12.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun statusLine(status: ComposioStatus): String = when {
    status.connecting -> "connecting…"
    status.connected -> {
        val mode = when (status.mode) {
            ComposioKeyMode.CONNECT -> "Connect"
            ComposioKeyMode.PROJECT -> "project key"
            else -> "Composio"
        }
        "${status.toolCount} tools · $mode"
    }
    status.error != null -> "not connected"
    status.configured -> "not connected"
    else -> "Composio off"
}
