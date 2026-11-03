package dev.jasper.ishav2.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaCream
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted

enum class EmailTone(val label: String, val instruction: String) {
    FORMAL("Formal", "formal and polite (suitable for a professor or an office)"),
    FRIENDLY("Friendly", "warm and friendly"),
    BRIEF("Brief", "short and to the point")
}

/** What the email form collects; turned into a chat prompt by [buildEmailPrompt]. */
data class EmailDraft(
    val to: String,
    val message: String,
    val subject: String = "",
    val cc: String = "",
    val tone: EmailTone = EmailTone.FORMAL,
    /** true: polish the points into a full email. false: send the words as written. */
    val writeForMe: Boolean = true
)

/**
 * Turns a filled-in email form into a Gmail request. isha always shows the
 * draft first and only sends after the user confirms (the send itself still
 * goes through the approval card).
 */
fun buildEmailPrompt(email: EmailDraft): String = buildString {
    appendLine(if (email.writeForMe) "Write an email for me in Gmail:" else "Prepare this email in Gmail, using my exact words:")
    appendLine("- To: ${email.to.trim()}")
    if (email.cc.isNotBlank()) appendLine("- Cc: ${email.cc.trim()}")
    appendLine("- Subject: ${email.subject.trim().ifBlank { "(suggest a clear subject)" }}")
    if (email.writeForMe) {
        appendLine("- Tone: ${email.tone.instruction}")
        appendLine("- What to say:")
    } else {
        appendLine("- Body (keep it as written; only fix obvious typos):")
    }
    email.message.trim().lines().forEach { appendLine("  $it") }
    if (!email.to.contains("@")) {
        appendLine("If the recipient is a name rather than an address, find their address in my recent Gmail and tell me which one you used.")
    }
    append("Show me the full draft here first and wait for me to say send before sending it.")
}

/**
 * The "Email" form from the composer's + menu. On submit it hands back a ready
 * prompt; the composer drops it into the message box for review.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmailFormDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var to by rememberSaveable { mutableStateOf("") }
    var cc by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var tone by rememberSaveable { mutableStateOf(EmailTone.FORMAL) }
    var writeForMe by rememberSaveable { mutableStateOf(true) }
    var showCc by rememberSaveable { mutableStateOf(false) }

    val valid = to.isNotBlank() && message.isNotBlank()
    val emailKeyboard = KeyboardOptions(keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IshaCream,
        title = { Text("New email", color = IshaInk) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "isha drafts it in Gmail and shows you before sending. Needs Gmail connected in Apps.",
                    color = IshaMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                FormField(value = to, onValueChange = { to = it }, placeholder = "To (email or name)", keyboardOptions = emailKeyboard)
                if (showCc) {
                    FormField(value = cc, onValueChange = { cc = it }, placeholder = "Cc (optional)", keyboardOptions = emailKeyboard)
                } else {
                    Text(
                        text = "+ Add Cc",
                        color = IshaAccent,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clickable { showCc = true }
                            .padding(vertical = 2.dp)
                    )
                }
                FormField(value = subject, onValueChange = { subject = it }, placeholder = "Subject (optional, isha can suggest one)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormChip("Write it for me", writeForMe) { writeForMe = true }
                    FormChip("Use my exact words", !writeForMe) { writeForMe = false }
                }
                FormField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = if (writeForMe) "What to say (key points are fine)" else "Your message",
                    singleLine = false,
                    maxLines = 8
                )
                if (writeForMe) {
                    Text("Tone", color = IshaMuted, style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EmailTone.entries.forEach { option ->
                            FormChip(option.label, tone == option) { tone = option }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(buildEmailPrompt(EmailDraft(to, message, subject, cc, tone, writeForMe))) },
                enabled = valid
            ) { Text("Draft email", color = if (valid) IshaAccent else IshaMuted) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
