package dev.jasper.ishav2.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import dev.jasper.ishav2.agent.Brain
import dev.jasper.ishav2.ui.components.NeuButton
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaWarn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jasper.ishav2.data.MessageStatus
import dev.jasper.ishav2.data.Part
import dev.jasper.ishav2.data.Role
import dev.jasper.ishav2.data.plainText
import dev.jasper.ishav2.ui.components.ApprovalCard
import dev.jasper.ishav2.ui.components.AssistantMessage
import dev.jasper.ishav2.ui.components.Composer
import dev.jasper.ishav2.ui.components.IshaLogo
import dev.jasper.ishav2.ui.components.NeuIconButton
import dev.jasper.ishav2.ui.components.UserMessage
import dev.jasper.ishav2.ui.theme.IshaCream
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaDanger
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaScrim
import dev.jasper.ishav2.ui.theme.IshaSurfaceHi
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenSettings: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenDeadlines: () -> Unit,
    onOpenMail: () -> Unit
) {
    // Plain collectAsState (not lifecycle-gated): the chat must keep painting
    // tokens while it is on screen, and this screen only exists while visible.
    val conversation by vm.conversation.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val busy by vm.busy.collectAsState()
    val pending by vm.pendingApproval.collectAsState()
    val sharedDraft by vm.sharedDraft.collectAsState()
    val settings by vm.settings.collectAsState()
    val composio by vm.composioStatus.collectAsState()
    val unread by vm.inboxUnread.collectAsState()
    val brain by vm.brain.collectAsState()

    // Recompute the visible turn list on every snapshot. Do not remember it by
    // conversation identity: a stale list is exactly what makes streaming
    // replies only appear after leaving and re-entering the screen.
    val messages = conversation?.messages?.filter { it.role != Role.TOOL }.orEmpty()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerStateCompat()
    val scope = rememberCoroutineScope()
    // Everything about the newest message that changes its height while it
    // streams: text, tool chips appearing, tool status, final status.
    val tail = messages.lastOrNull()
    val tailKey = tail?.let { message ->
        buildString {
            append(message.id).append(':').append(message.parts.size)
            append(':').append(message.plainText().length).append(':').append(message.status)
            message.parts.forEach { part -> if (part is Part.ToolCall) append(part.status.ordinal) }
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { message -> snackbar.showSnackbar(message) }
    }

    // Follow the reply live while the user is at the bottom. Dragging the list
    // pauses following (so reading older messages is never yanked away), and
    // it resumes as soon as a scroll comes to rest at the bottom again.
    // Sending a message or switching chats always jumps to the newest turn.
    val jumpToBottom = remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) {
        if (dragged) follow = false
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (!scrolling) follow = !listState.canScrollForward }
    }
    val viewportHeight by remember { derivedStateOf { listState.layoutInfo.viewportSize.height } }
    val chatId = conversation?.id
    var lastChatId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(chatId, messages.size, tailKey, busy, pending, viewportHeight, jumpToBottom.value) {
        if (messages.isEmpty()) return@LaunchedEffect
        val switched = lastChatId != chatId
        lastChatId = chatId
        if (switched || jumpToBottom.value) {
            jumpToBottom.value = false
            follow = true
        }
        if (follow) listState.scrollToBottom(messages.lastIndex)
    }
    val showJump by remember {
        derivedStateOf { !follow && listState.canScrollForward }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        scrimColor = IshaScrim,
        drawerContent = {
            ChatDrawer(
                conversations = conversations,
                currentId = conversation?.id,
                approvalMode = settings.approvalMode,
                composio = composio,
                unread = unread,
                onNewChat = vm::newChat,
                onOpenChat = vm::openChat,
                onRename = vm::renameConversation,
                onDelete = vm::deleteConversation,
                onSetApproval = vm::setApprovalMode,
                onOpenApps = onOpenApps,
                onOpenInbox = onOpenInbox,
                onOpenModels = onOpenModels,
                onOpenBudget = onOpenBudget,
                onOpenDeadlines = onOpenDeadlines,
                onOpenMail = onOpenMail,
                brain = brain,
                onOpenSettings = onOpenSettings,
                onClose = { scope.launch { drawer.close() } }
            )
        }
    ) {
        Scaffold(
            containerColor = IshaCream,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                ChatTopBar(
                    title = conversation?.title?.ifBlank { "New chat" } ?: "New chat",
                    subtitle = topSubtitle(busy, brain, composio.connected, composio.toolCount, composio.error != null),
                    offline = brain is Brain.Local,
                    onMenu = { scope.launch { drawer.open() } },
                    onBrain = onOpenModels,
                    onNewChat = vm::newChat
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty()) {
                        EmptyChat(
                            brain = brain,
                            onOpenSettings = onOpenSettings,
                            onOpenModels = onOpenModels,
                            onSuggestion = { text ->
                                if (vm.send(text, emptyList())) jumpToBottom.value = true
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                val itemModifier = Modifier.animateItem(
                                    fadeInSpec = tween(260),
                                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    fadeOutSpec = tween(160)
                                )
                                when (message.role) {
                                    Role.USER -> UserMessage(message, itemModifier)
                                    else -> AssistantMessage(message, itemModifier)
                                }
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showJump,
                            enter = fadeIn() + scaleIn(initialScale = 0.7f),
                            exit = fadeOut() + scaleOut(targetScale = 0.7f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        ) {
                            JumpToLatest(
                                streaming = busy,
                                onClick = { jumpToBottom.value = true }
                            )
                        }
                    }
                }

                ApprovalCard(
                    request = pending,
                    onResolve = vm::resolveApproval,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Composer(
                    busy = busy,
                    onSend = { text, parts ->
                        val accepted = vm.send(text, parts)
                        if (accepted) jumpToBottom.value = true
                        accepted
                    },
                    onStop = vm::stop,
                    onError = vm::report,
                    draft = sharedDraft,
                    onDraftTaken = vm::consumeSharedDraft
                )
            }
        }
    }
}

