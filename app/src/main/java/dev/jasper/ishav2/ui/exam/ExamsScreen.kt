package dev.jasper.ishav2.ui.exam

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jasper.ishav2.data.Exam
import dev.jasper.ishav2.data.ExamItem
import dev.jasper.ishav2.data.ItemType
import dev.jasper.ishav2.ui.components.FormField
import dev.jasper.ishav2.ui.components.NeuButton
import dev.jasper.ishav2.ui.components.NeuIconButton
import dev.jasper.ishav2.ui.components.NeuSurface
import dev.jasper.ishav2.ui.components.ScreenTopBar
import dev.jasper.ishav2.ui.components.SectionLabel
import dev.jasper.ishav2.ui.theme.IshaAccent
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

/**
 * Exams made from the student's own notes. One route; the library, the new-exam
 * form, generation, taking and results are stages inside it, so moving between
 * them is a quick in-place animation instead of a navigation.
 */
@Composable
fun ExamsScreen(vm: ExamViewModel, onBack: () -> Unit) {
    val stage by vm.stage.collectAsStateWithLifecycle()
    val exams by vm.exams.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    BackHandler(enabled = stage != ExamStage.Library) { vm.back() }

    Scaffold(
        containerColor = IshaCream,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) }
    ) { padding ->
        AnimatedContent(
            targetState = stage,
            contentKey = { s -> s::class to (s as? ExamStage.Detail)?.examId },
            transitionSpec = {
                val forward = targetState.depth >= initialState.depth
                (slideInHorizontally(tween(280)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(280)))
                    .togetherWith(fadeOut(tween(160)))
            },
            label = "exam stage",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { s ->
            when (s) {
                ExamStage.Library -> ExamLibrary(exams, onBack = onBack, onNew = vm::newExam, onOpen = vm::open)
                ExamStage.Create -> ExamCreate(vm)
                is ExamStage.Generating -> ExamGenerating(s, onCancel = vm::cancelGeneration)
                is ExamStage.Detail -> {
                    val exam = exams.firstOrNull { it.id == s.examId }
                    if (exam != null) ExamDetail(exam, vm) else LaunchedEffect(Unit) { vm.back() }
                }
                is ExamStage.Taking -> ExamTaking(s.session, vm)
                is ExamStage.Results -> ExamResults(s.result, vm)
            }
        }
    }
}

// ---- Library ----------------------------------------------------------------

@Composable
private fun ExamLibrary(exams: List<Exam>, onBack: () -> Unit, onNew: () -> Unit, onOpen: (Exam) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(
                title = "Exams",
                subtitle = if (exams.isEmpty()) "Practice exams from your notes"
                else "${exams.size} exam${if (exams.size == 1) "" else "s"} · made from your notes",
                onBack = onBack
            )
            if (exams.isEmpty()) {
                EmptyLibrary(onNew)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(exams, key = { it.id }) { exam -> ExamCard(exam, onClick = { onOpen(exam) }) }
                }
            }
        }
        if (exams.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = onNew,
                containerColor = IshaAccent,
                contentColor = IshaOnAccent,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New exam") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(18.dp)
            )
        }
    }
}

@Composable
private fun EmptyLibrary(onNew: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(IshaAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.School, contentDescription = null, tint = IshaAccent, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Turn your notes into a practice exam",
            color = IshaInk,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Import Markdown, text, PDF, Word or PowerPoint files, or photos of your notes. " +
                "Choose how many multiple choice, true or false and identification items you want, then take it right here.",
            color = IshaMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        NeuButton(text = "Make an exam", onClick = onNew, accent = true, icon = Icons.Rounded.Add)
    }
}

@Composable
private fun ExamCard(exam: Exam, onClick: () -> Unit) {
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        contentPadding = PaddingValues(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(exam.title, color = IshaInk, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(itemsLine(exam), color = IshaMuted, style = MaterialTheme.typography.labelSmall)
                val last = exam.last
                Text(
                    if (last != null) "Last taken ${relativeTime(last.at)}" else "Not taken yet",
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.width(10.dp))
            val best = exam.best
            if (best != null) ScoreBadge(best.percent) else Icon(Icons.Rounded.PlayArrow, null, tint = IshaAccent)
        }
    }
}

