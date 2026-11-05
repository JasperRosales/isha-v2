package dev.jasper.ishav2.ui.exam

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jasper.ishav2.data.ExamItem
import dev.jasper.ishav2.data.ItemType
import dev.jasper.ishav2.exam.Grading
import dev.jasper.ishav2.ui.components.NeuButton
import dev.jasper.ishav2.ui.components.NeuIconButton
import dev.jasper.ishav2.ui.components.NeuInset
import dev.jasper.ishav2.ui.components.NeuSurface
import dev.jasper.ishav2.ui.components.ScreenTopBar
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaDanger
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaOnAccent
import dev.jasper.ishav2.ui.theme.IshaSuccess
import dev.jasper.ishav2.ui.theme.IshaSurfaceHi

// ---- Taking -------------------------------------------------------------------

@Composable
internal fun ExamTaking(session: ExamSession, vm: ExamViewModel) {
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    BackHandler { confirmExit = true }

    val item = session.current
    val revealed = item.id in session.revealed
    val response = session.responses[item.id].orEmpty()
    val progress by animateFloatAsState(
        (session.index + if (revealed || !session.instantFeedback && response.isNotBlank()) 1f else 0f) / session.items.size,
        tween(350, easing = FastOutSlowInEasing),
        label = "exam progress"
    )

    // A small buzz the moment an answer is revealed (not when revisiting it):
    // a light tick for right, a longer one for wrong.
    var seenRevealed by remember { mutableStateOf(session.revealed) }
    LaunchedEffect(session.revealed) {
        val newly = session.items.firstOrNull { it.id in session.revealed && it.id !in seenRevealed }
        seenRevealed = session.revealed
        if (newly != null) {
            haptics.performHapticFeedback(
                if (Grading.isCorrect(newly, session.responses[newly.id])) HapticFeedbackType.TextHandleMove
                else HapticFeedbackType.LongPress
            )
        }
    }

    fun next() {
        focus.clearFocus()
        if (session.isLast) {
            if (!session.instantFeedback && session.answered < session.items.size) confirmFinish = true else vm.finish()
        } else {
            vm.goTo(session.index + 1)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeuIconButton(icon = Icons.Rounded.Close, contentDescription = "Leave exam", onClick = { confirmExit = true }, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Item ${session.index + 1} of ${session.items.size}", color = IshaInk, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (session.missedOnly) "Practising missed items" else session.title,
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
            TypeChip(item.type)
        }
        LinearProgressIndicator(
            progress = { progress },
            color = IshaAccent,
            trackColor = IshaCreamDeep,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(6.dp)
        )

        // Slide forward or back between items.
        AnimatedContent(
            targetState = session.index,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(260)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(200)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
            },
            label = "exam item",
            modifier = Modifier.weight(1f)
        ) { index ->
            val shown = session.items[index]
            QuestionPage(
                item = shown,
                response = session.responses[shown.id].orEmpty(),
                revealed = shown.id in session.revealed,
                onRespond = { vm.respond(shown, it) },
                onSubmitText = {
                    if (session.instantFeedback && shown.id !in session.revealed) vm.check(shown) else next()
                }
            )
        }

        // Bottom actions.
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (session.index > 0) {
                NeuIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Previous item",
                    onClick = { focus.clearFocus(); vm.goTo(session.index - 1) },
                    size = 48.dp
                )
            }
            val needsCheck = session.instantFeedback && !revealed
            val label = when {
                needsCheck && item.type == ItemType.IDENTIFICATION && response.isNotBlank() -> "Check"
                needsCheck && response.isBlank() -> "Skip"
                needsCheck -> "Check"
                session.isLast -> "See results"
                !session.instantFeedback && response.isBlank() -> "Skip"
                else -> "Next"
            }
            NeuButton(
                text = label,
                onClick = {
                    if (needsCheck && response.isNotBlank()) vm.check(item) else next()
                },
                accent = label != "Skip",
                icon = if (label == "See results") null else Icons.AutoMirrored.Rounded.ArrowForward,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            containerColor = IshaSurfaceHi,
            title = { Text("Leave this exam?") },
            text = { Text("You've answered ${session.answered} of ${session.items.size}. This attempt won't be saved.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; vm.back() }) { Text("Leave", color = IshaDanger) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep going", color = IshaAccent) } }
        )
    }
    if (confirmFinish) {
        val left = session.items.size - session.answered
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            containerColor = IshaSurfaceHi,
            title = { Text("$left item${if (left == 1) "" else "s"} unanswered") },
            text = { Text("Unanswered items count as wrong. Finish anyway?") },
            confirmButton = { TextButton(onClick = { confirmFinish = false; vm.finish() }) { Text("Finish", color = IshaAccent) } },
            dismissButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    val first = session.items.indexOfFirst { session.responses[it.id].isNullOrBlank() }
                    if (first >= 0) vm.goTo(first)
                }) { Text("Go to first", color = IshaMuted) }
            }
        )
    }
}

