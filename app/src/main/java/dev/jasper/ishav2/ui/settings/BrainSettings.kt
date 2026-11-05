package dev.jasper.ishav2.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.jasper.ishav2.data.AppSettings
import dev.jasper.ishav2.data.CloudBrain
import dev.jasper.ishav2.data.ExtraProvider
import dev.jasper.ishav2.ui.components.NeuButton
import dev.jasper.ishav2.ui.components.NeuIconButton
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaOnAccent
import dev.jasper.ishav2.ui.theme.IshaSuccess
import dev.jasper.ishav2.ui.theme.IshaWarn
import kotlinx.coroutines.delay

/** Cloud brain priority: reorder with arrows, see cooldowns, retry early. */
@Composable
internal fun BrainPriorityCard(
    settings: AppSettings,
    limitedUntil: Map<CloudBrain, Long>,
    onMove: (CloudBrain, Int) -> Unit,
    onRetry: (CloudBrain) -> Unit,
    onSlowSwitch: (Int) -> Unit
) {
    // Ticks so the "cooling down" countdown stays current.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(limitedUntil) {
        while (true) {
            now = System.currentTimeMillis()
            if (limitedUntil.values.none { it > now }) break
            delay(1_000)
        }
    }
    SettingsCard(
        title = "Brain priority",
        subtitle = "isha tries the top brain first and moves down the list when one hits a limit, fails or stalls. " +
            "Images and PDFs always go to Gemini."
    ) {
        val order = settings.brainOrder
        order.forEachIndexed { index, brain ->
            val hasKey = settings.hasKey(brain)
            val until = limitedUntil[brain] ?: 0L
            val cooling = hasKey && until > now
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}.",
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(22.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = settings.brainLabel(brain),
                        color = if (hasKey) IshaInk else IshaMuted,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = when {
                            !hasKey -> "Not set up · skipped"
                            cooling -> "Cooling down · ${formatLeft(until - now)} left · tap to retry"
                            else -> "Ready"
                        },
                        color = when {
                            !hasKey -> IshaMuted
                            cooling -> IshaWarn
                            else -> IshaSuccess
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = if (cooling) Modifier.clickable { onRetry(brain) } else Modifier
                    )
                }
                NeuIconButton(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Move ${settings.brainLabel(brain)} up",
                    onClick = { onMove(brain, -1) },
                    tint = if (index == 0) IshaMuted else IshaInk,
                    size = 34.dp
                )
                Spacer(Modifier.width(6.dp))
                NeuIconButton(
                    icon = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Move ${settings.brainLabel(brain)} down",
                    onClick = { onMove(brain, 1) },
                    tint = if (index == order.lastIndex) IshaMuted else IshaInk,
                    size = 34.dp
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Switch to the next brain if a reply hasn't started after",
            color = IshaInk,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(4.dp))
        ChipRow(
            items = AppSettings.SLOW_SWITCH_CHOICES,
            selected = { it == settings.slowSwitchSeconds },
            label = { if (it == 0) "Never" else "${it}s" },
            onPick = onSlowSwitch
        )
    }
}

/** Third brain on any OpenAI-compatible API. */
@Composable
internal fun ExtraBrainCard(
    settings: AppSettings,
    testing: Boolean,
    models: List<String>,
    onProvider: (ExtraProvider) -> Unit,
    onSaveKey: (String) -> Unit,
    onTest: () -> Unit,
    onModel: (String) -> Unit,
    onBaseUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val provider = settings.extraProvider
    var key by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var model by rememberSaveable(provider) { mutableStateOf(settings.extraModel) }
    var url by rememberSaveable { mutableStateOf(settings.extraBaseUrl) }
    LaunchedEffect(settings.extraModel) { model = settings.extraModel }

    SettingsCard(
        title = "Extra brain · ${provider.label}",
        subtitle = "A third cloud brain on any OpenAI-compatible API. Cerebras and OpenRouter have free tiers. " +
            "Reads text only. Key stored encrypted on this phone."
    ) {
        ChipRow(
            items = ExtraProvider.entries,
            selected = { it == provider },
            label = { it.label },
            onPick = onProvider
        )
        Spacer(Modifier.height(8.dp))
        if (provider == ExtraProvider.CUSTOM) {
            KeyField(
                value = url,
                onValueChange = { url = it },
                placeholder = "https://…/v1",
                visible = true,
                onToggle = {}
            )
            Spacer(Modifier.height(6.dp))
            NeuButton(
                text = "Save base URL",
                onClick = { onBaseUrl(url) },
                enabled = url.isNotBlank() && url.trim() != settings.extraBaseUrl,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
        KeyField(
            value = key,
            onValueChange = { key = it },
            placeholder = if (settings.extraKey.isNotBlank()) "Saved · paste to replace" else provider.keyHint,
            visible = showKey,
            onToggle = { showKey = !showKey }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuButton(
                text = "Save",
                onClick = {
                    onSaveKey(key)
                    key = ""
                },
                accent = true,
                enabled = key.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
            NeuButton(
                text = if (testing) "Testing…" else "Test",
                onClick = onTest,
                enabled = settings.extraKey.isNotBlank() && settings.extraUrl.isNotBlank() && !testing,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (settings.extraKey.isNotBlank()) {
                NeuButton(
                    text = "Remove key",
                    onClick = {
                        onSaveKey("")
                        key = ""
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            if (provider.keyUrl.isNotBlank()) {
                NeuButton(
                    text = "Get a key",
                    onClick = { openBrowser(context, provider.keyUrl) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = "Model", color = IshaInk, style = MaterialTheme.typography.labelLarge)
        KeyField(
            value = model,
            onValueChange = { model = it },
            placeholder = provider.defaultModel.ifBlank { "model id" },
            visible = true,
            onToggle = {}
        )
        if (models.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Available for this key (from Test):",
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall
            )
            ChipRow(
                items = models,
                selected = { it == settings.extraModel },
                label = { it },
                onPick = {
                    model = it
                    onModel(it)
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        NeuButton(
            text = "Save model",
            onClick = { onModel(model) },
            enabled = model.isNotBlank() && model.trim() != settings.extraModel,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun <T> ChipRow(
    items: List<T>,
    selected: (T) -> Boolean,
    label: (T) -> String,
    onPick: (T) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        for (item in items) {
            val on = selected(item)
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) IshaAccent else IshaCreamDeep)
                    .clickable { onPick(item) }
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label(item),
                    color = if (on) IshaOnAccent else IshaInk,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun formatLeft(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(1)
    return when {
        seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        seconds >= 60 -> "${seconds / 60}m"
        else -> "${seconds}s"
    }
}