@Composable
internal fun ScoreBadge(percent: Int) {
    val color = scoreColor(percent)
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Text("$percent%", color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
internal fun scoreColor(percent: Int): Color = when {
    percent >= 85 -> IshaSuccess
    percent >= 60 -> IshaWarn
    else -> IshaDanger
}

private fun itemsLine(exam: Exam): String {
    val parts = ItemType.entries.mapNotNull { t -> exam.countOf(t).takeIf { it > 0 }?.let { "${t.short} $it" } }
    return "${exam.items.size} items · ${parts.joinToString(" · ")}"
}

// ---- Detail -------------------------------------------------------------------

@Composable
private fun ExamDetail(exam: Exam, vm: ExamViewModel) {
    var menu by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var showAnswers by rememberSaveable { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<ExamItem?>(null) }
    val missed = exam.lastMissed

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = exam.title,
            subtitle = itemsLine(exam),
            onBack = { vm.back() }
        ) {
            Box {
                NeuIconButton(icon = Icons.Rounded.MoreVert, contentDescription = "More", onClick = { menu = true }, size = 40.dp)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = IshaSurfaceHi) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = IshaInk) },
                        onClick = { menu = false; renaming = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete exam", color = IshaDanger) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = IshaDanger) },
                        onClick = { menu = false; confirmDelete = true }
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.navigationBarsPadding()
        ) {
            item { ScoreSummary(exam) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeuButton(
                        text = if (exam.attempts.isEmpty()) "Start exam · ${exam.items.size} items" else "Take again · ${exam.items.size} items",
                        onClick = { vm.start(exam) },
                        accent = true,
                        enabled = exam.items.isNotEmpty(),
                        icon = Icons.Rounded.PlayArrow,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AnimatedVisibility(missed.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        NeuButton(
                            text = "Practice the ${missed.size} you missed",
                            onClick = { vm.start(exam, missedOnly = true) },
                            icon = Icons.Rounded.Replay,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item {
                NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(15.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToggleRow(
                            "Show answers as you go",
                            if (exam.config.instantFeedback) "Right or wrong, with the explanation, after each item."
                            else "Answers are revealed at the end, like a real exam.",
                            exam.config.instantFeedback
                        ) { vm.setInstantFeedback(exam, it) }
                        ToggleRow("Shuffle items and choices", "A new order every time you take it.", exam.config.shuffle) {
                            vm.setShuffle(exam, it)
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Items", Modifier.weight(1f))
                    TextButton(onClick = { showAnswers = !showAnswers }) {
                        Icon(
                            if (showAnswers) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            null, tint = IshaAccent, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(if (showAnswers) "Hide answers" else "Show answers", color = IshaAccent)
                    }
                    TextButton(onClick = { adding = true }, enabled = exam.sources.isNotEmpty()) {
                        Icon(Icons.Rounded.Add, null, tint = IshaAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add", color = IshaAccent)
                    }
                }
            }
            items(exam.items, key = { it.id }) { item ->
                ItemPreview(
                    number = exam.items.indexOf(item) + 1,
                    item = item,
                    showAnswer = showAnswers,
                    onDelete = { deletingItem = item }
                )
            }
        }
    }

    if (renaming) {
        var title by rememberSaveable { mutableStateOf(exam.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = IshaSurfaceHi,
            title = { Text("Rename exam") },
            text = { FormField(value = title, onValueChange = { title = it }, placeholder = "Exam title") },
            confirmButton = { TextButton(onClick = { vm.rename(exam, title); renaming = false }) { Text("Save", color = IshaAccent) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel", color = IshaMuted) } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = IshaSurfaceHi,
            title = { Text("Delete “${exam.title}”?") },
            text = { Text("Its items, scores and saved source files are removed from this phone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteExam(exam) }) { Text("Delete", color = IshaDanger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = IshaMuted) } }
        )
    }
    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            containerColor = IshaSurfaceHi,
            title = { Text("Remove this item?") },
            text = { Text(item.question, maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton(onClick = { vm.deleteItem(exam, item); deletingItem = null }) { Text("Remove", color = IshaDanger) } },
            dismissButton = { TextButton(onClick = { deletingItem = null }) { Text("Cancel", color = IshaMuted) } }
        )
    }
    if (adding) {
        AddItemsDialog(
            onDismiss = { adding = false },
            onAdd = { counts -> adding = false; vm.addItems(exam, counts) }
        )
    }
}

@Composable
private fun ScoreSummary(exam: Exam) {
    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(15.dp)) {
        val best = exam.best
        val last = exam.last
        if (best == null || last == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.School, null, tint = IshaAccent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Ready when you are", color = IshaInk, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${exam.items.size} items · about ${estimatedMinutes(exam.items)} min",
                        color = IshaMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            return@NeuSurface
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreBadge(best.percent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Best ${best.score}/${best.total}", color = IshaInk, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Last ${last.percent}% ${relativeTime(last.at)} · ${exam.attempts.size} attempt${if (exam.attempts.size == 1) "" else "s"}",
                        color = IshaMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            // Recent attempts, oldest to newest.
            val recent = exam.attempts.sortedBy { it.at }.takeLast(10)
            if (recent.size > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    for (attempt in recent) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height((6 + attempt.percent * 0.38f).dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(scoreColor(attempt.percent).copy(alpha = if (attempt.missedOnly) 0.45f else 0.9f))
                        )
                    }
                }
            }
        }
    }
}

internal fun estimatedMinutes(items: List<ExamItem>): Int {
    val seconds = items.sumOf {
        when (it.type) {
            ItemType.MULTIPLE_CHOICE -> 40
            ItemType.TRUE_FALSE -> 20
            ItemType.IDENTIFICATION -> 35
        }.toInt()
    }
    return (seconds / 60).coerceAtLeast(1)
}

@Composable
private fun ItemPreview(number: Int, item: ExamItem, showAnswer: Boolean, onDelete: () -> Unit) {
    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(start = 13.dp, top = 11.dp, bottom = 11.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$number", color = IshaMuted, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    TypeChip(item.type)
                    if (item.topic.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(item.topic, color = IshaMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(item.question, color = IshaInk, style = MaterialTheme.typography.bodyMedium)
                AnimatedVisibility(showAnswer) {
                    Text("Answer: ${item.answer}", color = IshaSuccess, style = MaterialTheme.typography.labelLarge)
                }
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove item", tint = IshaMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun TypeChip(type: ItemType) {
    Text(
        text = type.short,
        color = IshaAccent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(IshaAccent.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
internal fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = IshaInk, style = MaterialTheme.typography.labelLarge)
            Text(description, color = IshaMuted, style = MaterialTheme.typography.labelSmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = IshaOnAccent,
                checkedTrackColor = IshaAccent,
                uncheckedThumbColor = IshaMuted,
                uncheckedTrackColor = IshaCreamDeep
            )
        )
    }
}

@Composable
private fun AddItemsDialog(onDismiss: () -> Unit, onAdd: (Map<ItemType, Int>) -> Unit) {
    var counts by remember {
        mutableStateOf(mapOf(ItemType.MULTIPLE_CHOICE to 5, ItemType.TRUE_FALSE to 0, ItemType.IDENTIFICATION to 0))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IshaSurfaceHi,
        title = { Text("Add more items") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Written from this exam's notes, different from the items it already has.", color = IshaMuted, style = MaterialTheme.typography.labelSmall)
                for (type in ItemType.entries) {
                    CountSlider(type, counts[type] ?: 0, max = 20) { counts = counts + (type to it) }
                }
            }
        },
        confirmButton = {
            val total = counts.values.sum()
            TextButton(onClick = { onAdd(counts) }, enabled = total > 0) {
                Text("Add $total", color = if (total > 0) IshaAccent else IshaMuted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = IshaMuted) } }
    )
}
