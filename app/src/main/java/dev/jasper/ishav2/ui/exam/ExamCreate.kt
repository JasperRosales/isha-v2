package dev.jasper.ishav2.ui.exam

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jasper.ishav2.data.Difficulty
import dev.jasper.ishav2.data.ExamConfig
import dev.jasper.ishav2.data.ExamSourceFile
import dev.jasper.ishav2.data.ItemType
import dev.jasper.ishav2.exam.ExamSources
import dev.jasper.ishav2.ui.components.FormChip
import dev.jasper.ishav2.ui.components.FormField
import dev.jasper.ishav2.ui.components.IshaLogo
import dev.jasper.ishav2.ui.components.NeuButton
import dev.jasper.ishav2.ui.components.NeuSurface
import dev.jasper.ishav2.ui.components.ScreenTopBar
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaWarn
import dev.jasper.ishav2.util.humanSize
import java.io.File

private val LANGUAGES = listOf("" to "Same as notes", "English" to "English", "Filipino" to "Filipino")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExamCreate(vm: ExamViewModel) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val config = draft.config

    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importUris(it) }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { vm.importUris(it) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "New exam", subtitle = "From your notes or files", onBack = { vm.back() })
        LazyColumn(
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .imePadding()
        ) {
            item {
                Card(title = "Material", subtitle = "Markdown, text, PDF, Word, PowerPoint or photos. Mix as many as you like.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NeuButton(
                            text = "Import files",
                            onClick = { files.launch(ExamSources.PICKER_TYPES) },
                            icon = Icons.Rounded.UploadFile,
                            modifier = Modifier.weight(1f)
                        )
                        NeuButton(
                            text = "Photos",
                            onClick = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            icon = Icons.Rounded.Image,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (file in draft.files) SourceRow(file, onRemove = { vm.removeFile(file) })
                        if (draft.importing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = IshaAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Adding…", color = IshaMuted, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    FormField(
                        value = draft.pasted,
                        onValueChange = { text -> vm.editDraft { it.copy(pasted = text) } },
                        placeholder = "…or paste your notes here",
                        singleLine = false,
                        maxLines = 8
                    )
                }
            }

            item {
                Card(title = "Title") {
                    FormField(
                        value = draft.title,
                        onValueChange = { text -> vm.editDraft { it.copy(title = text) } },
                        placeholder = "e.g. Biology – Cell structure"
                    )
                }
            }

            item {
                Card(
                    title = "Items",
                    subtitle = "${config.total} item${if (config.total == 1) "" else "s"} · about ${minutesFor(config)} min"
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Preset("Quick 10", config, 5, 3, 2, vm)
                        Preset("Standard 20", config, 10, 5, 5, vm)
                        Preset("Long 40", config, 20, 10, 10, vm)
                        Preset("All MC 25", config, 25, 0, 0, vm)
                    }
                    for (type in ItemType.entries) {
                        CountSlider(type, config.counts[type] ?: 0, max = ExamConfig.MAX_PER_TYPE) { vm.setCount(type, it) }
                    }
                    if (config.total >= ExamConfig.MAX_TOTAL) {
                        Text("${ExamConfig.MAX_TOTAL} items is the most for one exam.", color = IshaWarn, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            item {
                Card(title = "Style") {
                    Label("Difficulty")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (d in Difficulty.entries) {
                            FormChip(d.label, config.difficulty == d) { vm.editDraft { it.copy(config = it.config.copy(difficulty = d)) } }
                        }
                    }
                    Label("Language")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((value, label) in LANGUAGES) {
                            FormChip(label, config.language == value) { vm.editDraft { it.copy(config = it.config.copy(language = value)) } }
                        }
                    }
                    Label("Focus (optional)")
                    FormField(
                        value = config.focus,
                        onValueChange = { text -> vm.editDraft { it.copy(config = it.config.copy(focus = text)) } },
                        placeholder = "e.g. chapter 3, the Krebs cycle, dates"
                    )
                    ToggleRow(
                        "Show answers as you go",
                        "Off: answers are revealed at the end, like a real exam.",
                        config.instantFeedback
                    ) { on -> vm.editDraft { it.copy(config = it.config.copy(instantFeedback = on)) } }
                    ToggleRow("Shuffle items and choices", "A new order every time you take it.", config.shuffle) { on ->
                        vm.editDraft { it.copy(config = it.config.copy(shuffle = on)) }
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            NeuButton(
                text = when {
                    !draft.hasSource -> "Add notes or a file first"
                    config.total == 0 -> "Choose at least one item"
                    else -> "Write ${config.total} items"
                },
                onClick = vm::generate,
                accent = draft.canGenerate,
                enabled = draft.canGenerate,
                icon = Icons.Rounded.AutoAwesome,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun minutesFor(config: ExamConfig): Int {
    val s = (config.counts[ItemType.MULTIPLE_CHOICE] ?: 0) * 40 +
        (config.counts[ItemType.TRUE_FALSE] ?: 0) * 20 +
        (config.counts[ItemType.IDENTIFICATION] ?: 0) * 35
    return (s / 60).coerceAtLeast(1)
}

@Composable
private fun Preset(label: String, config: ExamConfig, mc: Int, tf: Int, id: Int, vm: ExamViewModel) {
    val selected = config.counts[ItemType.MULTIPLE_CHOICE] == mc &&
        config.counts[ItemType.TRUE_FALSE] == tf &&
        config.counts[ItemType.IDENTIFICATION] == id
    FormChip(label, selected) { vm.applyPreset(mc, tf, id) }
}

@Composable
internal fun CountSlider(type: ItemType, value: Int, max: Int, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(type.label, color = IshaInk, style = MaterialTheme.typography.labelLarge)
                Text(
                    when (type) {
                        ItemType.MULTIPLE_CHOICE -> "4 choices, one correct"
                        ItemType.TRUE_FALSE -> "Is the statement right?"
                        ItemType.IDENTIFICATION -> "Type the term, name or number"
                    },
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                "$value",
                color = if (value > 0) IshaAccent else IshaMuted,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(Math.round(it)) },
            valueRange = 0f..max.toFloat(),
            steps = max - 1,
            colors = SliderDefaults.colors(
                thumbColor = IshaAccent,
                activeTrackColor = IshaAccent,
                inactiveTrackColor = IshaCreamDeep,
                activeTickColor = IshaAccent.copy(alpha = 0f),
                inactiveTickColor = IshaCreamDeep.copy(alpha = 0f)
            )
        )
    }
}

@Composable
private fun SourceRow(file: ExamSourceFile, onRemove: () -> Unit) {
    val kind = ExamSources.kindOf(file)
    val icon: ImageVector = when {
        file.mime.startsWith("image/") -> Icons.Rounded.Image
        file.mime == "application/pdf" || file.name.endsWith(".pdf", true) -> Icons.Rounded.PictureAsPdf
        kind == ExamSources.Kind.PPTX -> Icons.Rounded.Slideshow
        else -> Icons.Rounded.Description
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(IshaCreamDeep)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = IshaAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(file.name, color = IshaInk, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                humanSize(File(file.path).length()) + if (kind == ExamSources.Kind.MEDIA) " · read by Gemini" else "",
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove ${file.name}", tint = IshaMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Card(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(15.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(title, color = IshaInk, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, color = IshaMuted, style = MaterialTheme.typography.labelSmall)
            }
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = IshaMuted, style = MaterialTheme.typography.labelMedium)
}

// ---- Generating --------------------------------------------------------------

@Composable
internal fun ExamGenerating(stage: ExamStage.Generating, onCancel: () -> Unit) {
    val p = stage.progress
    val target = if (p.total > 0) p.made.toFloat() / p.total else 0f
    val progress by animateFloatAsState(target, tween(600), label = "progress")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "logo"
    )
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.scale(pulse)) { IshaLogo(84.dp) }
        Spacer(Modifier.height(22.dp))
        Text(
            if (stage.appendTo != null) "Adding items" else "Writing your exam",
            color = IshaInk,
            style = MaterialTheme.typography.titleLarge
        )
        Text(stage.title, color = IshaMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 2)
        Spacer(Modifier.height(22.dp))
        if (p.total > 0 && p.made > 0) {
            LinearProgressIndicator(
                progress = { progress },
                color = IshaAccent,
                trackColor = IshaCreamDeep,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        } else {
            LinearProgressIndicator(
                color = IshaAccent,
                trackColor = IshaCreamDeep,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                p.note != null -> p.note
                p.total == 0 -> "Reading your material…"
                p.made == 0 -> "Writing questions…"
                else -> "${p.made} of ${p.total} items written"
            },
            color = IshaMuted,
            style = MaterialTheme.typography.labelMedium
        )
        AnimatedVisibility(p.total > 0) {
            Text(
                "Every item is checked: answers must match a choice, and repeats are dropped.",
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(26.dp))
        NeuButton(text = "Cancel", onClick = onCancel)
    }
}
