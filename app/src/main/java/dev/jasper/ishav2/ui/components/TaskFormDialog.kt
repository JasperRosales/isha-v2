package dev.jasper.ishav2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaCream
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaOnAccent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What the task form collects; turned into a chat prompt by [buildTaskPrompt]. */
data class TaskDraft(
    val title: String,
    val notes: String = "",
    val due: LocalDate? = null,
    val list: String = ""
)

/**
 * Turns a filled-in task form into a clear instruction for isha to create the
 * task in Google Tasks, so every task request has the same shape.
 */
fun buildTaskPrompt(task: TaskDraft): String = buildString {
    appendLine("Add this task to my Google Tasks:")
    appendLine("- Title: ${task.title.trim()}")
    if (task.notes.isNotBlank()) appendLine("- Notes: ${task.notes.trim()}")
    task.due?.let {
        appendLine("- Due: ${it.format(DateTimeFormatter.ISO_LOCAL_DATE)} (${it.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))})")
    }
    appendLine("- List: ${task.list.trim().ifBlank { "my default list" }}")
    append("Create it right away and confirm when it's done.")
}

private enum class DueChoice { NONE, TODAY, TOMORROW, PICK }

/**
 * The "Task" form from the composer's + menu. On submit it hands back a ready
 * prompt for Google Tasks; the caller decides whether to send or prefill it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskFormDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var list by rememberSaveable { mutableStateOf("") }
    var dueChoice by rememberSaveable { mutableStateOf(DueChoice.NONE) }
    var pickedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    val today = LocalDate.now()
    val due: LocalDate? = when (dueChoice) {
        DueChoice.NONE -> null
        DueChoice.TODAY -> today
        DueChoice.TOMORROW -> today.plusDays(1)
        DueChoice.PICK -> pickedEpochDay?.let(LocalDate::ofEpochDay)
    }
    val valid = title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IshaCream,
        title = { Text("New task", color = IshaInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "isha will add it to Google Tasks. Needs Google Tasks connected in Apps.",
                    color = IshaMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                FormField(value = title, onValueChange = { title = it }, placeholder = "Task title")
                FormField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = "Notes (optional)",
                    singleLine = false
                )
                Text("Due", color = IshaMuted, style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FormChip("No date", dueChoice == DueChoice.NONE) { dueChoice = DueChoice.NONE }
                    FormChip("Today", dueChoice == DueChoice.TODAY) { dueChoice = DueChoice.TODAY }
                    FormChip("Tomorrow", dueChoice == DueChoice.TOMORROW) { dueChoice = DueChoice.TOMORROW }
                    val pickLabel = if (dueChoice == DueChoice.PICK && due != null) {
                        due.format(DateTimeFormatter.ofPattern("MMM d"))
                    } else {
                        "Pick date…"
                    }
                    FormChip(pickLabel, dueChoice == DueChoice.PICK) { pickingDate = true }
                }
                FormField(value = list, onValueChange = { list = it }, placeholder = "Task list (optional)")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(buildTaskPrompt(TaskDraft(title, notes, due, list))) },
                enabled = valid
            ) { Text("Add task", color = if (valid) IshaAccent else IshaMuted) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (pickingDate) {
        val initial = (pickedEpochDay?.let(LocalDate::ofEpochDay) ?: today)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        // The picker reports midnight UTC of the chosen day.
                        pickedEpochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        dueChoice = DueChoice.PICK
                    }
                    pickingDate = false
                }) { Text("OK", color = IshaAccent) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
internal fun FormChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) IshaAccent else IshaCreamDeep)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = if (selected) IshaOnAccent else IshaInk,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
internal fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    maxLines: Int = 4,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
) {
    val style = MaterialTheme.typography.bodyMedium
    NeuInset(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else maxLines,
            textStyle = style.copy(color = IshaInk),
            cursorBrush = SolidColor(IshaAccent),
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (singleLine) Modifier else Modifier.heightIn(min = 44.dp)),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(text = placeholder, color = IshaMuted, style = style)
                    inner()
                }
            }
        )
    }
}
