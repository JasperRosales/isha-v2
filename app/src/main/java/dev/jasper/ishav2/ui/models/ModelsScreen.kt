package dev.jasper.ishav2.ui.models

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jasper.ishav2.data.BrainMode
import dev.jasper.ishav2.local.DownloadState
import dev.jasper.ishav2.local.InstalledModel
import dev.jasper.ishav2.local.LocalModelCatalog
import dev.jasper.ishav2.local.LocalModelSpec
import dev.jasper.ishav2.ui.components.IshaLogo
import dev.jasper.ishav2.ui.components.NeuButton
import dev.jasper.ishav2.ui.components.NeuIconButton
import dev.jasper.ishav2.ui.components.NeuInset
import dev.jasper.ishav2.ui.components.NeuSurface
import dev.jasper.ishav2.ui.components.SectionLabel
import dev.jasper.ishav2.ui.components.StatusChip
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaAccentSoft
import dev.jasper.ishav2.ui.theme.IshaCream
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaDanger
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaOnAccent
import dev.jasper.ishav2.ui.theme.IshaSuccess
import dev.jasper.ishav2.ui.theme.IshaWarn
import dev.jasper.ishav2.util.humanSize

@Composable
fun ModelsScreen(vm: ModelsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var token by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }

    val imported = installed.filter { model -> LocalModelCatalog.models.none { it.id == model.id } }

    Scaffold(
        containerColor = IshaCream,
        snackbarHost = { SnackbarHost(snackbar) },
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
                    Text(
                        text = "Offline brain",
                        color = IshaInk,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(
                        text = if (online) "Online" else "Offline",
                        color = if (online) IshaSuccess else IshaWarn
                    )
                }
                HorizontalDivider(color = IshaInk.copy(alpha = 0.06f))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionLabel("How isha thinks") }
            item {
                NeuSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrainOption(
                            selected = settings.brainMode == BrainMode.AUTO,
                            title = "Automatic",
                            body = "Gemini + your apps when online, the on-device model when there's no signal.",
                            onClick = { vm.setBrainMode(BrainMode.AUTO) }
                        )
                        BrainOption(
                            selected = settings.brainMode == BrainMode.ONLINE,
                            title = "Always online",
                            body = "Only Gemini. Needs internet; full access to Composio apps and the web.",
                            onClick = { vm.setBrainMode(BrainMode.ONLINE) }
                        )
                        BrainOption(
                            selected = settings.brainMode == BrainMode.OFFLINE,
                            title = "Always offline",
                            body = "Everything stays on this phone. Private and free, but no apps or web.",
                            onClick = { vm.setBrainMode(BrainMode.OFFLINE) }
                        )
                    }
                }
            }

            item {
                SectionLabel("Models for your assistant")
            }
            item {
                Text(
                    text = "Picked for everyday help (planning, drafting, summaries), not coding. " +
                        "Downloads continue in the background; Wi-Fi is recommended. This phone has about ${vm.deviceRamGb} GB of RAM.",
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(LocalModelCatalog.models, key = { it.id }) { spec ->
                val model = installed.firstOrNull { it.id == spec.id }
                CatalogCard(
                    spec = spec,
                    installed = model,
                    download = downloads[spec.id],
                    active = settings.localModelId == spec.id,
                    lowRam = vm.deviceRamGb in 1 until spec.minRamGb,
                    missingToken = spec.needsToken && settings.huggingFaceToken.isBlank(),
                    onDownload = { vm.download(spec.id) },
                    onCancel = { vm.cancel(spec.id) },
                    onUse = { vm.select(spec.id) },
                    onDelete = { confirmDelete = spec.id }
                )
            }

            item { SectionLabel("Hugging Face token") }
            item {
                NeuSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(15.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Only needed for Gemma. Accept the license on the model page, then paste a read token from huggingface.co/settings/tokens.",
                            color = IshaMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                        NeuInset(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp)
                        ) {
                            BasicTextField(
                                value = token,
                                onValueChange = { token = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = IshaInk),
                                cursorBrush = SolidColor(IshaAccent),
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    Box {
                                        if (token.isEmpty()) {
                                            Text(
                                                text = if (settings.huggingFaceToken.isNotBlank()) "Saved · paste to replace" else "hf_…",
                                                color = IshaMuted,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeuButton(
                                text = "Save",
                                onClick = {
                                    vm.saveToken(token)
                                    token = ""
                                },
                                accent = true,
                                enabled = token.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            )
                            NeuButton(
                                text = "Gemma license",
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://huggingface.co/litert-community/Gemma3-1B-IT")
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item { SectionLabel("Your own models") }
            items(imported, key = { it.id }) { model ->
                ImportedCard(
                    model = model,
                    active = settings.localModelId == model.id,
                    onUse = { vm.select(model.id) },
                    onDelete = { confirmDelete = model.id }
                )
            }
            item {
                NeuButton(
                    text = if (importing) "Importing…" else "Import a .task model file",
                    icon = Icons.Rounded.FileOpen,
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    text = "Any MediaPipe LLM .task bundle works (for example from huggingface.co/litert-community).",
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }

    confirmDelete?.let { id ->
        val name = installed.firstOrNull { it.id == id }?.name ?: "this model"
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = IshaCream,
            title = { Text("Delete $name?", color = IshaInk) },
            text = { Text("You can download it again later.", color = IshaMuted) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(id)
                    confirmDelete = null
                }) { Text("Delete", color = IshaDanger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BrainOption(selected: Boolean, title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) IshaAccentSoft.copy(alpha = 0.7f) else IshaCreamDeep.copy(alpha = 0f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) IshaAccent else IshaMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(11.dp))
        Column {
            Text(text = title, color = IshaInk, style = MaterialTheme.typography.titleSmall)
            Text(text = body, color = IshaMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CatalogCard(
    spec: LocalModelSpec,
    installed: InstalledModel?,
    download: DownloadState?,
    active: Boolean,
    lowRam: Boolean,
    missingToken: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit
) {
    NeuSurface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(15.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = spec.name,
                    color = IshaInk,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Tag(spec.tag)
            }
            Text(text = spec.blurb, color = IshaMuted, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "${humanSize(spec.sizeBytes)} · ${spec.maxTokens} token memory · ${spec.minRamGb} GB+ RAM",
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall
            )
            if (lowRam) {
                Text(
                    text = "This phone may not have enough memory to run it smoothly.",
                    color = IshaWarn,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            download?.error?.let { Text(text = it, color = IshaDanger, style = MaterialTheme.typography.labelSmall) }
            Spacer(Modifier.height(2.dp))
            when {
                download?.downloading == true -> {
                    LinearProgressIndicator(
                        progress = { download.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = IshaAccent,
                        trackColor = IshaCreamDeep,
                        strokeCap = StrokeCap.Round
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${humanSize(download.bytes)} of ${humanSize(download.total)} · ${(download.fraction * 100).toInt()}%",
                            color = IshaMuted,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onCancel) { Text("Cancel", color = IshaDanger) }
                    }
                }
                installed != null -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeuButton(
                        text = if (active) "In use" else "Use offline",
                        icon = if (active) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                        onClick = onUse,
                        accent = !active,
                        enabled = !active,
                        modifier = Modifier.weight(1f)
                    )
                    NeuIconButton(
                        icon = Icons.Rounded.Delete,
                        contentDescription = "Delete model",
                        onClick = onDelete,
                        tint = IshaDanger,
                        size = 44.dp
                    )
                }
                else -> NeuButton(
                    text = if (missingToken) "Add a token below to download" else "Download",
                    icon = Icons.Rounded.Download,
                    onClick = onDownload,
                    accent = true,
                    enabled = !missingToken,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ImportedCard(
    model: InstalledModel,
    active: Boolean,
    onUse: () -> Unit,
    onDelete: () -> Unit
) {
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(15.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = model.name, color = IshaInk, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Imported · ${humanSize(model.file.length())}",
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NeuButton(
                    text = if (active) "In use" else "Use offline",
                    onClick = onUse,
                    accent = !active,
                    enabled = !active,
                    modifier = Modifier.weight(1f)
                )
                NeuIconButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "Delete model",
                    onClick = onDelete,
                    tint = IshaDanger,
                    size = 44.dp
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(IshaAccent)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = IshaOnAccent, style = MaterialTheme.typography.labelSmall)
    }
}