@Composable
private fun QuestionPage(
    item: ExamItem,
    response: String,
    revealed: Boolean,
    onRespond: (String) -> Unit,
    onSubmitText: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (item.topic.isNotBlank()) {
            Text(item.topic.uppercase(), color = IshaAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Text(item.question, color = IshaInk, style = MaterialTheme.typography.titleLarge.copy(lineHeight = 28.sp))

        when (item.type) {
            ItemType.MULTIPLE_CHOICE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item.choices.forEachIndexed { i, choice ->
                    ChoiceCard(
                        label = ('A' + i).toString(),
                        text = choice,
                        state = choiceState(choice, item.answer, response, revealed),
                        onClick = { onRespond(choice) }
                    )
                }
            }
            ItemType.TRUE_FALSE -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (choice in listOf("True", "False")) {
                    ChoiceCard(
                        label = null,
                        text = choice,
                        state = choiceState(choice, item.answer, response, revealed),
                        onClick = { onRespond(choice) },
                        modifier = Modifier.weight(1f),
                        centered = true
                    )
                }
            }
            ItemType.IDENTIFICATION -> AnswerField(
                value = response,
                enabled = !revealed,
                correct = if (revealed) Grading.isCorrect(item, response) else null,
                onChange = onRespond,
                onDone = onSubmitText
            )
        }

        AnimatedVisibility(revealed, enter = expandVertically(tween(260)) + fadeIn(tween(260))) {
            Feedback(item, response)
        }
    }
}

private enum class ChoiceState { IDLE, SELECTED, RIGHT, WRONG, DIMMED }

private fun choiceState(choice: String, answer: String, response: String, revealed: Boolean): ChoiceState {
    val chosen = choice.equals(response, ignoreCase = true)
    val right = choice.equals(answer, ignoreCase = true)
    return when {
        !revealed -> if (chosen) ChoiceState.SELECTED else ChoiceState.IDLE
        right -> ChoiceState.RIGHT
        chosen -> ChoiceState.WRONG
        else -> ChoiceState.DIMMED
    }
}

@Composable
private fun ChoiceCard(
    label: String?,
    text: String,
    state: ChoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = false
) {
    val accent = when (state) {
        ChoiceState.SELECTED -> IshaAccent
        ChoiceState.RIGHT -> IshaSuccess
        ChoiceState.WRONG -> IshaDanger
        else -> Color.Transparent
    }
    val border by animateColorAsState(accent, tween(220), label = "choice border")
    val fill by animateColorAsState(
        if (state == ChoiceState.IDLE || state == ChoiceState.DIMMED) Color.Transparent else accent.copy(alpha = 0.12f),
        tween(220),
        label = "choice fill"
    )
    val alpha = if (state == ChoiceState.DIMMED) 0.5f else 1f
    val shape = RoundedCornerShape(18.dp)
    NeuSurface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        onClick = onClick,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(fill)
                .border(BorderStroke(2.dp, border), shape)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start
        ) {
            if (label != null) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (state == ChoiceState.IDLE || state == ChoiceState.DIMMED) IshaCreamDeep else accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (state == ChoiceState.IDLE || state == ChoiceState.DIMMED) IshaInk.copy(alpha = alpha) else IshaOnAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text,
                color = IshaInk.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (centered) FontWeight.Bold else FontWeight.Normal,
                modifier = if (centered) Modifier else Modifier.weight(1f)
            )
            if (state == ChoiceState.RIGHT || state == ChoiceState.WRONG) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (state == ChoiceState.RIGHT) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                    contentDescription = if (state == ChoiceState.RIGHT) "Correct answer" else "Your answer",
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AnswerField(value: String, enabled: Boolean, correct: Boolean?, onChange: (String) -> Unit, onDone: () -> Unit) {
    val border = when (correct) {
        true -> IshaSuccess
        false -> IshaDanger
        null -> Color.Transparent
    }
    NeuInset(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, border), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = IshaInk, fontSize = 18.sp),
            cursorBrush = SolidColor(IshaAccent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("Type your answer", color = IshaMuted, fontSize = 18.sp)
                    inner()
                }
            }
        )
    }
}

