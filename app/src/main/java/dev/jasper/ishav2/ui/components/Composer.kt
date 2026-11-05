package dev.jasper.ishav2.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import dev.jasper.ishav2.agent.SharedDraft
import dev.jasper.ishav2.data.Part
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaDanger
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaOnAccent
import dev.jasper.ishav2.ui.theme.IshaSurfaceHi
import dev.jasper.ishav2.util.FileStore
import dev.jasper.ishav2.util.humanSize
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun Composer(
    busy: Boolean,
    onSend: (String, List<Part>) -> Boolean,
    onStop: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    draft: SharedDraft? = null,
    onDraftTaken: (SharedDraft) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<Part>() }
    var importing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var taskFormOpen by rememberSaveable { mutableStateOf(false) }
    var emailFormOpen by rememberSaveable { mutableStateOf(false) }
    var pendingCamera by remember { mutableStateOf<File?>(null) }

    // Something shared from another app: put it in the box so the user can add
    // what they want isha to do with it before sending.
    LaunchedEffect(draft) {
        val shared = draft ?: return@LaunchedEffect
        if (shared.text.isNotBlank()) {
            text = if (text.isBlank()) shared.text else text.trimEnd() + "\n\n" + shared.text
        }
        attachments += shared.parts
        onDraftTaken(shared)
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            importing = true
            for (uri in uris) {
                runCatching { FileStore.importUri(context, uri) }
                    .onSuccess { attachments += it }
                    .onFailure { onError(it.message ?: "Could not attach that file.") }
            }
            importing = false
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris -> importUris(uris) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> importUris(uris) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCamera
        pendingCamera = null
        if (success && file != null) {
            scope.launch {
                importing = true
                runCatching { FileStore.importCameraFile(context, file) }
                    .onSuccess { attachments += it }
                    .onFailure { onError(it.message ?: "Could not add the photo.") }
                importing = false
            }
        } else {
            file?.delete()
        }
    }

    fun canSend(): Boolean =
        !busy && !importing && (text.isNotBlank() || attachments.isNotEmpty())

    fun send() {
        if (!canSend()) return
        val message = text.trim()
        val parts = attachments.toList()
        // Only clear the composer once the message is actually accepted, so a
        // missing key or a rejected send never silently eats what was typed.
        if (!onSend(message, parts)) return
        attachments.clear()
        text = ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (attachments.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(attachments, key = { attachmentKey(it) }) { part ->
                    AttachmentChip(
                        part = part,
                        onRemove = {
                            attachments.remove(part)
                            FileStore.delete(part)
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                NeuIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Attach",
                    onClick = { menuOpen = true }
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = IshaSurfaceHi
                ) {
                    DropdownMenuItem(
                        text = { Text("Photos") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Image, contentDescription = null, tint = IshaAccent)
                        },
                        onClick = {
                            menuOpen = false
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Camera") },
                        leadingIcon = {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = IshaAccent)
                        },
                        onClick = {
                            menuOpen = false
                            runCatching {
                                val file = FileStore.newCameraFile(context)
                                pendingCamera = file
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.files",
                                    file
                                )
                                cameraLauncher.launch(uri)
                            }.onFailure {
                                pendingCamera = null
                                onError(it.message ?: "Could not open the camera.")
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Task") },
                        leadingIcon = {
                            Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = IshaAccent)
                        },
                        onClick = {
                            menuOpen = false
                            taskFormOpen = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Email") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Email, contentDescription = null, tint = IshaAccent)
                        },
                        onClick = {
                            menuOpen = false
                            emailFormOpen = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("File") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Description,
                                contentDescription = null,
                                tint = IshaAccent
                            )
                        },
                        onClick = {
                            menuOpen = false
                            documentPicker.launch(arrayOf("*/*"))
                        }
                    )
                }
            }

            NeuInset(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        color = IshaInk,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(IshaAccent),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isBlank()) {
                                Text(
                                    text = if (importing) "Adding attachments…" else "Message isha…",
                                    color = IshaMuted,
                                    fontSize = 16.sp
                                )
                            }
                            inner()
                        }
                    }
                )
            }

            if (busy) {
                NeuIconButton(
                    icon = Icons.Rounded.Stop,
                    contentDescription = "Stop",
                    onClick = onStop,
                    tint = IshaDanger
                )
            } else {
                val enabled = canSend()
                NeuSurface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = if (enabled) IshaAccent else IshaCreamDeep,
                    onClick = if (enabled) ({ send() }) else null,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = "Send",
                            tint = if (enabled) IshaOnAccent else IshaMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // The forms fill the message box with a ready prompt, so it can be
    // reviewed or tweaked before sending.
    fun fill(prompt: String) {
        text = if (text.isBlank()) prompt else text.trimEnd() + "\n\n" + prompt
    }

    if (taskFormOpen) {
        TaskFormDialog(
            onDismiss = { taskFormOpen = false },
            onSubmit = { prompt ->
                fill(prompt)
                taskFormOpen = false
            }
        )
    }
    if (emailFormOpen) {
        EmailFormDialog(
            onDismiss = { emailFormOpen = false },
            onSubmit = { prompt ->
                fill(prompt)
                emailFormOpen = false
            }
        )
    }
}

@Composable
private fun AttachmentChip(part: Part, onRemove: () -> Unit) {
    when (part) {
        is Part.Image -> Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(IshaCreamDeep)
        ) {
            AsyncImage(
                model = File(part.path),
                contentDescription = part.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA000000))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        is Part.File -> Row(
            Modifier
                .height(46.dp)
                .widthIn(max = 210.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(IshaCreamDeep)
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = IshaAccent,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = part.name,
                    color = IshaInk,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = humanSize(part.size),
                    color = IshaMuted,
                    fontSize = 10.sp
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove",
                    tint = IshaMuted,
                    modifier = Modifier.size(14.dp),
                    // keeps the chip compact
                )
            }
        }

        else -> Unit
    }
}

private fun attachmentKey(part: Part): String = when (part) {
    is Part.Image -> "image:${part.path}"
    is Part.File -> "file:${part.path}"
    else -> part.hashCode().toString()
}