@Composable
private fun rememberDrawerStateCompat(): androidx.compose.material3.DrawerState {
    return androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
}

@Composable
private fun ChatTopBar(
    title: String,
    subtitle: String,
    offline: Boolean,
    onMenu: () -> Unit,
    onBrain: () -> Unit,
    onNewChat: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(IshaCream)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeuIconButton(
                icon = Icons.Rounded.Menu,
                contentDescription = "Open menu",
                onClick = onMenu,
                size = 40.dp
            )
            Spacer(Modifier.width(9.dp))
            IshaLogo(30.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = IshaInk,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            NeuIconButton(
                icon = if (offline) Icons.Rounded.OfflineBolt else Icons.Rounded.Cloud,
                contentDescription = if (offline) "Answering on-device" else "Answering online",
                onClick = onBrain,
                tint = if (offline) IshaWarn else IshaMuted,
                size = 40.dp
            )
            Spacer(Modifier.width(8.dp))
            NeuIconButton(
                icon = Icons.Rounded.AddComment,
                contentDescription = "New chat",
                onClick = onNewChat,
                size = 40.dp
            )
        }
        HorizontalDivider(color = IshaInk.copy(alpha = 0.06f))
    }
}

@Composable
private fun EmptyChat(
    brain: Brain,
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit,
    onSuggestion: (String) -> Unit
) {
    val offline = brain is Brain.Local
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(12.dp))
        WavingAvatar()
        Spacer(Modifier.height(16.dp))
        Text(
            text = greeting(),
            color = IshaInk,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (offline) "I'm running on your phone right now — private, and no signal needed."
            else "Your inbox, calendar, tasks and reminders — just ask.",
            color = IshaMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        if (brain is Brain.Missing) {
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(IshaDanger.copy(alpha = 0.10f))
                    .padding(13.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Give isha a brain to start",
                    color = IshaDanger,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = brain.reason,
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuButton(text = "Add key", onClick = onOpenSettings, accent = true, modifier = Modifier.weight(1f))
                    NeuButton(text = "Go offline", onClick = onOpenModels, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        val suggestions = if (offline) listOf(
            "Help me plan my day in time blocks" to Icons.Rounded.Checklist,
            "Draft a polite message asking to move a meeting" to Icons.Rounded.EditNote,
            "Give me 3 quick ideas for dinner tonight" to Icons.Rounded.Restaurant,
            "How much did I spend this month?" to Icons.Rounded.Savings,
            "Help me study: quiz me on a topic" to Icons.Rounded.School
        ) else listOf(
            "How am I doing on my budget this month?" to Icons.Rounded.Savings,
            "Give me my briefing for today" to Icons.Rounded.WbSunny,
            "Summarise my unread email and what needs a reply" to Icons.Rounded.MailOutline,
            "What's on my calendar this week?" to Icons.Rounded.CalendarMonth,
            "Remind me in 1 hour to take a break" to Icons.Rounded.NotificationsActive,
            "What's due soon in Google Classroom?" to Icons.Rounded.School,
            "Search the web: what's happening today?" to Icons.Rounded.Search
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for ((text, icon) in suggestions) {
                SuggestionRow(text = text, icon = icon, onClick = { onSuggestion(text) })
            }
        }
    }
}

@Composable
private fun WavingAvatar() {
    val loop = rememberInfiniteTransition(label = "avatar")
    val bob by loop.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    Box(
        Modifier.graphicsLayer { translationY = bob * density },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(IshaAccent.copy(alpha = 0.12f))
        )
        IshaLogo(76.dp)
    }
}

@Composable
private fun JumpToLatest(streaming: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(IshaSurfaceHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = IshaAccent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (streaming) "isha is replying" else "Latest",
            color = IshaInk,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when (hour) {
        in 5..11 -> "Good morning ☀️"
        in 12..17 -> "Good afternoon"
        in 18..22 -> "Good evening 🌙"
        else -> "Up late? I'm here"
    }
}

@Composable
private fun SuggestionRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IshaSurfaceHi.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IshaMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = IshaInk,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun topSubtitle(
    busy: Boolean,
    brain: Brain,
    connected: Boolean,
    toolCount: Int,
    hasError: Boolean
): String = when {
    busy -> "typing…"
    brain is Brain.Local -> "Offline · ${brain.model.name}"
    brain is Brain.Missing -> "Not set up yet"
    brain is Brain.Gemini -> if (connected) "Online · Gemini (backup) · $toolCount app tools" else "Online · Gemini (backup)"
    connected -> "Online · Groq · $toolCount app tools ready"
    hasError -> "Online · Groq · apps not connected"
    else -> "Online · Groq"
}

/** Bring the last item in, then scroll to the true end so long replies stay visible. */
private suspend fun LazyListState.scrollToBottom(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
    scrollBy(100_000f)
}
