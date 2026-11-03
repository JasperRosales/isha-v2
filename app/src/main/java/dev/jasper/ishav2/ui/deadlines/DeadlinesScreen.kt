package dev.jasper.ishav2.ui.deadlines

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jasper.ishav2.data.Deadline
import dev.jasper.ishav2.data.DeadlineGroup
import dev.jasper.ishav2.data.DeadlineStore
import dev.jasper.ishav2.ui.components.FormChip
import dev.jasper.ishav2.ui.components.FormField
import dev.jasper.ishav2.ui.components.ScreenTopBar
import dev.jasper.ishav2.ui.components.SectionLabel
import dev.jasper.ishav2.ui.components.SyncButton
import dev.jasper.ishav2.ui.components.syncLine
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Everything due, from Google Classroom (synced every few hours) and added by
 * hand. Reads the copy saved on the phone, so it works offline; reminders are
 * scheduled on the phone too.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeadlinesScreen(vm: DeadlinesViewModel, onBack: () -> Unit, onOpenChat: () -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var adding by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = data.items.firstOrNull { it.id == selectedId }

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    // Re-group once a minute so "Today" rolls into "Overdue" on its own.
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            now = ZonedDateTime.now()
        }
    }
    val groups = remember(data.items, now) { DeadlineStore.grouped(data.items, now) }
    val openCount = groups.filter { it.first != DeadlineGroup.DONE }.sumOf { it.second.size }

    Scaffold(
        containerColor = IshaCream,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                containerColor = IshaAccent,
                contentColor = IshaOnAccent,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add") },
                modifier = Modifier.navigationBarsPadding()
            )
        },
        topBar = {
            ScreenTopBar(
                title = "Due",
                subtitle = syncLine(data.lastSyncedAt, online) + " · works offline",
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
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data.lastSyncError?.let { error ->
                item(key = "error") { Banner(error) }
            }
            if (openCount == 0) {
                item(key = "empty") { EmptyState(hasAny = data.items.isNotEmpty()) }
            }
            for ((group, list) in groups) {
                val isDone = group == DeadlineGroup.DONE
                item(key = "h-${group.name}") {
                    GroupHeader(
                        group = group,
                        count = list.size,
                        expanded = !isDone || showDone,
                        onToggle = if (isDone) ({ showDone = !showDone }) else null
                    )
                }
                if (!isDone || showDone) {
                    items(list, key = { it.id }) { item ->
                        DeadlineRow(
                            item = item,
                            group = group,
                            now = now,
                            onToggleDone = { vm.setDone(item, !item.done) },
                            onClick = { selectedId = item.id },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        AddDeadlineDialog(
            onDismiss = { adding = false },
            onSave = { title, course, notes, dueAt, hasTime ->
                vm.add(title, course, notes, dueAt, hasTime)
                adding = false
            }
        )
    }

    selected?.let { item ->
        DeadlineDetailDialog(
            item = item,
            onDismiss = { selectedId = null },
            onToggleDone = {
                vm.setDone(item, !item.done)
                selectedId = null
            },
            onDelete = {
                vm.delete(item)
                selectedId = null
            },
            onOpenLink = item.link?.let { link ->
                {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    selectedId = null
                }
            },
            onSendToTasks = {
                selectedId = null
                if (vm.sendToTasks(item)) onOpenChat()
            }
        )
    }
}

@Composable
private fun Banner(text: String) {
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
        Text(text = text, color = IshaInk, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyState(hasAny: Boolean) {
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
            Icon(Icons.Rounded.EventAvailable, contentDescription = null, tint = IshaMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = if (hasAny) "All caught up 🎉" else "Nothing due yet",
            color = IshaInk,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = "Assignments from Google Classroom show up here once it's connected in Apps. " +
                "Tap Add for anything else, or tell isha \"I have a quiz on Friday\".",
            color = IshaMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun GroupHeader(group: DeadlineGroup, count: Int, expanded: Boolean, onToggle: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel("${group.label} · $count", Modifier.weight(1f))
        if (onToggle != null) {
            Text(
                text = if (expanded) "Hide" else "Show",
                color = IshaAccent,
                style = MaterialTheme.typography.labelMedium
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = IshaAccent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DeadlineRow(
    item: Deadline,
    group: DeadlineGroup,
    now: ZonedDateTime,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (group) {
        DeadlineGroup.OVERDUE -> IshaDanger
        DeadlineGroup.TODAY -> IshaWarn
        DeadlineGroup.DONE -> IshaSuccess
        else -> IshaAccent
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (item.isDone) IshaSurfaceHi.copy(alpha = 0.55f) else IshaSurfaceHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Turned in on Classroom can't be un-checked here; it's Classroom's call.
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (item.isDone) IshaSuccess else Color.Transparent)
                .border(2.dp, if (item.isDone) IshaSuccess else accent.copy(alpha = 0.7f), CircleShape)
                .clickable(enabled = !item.turnedIn, onClick = onToggleDone),
            contentAlignment = Alignment.Center
        ) {
            if (item.isDone) {
                Icon(Icons.Rounded.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = if (item.isDone) IshaMuted else IshaInk,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (item.isDone) FontWeight.Normal else FontWeight.Medium,
                textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val course = if (item.course.isNotBlank()) "${item.course} · " else ""
            Text(
                text = course + DeadlineStore.describeDue(item),
                color = IshaMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        val badge = when {
            item.turnedIn -> "Turned in"
            item.done -> "Done"
            else -> countdown(item.dueAt, now.toInstant().toEpochMilli())
        }
        Text(
            text = badge,
            color = if (item.isDone) IshaSuccess else accent,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .clip(CircleShape)
                .background((if (item.isDone) IshaSuccess else accent).copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        if (item.fromClassroom) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.School, contentDescription = "From Classroom", tint = IshaMuted, modifier = Modifier.size(15.dp))
        }
    }
}

/** "in 3h", "in 2d", "5h late". */
private fun countdown(dueAt: Long, now: Long): String {
    val delta = dueAt - now
    val abs = kotlin.math.abs(delta)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(abs)
    val hours = TimeUnit.MILLISECONDS.toHours(abs)
    val days = TimeUnit.MILLISECONDS.toDays(abs)
    val span = when {
        minutes < 60 -> "${minutes}m"
        hours < 48 -> "${hours}h"
        else -> "${days}d"
    }
    return if (delta >= 0) "in $span" else "$span late"
}

@Composable
private fun DeadlineDetailDialog(
    item: Deadline,
    onDismiss: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
    onOpenLink: (() -> Unit)?,
    onSendToTasks: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IshaCream,
        title = { Text(item.title, color = IshaInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val course = if (item.course.isNotBlank()) "${item.course}\n" else ""
                Text("${course}Due ${DeadlineStore.describeDue(item)}", color = IshaMuted, style = MaterialTheme.typography.bodyMedium)
                if (item.turnedIn) Text("Turned in on Classroom ✓", color = IshaSuccess, style = MaterialTheme.typography.labelMedium)
                if (item.notes.isNotBlank()) {
                    Text(item.notes, color = IshaInk, style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.size(2.dp))
                ActionLine("Add to Google Tasks", onSendToTasks)
                onOpenLink?.let { ActionLine("Open in Classroom", it) }
                if (!item.turnedIn) ActionLine(if (item.done) "Mark as not done" else "Mark as done", onToggleDone)
                ActionLine("Delete", onDelete, danger = true)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ActionLine(text: String, onClick: () -> Unit, danger: Boolean = false) {
    Text(
        text = text,
        color = if (danger) IshaDanger else IshaAccent,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp)
    )
}

private enum class DayChoice { TODAY, TOMORROW, PICK }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AddDeadlineDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, course: String, notes: String, dueAt: Long, hasTime: Boolean) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var course by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(DayChoice.TOMORROW) }
    var pickedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    // Minutes after midnight; null = no specific time (end of day).
    var minuteOfDay by rememberSaveable { mutableStateOf<Int?>(23 * 60 + 59) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    val today = LocalDate.now()
    val date = when (day) {
        DayChoice.TODAY -> today
        DayChoice.TOMORROW -> today.plusDays(1)
        DayChoice.PICK -> pickedEpochDay?.let(LocalDate::ofEpochDay) ?: today
    }
    val zone = ZoneId.systemDefault()
    val dueAt = minuteOfDay?.let { date.atTime(LocalTime.of(it / 60, it % 60)).atZone(zone).toInstant().toEpochMilli() }
        ?: DeadlineStore.endOfDay(date, zone)
    val valid = title.isNotBlank()
    val presets = listOf(8 * 60, 12 * 60, 17 * 60, 23 * 60 + 59)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IshaCream,
        title = { Text("New deadline", color = IshaInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(value = title, onValueChange = { title = it }, placeholder = "What's due?")
                FormField(value = course, onValueChange = { course = it }, placeholder = "Subject (optional)")
                Text("Day", color = IshaMuted, style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormChip("Today", day == DayChoice.TODAY) { day = DayChoice.TODAY }
                    FormChip("Tomorrow", day == DayChoice.TOMORROW) { day = DayChoice.TOMORROW }
                    FormChip(
                        if (day == DayChoice.PICK) date.format(DateTimeFormatter.ofPattern("EEE, MMM d")) else "Pick date…",
                        day == DayChoice.PICK
                    ) { pickingDate = true }
                }
                Text("Time", color = IshaMuted, style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormChip("No time", minuteOfDay == null) { minuteOfDay = null }
                    presets.forEach { m -> FormChip(timeLabel(m), minuteOfDay == m) { minuteOfDay = m } }
                    val custom = minuteOfDay?.takeIf { it !in presets }
                    FormChip(custom?.let(::timeLabel) ?: "Other…", custom != null) { pickingTime = true }
                }
                FormField(value = notes, onValueChange = { notes = it }, placeholder = "Notes (optional)", singleLine = false)
                Text(
                    text = "isha reminds you the day before and 2 hours before.",
                    color = IshaMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, course, notes, dueAt, minuteOfDay != null) }, enabled = valid) {
                Text("Save", color = if (valid) IshaAccent else IshaMuted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        pickedEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        day = DayChoice.PICK
                    }
                    pickingDate = false
                }) { Text("OK", color = IshaAccent) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (pickingTime) {
        val start = minuteOfDay ?: (17 * 60)
        val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            containerColor = IshaCream,
            title = { Text("Due time", color = IshaInk) },
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        selectorColor = IshaAccent,
                        timeSelectorSelectedContainerColor = IshaAccent.copy(alpha = 0.25f),
                        clockDialColor = IshaCreamDeep
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    minuteOfDay = state.hour * 60 + state.minute
                    pickingTime = false
                }) { Text("OK", color = IshaAccent) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } }
        )
    }
}

private fun timeLabel(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(DateTimeFormatter.ofPattern("h:mm a"))
