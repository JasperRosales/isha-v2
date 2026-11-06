package dev.jasper.ishav2.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jasper.ishav2.BuildConfig
import dev.jasper.ishav2.data.AppSettings
import dev.jasper.ishav2.data.ApprovalMode
import dev.jasper.ishav2.data.BrainMode
import dev.jasper.ishav2.data.ComposioKeyMode
import dev.jasper.ishav2.ui.components.IshaLogo
import dev.jasper.ishav2.ui.components.NeuButton
import dev.jasper.ishav2.ui.components.NeuIconButton
import dev.jasper.ishav2.ui.components.NeuInset
import dev.jasper.ishav2.ui.components.NeuSurface
import dev.jasper.ishav2.ui.components.SectionLabel
import dev.jasper.ishav2.ui.components.StatusChip
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
import kotlin.math.roundToInt

private enum class ClearAction { CHATS, ALL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onCleared: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val composio by vm.composio.collectAsStateWithLifecycle()
    val testingGemini by vm.testingGemini.collectAsStateWithLifecycle()
    val testingComposio by vm.testingComposio.collectAsStateWithLifecycle()
    val testingGroq by vm.testingGroq.collectAsStateWithLifecycle()
    val testingExtra by vm.testingExtra.collectAsStateWithLifecycle()
    val extraModels by vm.extraModels.collectAsStateWithLifecycle()
    val limitedUntil by vm.limitedUntil.collectAsStateWithLifecycle()
    val cleared by vm.cleared.collectAsStateWithLifecycle()
    val memory by vm.memory.collectAsStateWithLifecycle()
    val installedModels by vm.installedModels.collectAsStateWithLifecycle()
    var pickBriefingTime by remember { mutableStateOf(false) }
    var confirmForgetAll by remember { mutableStateOf(false) }