@Composable
private fun Feedback(item: ExamItem, response: String) {
    val right = Grading.isCorrect(item, response)
    val color = if (right) IshaSuccess else IshaDanger
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (right) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    right -> "Correct!"
                    response.isBlank() -> "Skipped"
                    else -> "Not quite"
                },
                color = color,
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (!right || item.type == ItemType.IDENTIFICATION) {
            Text("Answer: ${item.answer}", color = IshaInk, style = MaterialTheme.typography.labelLarge)
        }
        if (item.explanation.isNotBlank()) {
            Text(item.explanation, color = IshaInk.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---- Results ------------------------------------------------------------------

@Composable
internal fun ExamResults(result: ExamResult, vm: ExamViewModel) {
    var filter by rememberSaveable { mutableStateOf(false) } // false = all, true = missed only
    val shown = if (filter) result.missed else result.items

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Results", subtitle = result.title, onBack = { vm.back() })
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
        ) {
            item { ScoreHero(result) }
            item { TypeBreakdown(result) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (result.missed.isNotEmpty()) {
                        NeuButton(
                            text = "Practice the ${result.missed.size} you missed",
                            onClick = { vm.retake(result, missedOnly = true) },
                            accent = true,
                            icon = Icons.Rounded.Replay,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NeuButton(text = "Retake all", onClick = { vm.retake(result, missedOnly = false) }, modifier = Modifier.weight(1f))
                        NeuButton(text = "Done", onClick = { vm.back() }, modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text("Review", color = IshaInk, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    dev.jasper.ishav2.ui.components.FormChip("All ${result.items.size}", !filter) { filter = false }
                    Spacer(Modifier.width(6.dp))
                    dev.jasper.ishav2.ui.components.FormChip("Missed ${result.missed.size}", filter) { filter = true }
                }
            }
            items(shown, key = { it.id }) { item ->
                ReviewCard(
                    number = result.items.indexOf(item) + 1,
                    item = item,
                    response = result.responses[item.id].orEmpty(),
                    correct = item.id in result.correct,
                    onCountCorrect = { vm.countAsCorrect(item) }
                )
            }
        }
    }
}

@Composable
private fun ScoreHero(result: ExamResult) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(result.attemptId) {
        sweep.animateTo(result.percent / 100f, tween(1100, easing = FastOutSlowInEasing))
    }
    val color = scoreColor(result.percent)
    val track = IshaCreamDeep
    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 14.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(color, -90f, 360f * sweep.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${Math.round(sweep.value * 100)}%", color = IshaInk, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("${result.score} of ${result.items.size}", color = IshaMuted, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    result.percent == 100 -> "Perfect score!"
                    result.percent >= 85 -> "Excellent work!"
                    result.percent >= 70 -> "Nice, almost there"
                    result.percent >= 50 -> "Getting there"
                    else -> "Keep practising, you've got this"
                },
                color = IshaInk,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "${formatDuration(result.seconds)}${if (result.missedOnly) " · missed items only" else ""}",
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun formatDuration(seconds: Int): String =
    if (seconds < 60) "${seconds}s" else "${seconds / 60} min ${seconds % 60}s"

@Composable
private fun TypeBreakdown(result: ExamResult) {
    val rows = ItemType.entries.mapNotNull { type ->
        val items = result.items.filter { it.type == type }
        if (items.isEmpty()) null else Triple(type, items.count { it.id in result.correct }, items.size)
    }
    val topics = result.items.filter { it.topic.isNotBlank() }.groupBy { it.topic.trim() }
        .mapValues { (_, items) -> items.count { it.id in result.correct } to items.size }
        .filter { it.value.second >= 2 }
        .entries.sortedBy { it.value.first.toFloat() / it.value.second }
        .take(3)
    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(15.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((type, right, total) in rows) BreakdownRow(type.label, right, total)
            if (topics.isNotEmpty() && topics.first().value.first < topics.first().value.second) {
                HorizontalDivider(color = IshaInk.copy(alpha = 0.06f))
                Text("Worth reviewing", color = IshaMuted, style = MaterialTheme.typography.labelMedium)
                for ((topic, score) in topics) {
                    if (score.first < score.second) BreakdownRow(topic, score.first, score.second)
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, right: Int, total: Int) {
    val fraction = if (total == 0) 0f else right.toFloat() / total
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, color = IshaInk, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("$right/$total", color = IshaMuted, style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(
            progress = { fraction },
            color = scoreColor(Math.round(fraction * 100)),
            trackColor = IshaCreamDeep,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
    }
}

@Composable
private fun ReviewCard(number: Int, item: ExamItem, response: String, correct: Boolean, onCountCorrect: () -> Unit) {
    val color = if (correct) IshaSuccess else IshaDanger
    NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (correct) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("$number", color = IshaMuted, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(7.dp))
                TypeChip(item.type)
            }
            Text(item.question, color = IshaInk, style = MaterialTheme.typography.bodyMedium)
            if (!correct) {
                Text(
                    if (response.isBlank()) "You skipped this one" else "Your answer: $response",
                    color = IshaDanger,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text("Answer: ${item.answer}", color = IshaSuccess, style = MaterialTheme.typography.labelLarge)
            if (item.explanation.isNotBlank()) {
                Text(item.explanation, color = IshaMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (!correct && item.type == ItemType.IDENTIFICATION && response.isNotBlank()) {
                Text(
                    "I was right, count it",
                    color = IshaAccent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onCountCorrect)
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
        }
    }
}
