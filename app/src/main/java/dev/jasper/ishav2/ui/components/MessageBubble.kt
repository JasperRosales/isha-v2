package dev.jasper.ishav2.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.jasper.ishav2.data.ChatMessage
import dev.jasper.ishav2.data.MessageStatus
import dev.jasper.ishav2.data.Part
import dev.jasper.ishav2.data.ToolStatus
import dev.jasper.ishav2.data.plainText
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaAccentDeep
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaDanger
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaOnAccent
import dev.jasper.ishav2.ui.theme.IshaSuccess
import dev.jasper.ishav2.ui.theme.IshaSurfaceHi
import dev.jasper.ishav2.ui.theme.IshaWarn
import dev.jasper.ishav2.util.humanSize
import java.io.File

@Composable
fun UserMessage(message: ChatMessage, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val attachments = message.parts.filter { it is Part.Image || it is Part.File }
        if (attachments.isNotEmpty()) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.widthIn(max = 260.dp)
            ) {
                for (part in attachments) AttachmentCard(part, own = true)
            }
        }
        val text = message.parts.filterIsInstance<Part.Text>()
            .filterNot { it.thought }
            .joinToString("") { it.text }
        if (text.isNotBlank()) {
            Box(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                    .background(Brush.linearGradient(listOf(IshaAccent, IshaAccentDeep)))
                    .padding(horizontal = 15.dp, vertical = 11.dp)
            ) {
                Text(
                    text = text,
                    color = IshaOnAccent,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun AssistantMessage(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    speaking: Boolean = false,
    /** Read this reply aloud, or stop if it is already being read. Null hides the button. */
    onSpeak: (() -> Unit)? = null
) {
    val calls = message.parts.filterIsInstance<Part.ToolCall>()
    val text = message.plainText()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        IshaLogo(26.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (text.isNotBlank() || (calls.isEmpty() && message.status != MessageStatus.ERROR)) {
                NeuSurface(
                    shape = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (text.isNotBlank()) {
                            MarkdownText(text)
                        }
                        if (message.status == MessageStatus.STREAMING && text.isBlank()) {
                            ThinkingDots()
                        }
                        if (message.status == MessageStatus.STREAMING && text.isNotBlank()) {
                            StreamingCaret()
                        }
                        if (message.status == MessageStatus.STOPPED) {
                            Text(
                                text = "Stopped.",
                                color = IshaMuted,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
            for (call in calls) {
                ToolCallChip(call)
            }
            message.error?.let { error ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IshaDanger.copy(alpha = 0.10f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = IshaDanger,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = error,
                        color = IshaDanger,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            val brain = message.brain
            val onDevice = brain != null && brain.startsWith("On-device") && message.status != MessageStatus.STREAMING
            val canSpeak = onSpeak != null && text.isNotBlank() && message.status != MessageStatus.STREAMING
            if (onDevice || canSpeak) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canSpeak) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onSpeak!!)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (speaking) Icons.Rounded.StopCircle else Icons.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = if (speaking) IshaAccent else IshaMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (speaking) "Stop" else "Listen",
                                color = if (speaking) IshaAccent else IshaMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    if (onDevice) {
                        Text(
                            text = "⚡ ${brain!!.replaceFirst("On-device", "answered on-device")}",
                            color = IshaMuted,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentCard(part: Part, own: Boolean) {
    when (part) {
        is Part.Image -> {
            val file = File(part.path)
            Box(
                Modifier
                    .height(150.dp)
                    .widthIn(max = 240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(IshaCreamDeep)
            ) {
                AsyncImage(
                    model = file,
                    contentDescription = part.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        is Part.File -> {
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (own) IshaOnAccent.copy(alpha = 0.14f) else IshaCreamDeep)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.InsertDriveFile,
                    contentDescription = null,
                    tint = if (own) IshaOnAccent else IshaInk,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = part.name,
                    color = if (own) IshaOnAccent else IshaInk,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 170.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = humanSize(part.size),
                    color = if (own) IshaOnAccent.copy(alpha = 0.75f) else IshaMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun ToolCallChip(call: Part.ToolCall) {
    var showDialog by remember { mutableStateOf(false) }
    val color = statusColor(call.status)
    val icon = statusIcon(call.status)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(IshaCreamDeep.copy(alpha = 0.75f))
            .clickable { showDialog = true }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = prettyToolName(call.name),
            color = IshaInk,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 210.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = statusLabel(call.status),
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = IshaSurfaceHi,
            title = {
                Text(prettyToolName(call.name), style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("INPUT", style = MaterialTheme.typography.labelSmall, color = IshaMuted)
                    Text(
                        text = call.argsJson.ifBlank { "{}" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = IshaInk
                    )
                    val result = call.resultJson
                    if (!result.isNullOrBlank()) {
                        Text("RESULT", style = MaterialTheme.typography.labelSmall, color = IshaMuted)
                        Text(
                            text = result,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (call.status == ToolStatus.ERROR) IshaDanger else IshaInk
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "dot-alpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(IshaAccent.copy(alpha = alpha * (1f - index * 0.18f)))
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "thinking",
            color = IshaMuted,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** A small pulsing caret so a streaming reply visibly keeps updating. */
@Composable
private fun StreamingCaret() {
    val transition = rememberInfiniteTransition(label = "streaming")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse),
        label = "caret-alpha"
    )
    Text(
        text = "▍",
        color = IshaAccent.copy(alpha = alpha),
        style = MaterialTheme.typography.bodyLarge
    )
}

private fun prettyToolName(name: String): String = name
    .replace(Regex("^COMPOSIO_"), "")
    .replace('_', ' ')
    .lowercase()
    .replaceFirstChar { it.uppercase() }

@Composable
@ReadOnlyComposable
private fun statusColor(status: ToolStatus): Color = when (status) {
    ToolStatus.OK -> IshaSuccess
    ToolStatus.ERROR, ToolStatus.DENIED -> IshaDanger
    ToolStatus.WAITING_APPROVAL -> IshaWarn
    ToolStatus.RUNNING -> IshaAccent
    ToolStatus.PENDING -> IshaMuted
}

private fun statusIcon(status: ToolStatus): ImageVector = when (status) {
    ToolStatus.OK -> Icons.Rounded.CheckCircle
    ToolStatus.ERROR -> Icons.Rounded.ErrorOutline
    ToolStatus.DENIED -> Icons.Rounded.Block
    ToolStatus.WAITING_APPROVAL -> Icons.Rounded.HourglassTop
    ToolStatus.RUNNING -> Icons.Rounded.PlayArrow
    ToolStatus.PENDING -> Icons.Rounded.Stop
}

private fun statusLabel(status: ToolStatus): String = when (status) {
    ToolStatus.OK -> "done"
    ToolStatus.ERROR -> "failed"
    ToolStatus.DENIED -> "denied"
    ToolStatus.WAITING_APPROVAL -> "waiting"
    ToolStatus.RUNNING -> "running"
    ToolStatus.PENDING -> "queued"
}