    var geminiKey by rememberSaveable { mutableStateOf("") }
    var composioKey by rememberSaveable { mutableStateOf("") }
    var showGemini by rememberSaveable { mutableStateOf(false) }
    var showComposio by rememberSaveable { mutableStateOf(false) }
    var groqKey by rememberSaveable { mutableStateOf("") }
    var showGroq by rememberSaveable { mutableStateOf(false) }
    var modelField by rememberSaveable { mutableStateOf(settings.model) }
    var confirmAuto by remember { mutableStateOf(false) }
    var clearAction by remember { mutableStateOf<ClearAction?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(settings.model) {
        if (modelField.isBlank()) modelField = settings.model
    }
    LaunchedEffect(cleared) {
        if (cleared) onCleared()
    }

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
                        text = "Settings",
                        color = IshaInk,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
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
            item { SectionLabel("Brain") }
            item {
                BrainPriorityCard(
                    settings = settings,
                    limitedUntil = limitedUntil,
                    onMove = vm::moveBrain,
                    onRetry = vm::retryBrain,
                    onSlowSwitch = vm::setSlowSwitch
                )
            }
            item {
                SettingsCard(
                    title = "Groq",
                    subtitle = "Fast, free tier with tight per-minute limits. Stored encrypted on this phone."
                ) {
                    KeyField(
                        value = groqKey,
                        onValueChange = { groqKey = it },
                        placeholder = if (settings.hasGroqKey) "Saved · paste to replace" else "gsk_…",
                        visible = showGroq,
                        onToggle = { showGroq = !showGroq }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeuButton(
                            text = "Save",
                            onClick = {
                                vm.saveGroqKey(groqKey)
                                groqKey = ""
                            },
                            accent = true,
                            enabled = groqKey.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        )
                        NeuButton(
                            text = if (testingGroq) "Testing…" else "Test",
                            onClick = vm::testGroq,
                            enabled = settings.hasGroqKey && !testingGroq,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (settings.hasGroqKey) {
                            NeuButton(
                                text = "Reset key",
                                onClick = {
                                    vm.saveGroqKey("")
                                    groqKey = ""
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        NeuButton(
                            text = "Get a key",
                            onClick = { openBrowser(context, "https://console.groq.com/keys") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        for (suggestion in AppSettings.GROQ_MODEL_SUGGESTIONS) {
                            val selected = settings.groqModel == suggestion
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) IshaAccent else IshaCreamDeep)
                                    .clickable { vm.setGroqModel(suggestion) }
                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = suggestion,
                                    color = if (selected) IshaOnAccent else IshaInk,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard(
                    title = "Gemini",
                    subtitle = "The only brain here that reads images and PDFs. Stored encrypted on this phone."
                ) {
                    KeyField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it },
                        placeholder = if (settings.hasGeminiKey) "Saved · paste to replace" else "AIza…",
                        visible = showGemini,
                        onToggle = { showGemini = !showGemini }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeuButton(
                            text = "Save",
                            onClick = {
                                vm.saveGeminiKey(geminiKey)
                                geminiKey = ""
                            },
                            accent = true,
                            enabled = geminiKey.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        )
                        NeuButton(
                            text = if (testingGemini) "Testing…" else "Test",
                            onClick = vm::testGemini,
                            enabled = settings.hasGeminiKey && !testingGemini,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (settings.hasGeminiKey) {
                            NeuButton(
                                text = "Remove key",
                                onClick = {
                                    vm.saveGeminiKey("")
                                    geminiKey = ""
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        NeuButton(
                            text = "Get a key",
                            onClick = { openBrowser(context, "https://aistudio.google.com/apikey") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                SettingsCard(
                    title = "Gemini model",
                    subtitle = "Used when Gemini answers."
                ) {
                    KeyField(
                        value = modelField,
                        onValueChange = { modelField = it },
                        placeholder = AppSettings.DEFAULT_MODEL,
                        visible = true,
                        onToggle = {}
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        for (suggestion in AppSettings.MODEL_SUGGESTIONS) {
                            val selected = settings.model == suggestion
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) IshaAccent else IshaCreamDeep)
                                    .clickable {
                                        modelField = suggestion
                                        vm.setModel(suggestion)
                                    }
                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = suggestion,
                                    color = if (selected) IshaOnAccent else IshaInk,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    NeuButton(
                        text = "Save model",
                        onClick = { vm.setModel(modelField) },
                        enabled = modelField.isNotBlank() && modelField != settings.model,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                ExtraBrainCard(
                    settings = settings,
                    testing = testingExtra,
                    models = extraModels,
                    onProvider = vm::setExtraProvider,
                    onSaveKey = vm::saveExtraKey,
                    onTest = vm::testExtra,
                    onModel = vm::setExtraModel,
                    onBaseUrl = vm::setExtraBaseUrl
                )
            }

            item {
                val active = installedModels.firstOrNull { it.id == settings.localModelId }
                SettingsCard(
                    title = "Offline brain",
                    subtitle = "Download a small model so isha still answers with no signal."
                ) {
                    Text(
                        text = when (settings.brainMode) {
                            BrainMode.AUTO -> "Automatic: cloud brains online, on-device offline"
                            BrainMode.ONLINE -> "Always online (cloud brains in priority order)"
                            BrainMode.OFFLINE -> "Always offline (on-device)"
                        },
                        color = IshaInk,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = active?.let { "Offline model: ${it.name}" } ?: "No offline model yet",
                        color = IshaMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    NeuButton(
                        text = "Manage offline models",
                        onClick = onOpenModels,
                        accent = active == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Tools", Modifier.weight(1f))
                    StatusChip(
                        text = when {
                            composio.connecting -> "connecting"
                            composio.connected -> "${composio.toolCount} tools"
                            composio.error != null -> "error"
                            else -> "off"
                        },
                        color = when {
                            composio.connected -> IshaSuccess
                            composio.error != null -> IshaDanger
                            else -> IshaMuted
                        }
                    )
                }
            }
            item {
                SettingsCard(
                    title = "Composio",
                    subtitle = "Gmail, Calendar, Drive, Notion, Slack, GitHub and more."
                ) {
                    Text(
                        text = composioExplanation(settings.composioMode),
                        color = IshaMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    KeyField(
                        value = composioKey,
                        onValueChange = { composioKey = it },
                        placeholder = if (settings.hasComposioKey) "Saved · paste to replace" else "ck_… or ak_…",
                        visible = showComposio,
                        onToggle = { showComposio = !showComposio }
                    )
                    composio.error?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = error,
                            color = IshaDanger,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeuButton(
                            text = "Save",
                            onClick = {
                                vm.saveComposioKey(composioKey)
                                composioKey = ""
                            },
                            accent = true,
                            enabled = composioKey.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        )
                        NeuButton(
                            text = if (testingComposio) "Testing…" else "Test",
                            onClick = vm::testComposio,
                            enabled = settings.hasComposioKey && !testingComposio,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (settings.hasComposioKey) {
                            NeuButton(
                                text = "Remove",
                                onClick = {
                                    vm.saveComposioKey("")
                                    composioKey = ""
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        NeuButton(
                            text = "Dashboard",
                            onClick = { openBrowser(context, "https://dashboard.composio.dev") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item { SectionLabel("Personal assistant") }
            item {
                SettingsCard(
                    title = "Daily briefing",
                    subtitle = "Each morning isha checks your calendar, email and tasks and drops a short summary in your inbox."
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Briefing at %02d:%02d".format(settings.briefingHour, settings.briefingMinute),
                                color = IshaInk,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = if (settings.hasComposioKey) "Uses your connected apps through Composio."
                                else "Works best with a Composio key and Gmail/Calendar connected.",
                                color = IshaMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Switch(
                            checked = settings.briefingEnabled,
                            onCheckedChange = { vm.setBriefing(it, settings.briefingHour, settings.briefingMinute) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = IshaOnAccent,
                                checkedTrackColor = IshaAccent,
                                uncheckedThumbColor = IshaMuted,
                                uncheckedTrackColor = IshaCreamDeep
                            )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeuButton(
                            text = "Change time",
                            onClick = { pickBriefingTime = true },
                            modifier = Modifier.weight(1f)
                        )
                        NeuButton(
                            text = "Send one now",
                            onClick = vm::sendBriefingNow,
                            enabled = settings.briefingEnabled && settings.hasCloudKey,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            item {
                SettingsCard(
                    title = "What isha remembers",
                    subtitle = "Facts isha saved about you to personalise help. Say \"remember that…\" or \"forget…\" in chat."
                ) {
                    if (memory.isEmpty()) {
                        Text(
                            text = "Nothing yet.",
                            color = IshaMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    for (fact in memory) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "• ${fact.text}",
                                color = IshaInk,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { vm.forgetFact(fact.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Forget",
                                    tint = IshaMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    if (memory.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        NeuButton(
                            text = "Forget everything",
                            onClick = { confirmForgetAll = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item { SectionLabel("Background alerts") }
            item {
                SettingsCard(
                    title = "Check for new mail & announcements",
                    subtitle = "Runs every 30 min with Composio. Needs an ak_ project key and the apps connected in Composio."
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Gmail new mail", color = IshaInk, style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "Pushes new unread mail every 30 minutes",
                                color = IshaMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Switch(
                            checked = settings.gmailPollingEnabled,
                            onCheckedChange = vm::setGmailPolling,
                            enabled = settings.hasComposioKey
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Google Classroom", color = IshaInk, style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "Pushes new announcements every 30 minutes",
                                color = IshaMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Switch(
                            checked = settings.classroomPollingEnabled,
                            onCheckedChange = vm::setClassroomPolling,
                            enabled = settings.hasComposioKey
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Google Tasks", color = IshaInk, style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "Keeps Due in sync with your Google Tasks, both ways",
                                color = IshaMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Switch(
                            checked = settings.tasksSyncEnabled,
                            onCheckedChange = vm::setTasksSync,
                            enabled = settings.hasComposioKey
                        )
                    }
                }
            }

            item { SectionLabel("Appearance") }
            item {
                SettingsCard(
                    title = "Theme",
                    subtitle = "Switch between isha's daylight cream and evening espresso looks."
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Dark theme",
                                color = IshaInk,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = if (settings.darkTheme)
                                    "Espresso surfaces with a warm orange glow."
                                else "Cream surfaces with a warm orange accent.",
                                color = IshaMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Switch(
                            checked = settings.darkTheme,
                            onCheckedChange = { vm.setDarkTheme(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = IshaOnAccent,
                                checkedTrackColor = IshaAccent,
                                uncheckedThumbColor = IshaMuted,
                                uncheckedTrackColor = IshaCreamDeep
                            )
                        )
                    }
                }
            }

            item { SectionLabel("Voice") }
            item {
                SettingsCard(
                    title = "Voice mode",
                    subtitle = "Tap the mic in the message box to talk to isha. Speech is turned into text by your phone's speech service."
                ) {
                    VoiceToggle(
                        title = "Read replies aloud",
                        description = "When you speak a message, isha speaks the answer back.",
                        checked = settings.voiceReadReplies,
                        onCheckedChange = vm::setVoiceReadReplies
                    )
                    Spacer(Modifier.height(12.dp))
                    VoiceToggle(
                        title = "Hands-free conversation",
                        description = if (settings.voiceReadReplies)
                            "After a spoken answer, isha listens again. Stay quiet to end the conversation."
                        else "Needs “Read replies aloud”.",
                        checked = settings.voiceHandsFree && settings.voiceReadReplies,
                        enabled = settings.voiceReadReplies,
                        onCheckedChange = vm::setVoiceHandsFree
                    )
                }
            }

            item { SectionLabel("Behaviour") }
            item {
                SettingsCard(
                    title = "Approvals",
                    subtitle = "Control when isha can act without checking in."
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Auto-approve actions",
                                color = IshaInk,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = if (settings.approvalMode == ApprovalMode.AUTO)
                                    "No approval card for sends, creates, deletes or code."
                                else "Ask before each consequential tool call.",
                                color = if (settings.approvalMode == ApprovalMode.AUTO) IshaWarn else IshaMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Switch(
                            checked = settings.approvalMode == ApprovalMode.AUTO,
                            onCheckedChange = { enabled ->
                                if (enabled) confirmAuto = true
                                else vm.setApproval(ApprovalMode.MANUAL)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = IshaOnAccent,
                                checkedTrackColor = IshaAccent,
                                uncheckedThumbColor = IshaMuted,
                                uncheckedTrackColor = IshaCreamDeep
                            )
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Skip approval for read-only lookups",
                                color = IshaInk,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Checking email, calendar or files runs instantly. Sending, creating and deleting still ask.",
                                color = IshaMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Switch(
                            checked = settings.autoApproveReads,
                            onCheckedChange = vm::setAutoApproveReads,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = IshaOnAccent,
                                checkedTrackColor = IshaAccent,
                                uncheckedThumbColor = IshaMuted,
                                uncheckedTrackColor = IshaCreamDeep
                            )
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Tool steps per message: ${settings.maxSteps}",
                        color = IshaInk,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = settings.maxSteps.toFloat(),
                        onValueChange = { vm.setMaxSteps(it.roundToInt()) },
                        valueRange = 4f..40f,
                        steps = 35,
                        colors = SliderDefaults.colors(
                            thumbColor = IshaAccent,
                            activeTrackColor = IshaAccent,
                            inactiveTrackColor = IshaCreamDeep
                        )
                    )
                    Text(
                        text = "Higher lets isha chain more tool calls before stopping. 20 is a good default.",
                        color = IshaMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            item { SectionLabel("Data") }
            item {
                SettingsCard(
                    title = "Local storage",
                    subtitle = "Chats and the inbox are JSON files inside this app. Keys use Android Keystore encryption."
                ) {
                    NeuButton(
                        text = "Clear chats & inbox",
                        onClick = { clearAction = ClearAction.CHATS },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(IshaDanger.copy(alpha = 0.08f))
                            .clickable { clearAction = ClearAction.ALL }
                            .padding(13.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = IshaDanger,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Clear everything, including keys",
                                color = IshaDanger,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            item { SectionLabel("About") }
            item {
                SettingsCard(
                    title = "isha v${BuildConfig.VERSION_NAME}",
                    subtitle = "A private assistant that runs only on this phone. No PC server, no analytics."
                ) {
                    Text(
                        text = "Network calls go to the cloud brains you set up (Groq, Google Gemini, your extra brain) for replies and to Composio only if you add a key. Web search and page reads go directly from this phone.",
                        color = IshaMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeuButton(
                            text = "Gemini keys",
                            onClick = { openBrowser(context, "https://aistudio.google.com/apikey") },
                            modifier = Modifier.weight(1f)
                        )
                        NeuButton(
                            text = "Composio",
                            onClick = { openBrowser(context, "https://connect.composio.dev") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (confirmAuto) {
        AlertDialog(
            onDismissRequest = { confirmAuto = false },
            containerColor = IshaSurfaceHi,
            icon = {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = IshaWarn)
            },
            title = { Text("Auto-approve everything?") },
            text = {
                Text(
                    text = "isha will be able to send mail, create events, post, delete and run connected code without showing you a card first.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setApproval(ApprovalMode.AUTO)
                    confirmAuto = false
                }) { Text("Enable", color = IshaAccent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAuto = false }) { Text("Keep asking") }
            }
        )
    }

    if (pickBriefingTime) {
        val timeState = rememberTimePickerState(
            initialHour = settings.briefingHour,
            initialMinute = settings.briefingMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pickBriefingTime = false },
            containerColor = IshaCream,
            title = { Text("Briefing time", color = IshaInk) },
            text = {
                TimePicker(
                    state = timeState,
                    colors = TimePickerDefaults.colors(
                        selectorColor = IshaAccent,
                        timeSelectorSelectedContainerColor = IshaAccent.copy(alpha = 0.25f),
                        clockDialColor = IshaCreamDeep
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setBriefing(true, timeState.hour, timeState.minute)
                    pickBriefingTime = false
                }) { Text("Save", color = IshaAccent) }
            },
            dismissButton = {
                TextButton(onClick = { pickBriefingTime = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmForgetAll) {
        AlertDialog(
            onDismissRequest = { confirmForgetAll = false },
            containerColor = IshaCream,
            title = { Text("Forget everything?", color = IshaInk) },
            text = { Text("isha will lose every fact it saved about you.", color = IshaMuted) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearMemory()
                    confirmForgetAll = false
                }) { Text("Forget", color = IshaDanger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForgetAll = false }) { Text("Cancel") }
            }
        )
    }

    clearAction?.let { action ->
        AlertDialog(
            onDismissRequest = { clearAction = null },
            containerColor = IshaSurfaceHi,
            title = {
                Text(if (action == ClearAction.ALL) "Clear everything?" else "Clear chats & inbox?")
            },
            text = {
                Text(
                    text = if (action == ClearAction.ALL)
                        "All chats, inbox items and both API keys will be deleted from this phone. This cannot be undone."
                    else
                        "Every conversation and inbox item will be deleted. Your keys and settings stay.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        ClearAction.CHATS -> vm.clearChats()
                        ClearAction.ALL -> {
                            vm.clearEverything()
                        }
                    }
                    clearAction = null
                }) {
                    Text("Delete", color = IshaDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearAction = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
internal fun SettingsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(15.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                color = IshaInk,
                style = MaterialTheme.typography.titleMedium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(5.dp))
            content()
        }
    }
}

@Composable
private fun VoiceToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) IshaInk else IshaMuted,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = description,
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
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
internal fun KeyField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visible: Boolean,
    onToggle: () -> Unit
) {
    NeuInset(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = IshaInk,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(IshaAccent),
                visualTransformation = if (visible) VisualTransformation.None
                else PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                color = IshaMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        inner()
                    }
                }
            )
            Box(
                Modifier
                    .size(36.dp)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff
                    else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide" else "Show",
                    tint = IshaMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

private fun composioExplanation(mode: ComposioKeyMode): String = when (mode) {
    ComposioKeyMode.NONE -> "Optional. Add a key to unlock app tools."
    ComposioKeyMode.CONNECT -> "Connect mode (ck_): uses the hosted Composio MCP endpoint. Fastest setup."
    ComposioKeyMode.PROJECT -> "Project key (ak_): uses Composio sessions with this phone's user id."
    ComposioKeyMode.UNKNOWN -> "That prefix is not recognised. It should start with ck_ or ak_."
}

internal fun openBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
