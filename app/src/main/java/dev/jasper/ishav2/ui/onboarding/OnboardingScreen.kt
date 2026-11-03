package dev.jasper.ishav2.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jasper.ishav2.agent.AppContainer
import dev.jasper.ishav2.data.BrainMode
import dev.jasper.ishav2.ui.components.IshaLogo
import dev.jasper.ishav2.ui.components.NeuButton
import dev.jasper.ishav2.ui.components.NeuInset
import dev.jasper.ishav2.ui.components.NeuSurface
import dev.jasper.ishav2.ui.components.SectionLabel
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaCream
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaDanger
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaSurfaceHi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit, onOffline: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var geminiKey by rememberSaveable { mutableStateOf("") }
    var composioKey by rememberSaveable { mutableStateOf("") }
    var showGemini by rememberSaveable { mutableStateOf(false) }
    var showComposio by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun start() {
        val gemini = geminiKey.trim()
        // Groq is the main brain (built-in key); Gemini is only needed without one.
        if (gemini.isBlank() && !container.settings.current.hasGroqKey) {
            error = "Add a Gemini API key so isha can think, or a Groq key in Settings."
            return
        }
        if (gemini.isNotBlank()) container.settings.saveGeminiKey(gemini)
        val composio = composioKey.trim()
        if (composio.isNotBlank()) {
            container.settings.saveComposioKey(composio)
            scope.launch(Dispatchers.IO) { runCatching { container.composio.ensure(force = true) } }
        }
        container.settings.setOnboarded(true)
        onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IshaCream)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IshaLogo(52.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "isha",
                    color = IshaInk,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "your personal assistant",
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = "Your phone,\nwith a brain.",
            color = IshaInk,
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Chat, search the web, and get real things done in Gmail, Calendar, Drive, Notion, Slack and more. Everything runs from this phone.",
            color = IshaMuted,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Feature(Icons.Rounded.Lock, "Private by default", "History stays on this phone. Keys are Keystore-encrypted.")
            Feature(Icons.Rounded.PhoneAndroid, "No PC server", "A standalone Android app. Nothing to keep running at home.")
            Feature(Icons.Rounded.Apps, "Apps via Composio", "One key unlocks hundreds of app tools.")
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel("Required")
        Spacer(Modifier.height(6.dp))
        NeuSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(15.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = IshaAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (container.settings.current.hasGroqKey) "Gemini API key (backup)" else "Gemini API key",
                        color = IshaInk,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = if (container.settings.current.hasGroqKey) "Groq answers first. Gemini takes over when Groq's limit runs out and reads images. Free keys work." else "Used for every reply and tool call. Free keys work.",
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                SecretField(
                    value = geminiKey,
                    onValueChange = {
                        geminiKey = it
                        error = null
                    },
                    placeholder = "AIza…",
                    visible = showGemini,
                    onToggle = { showGemini = !showGemini }
                )
                NeuButton(
                    text = "Get a Gemini key",
                    onClick = { openBrowser(context, "https://aistudio.google.com/apikey") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Optional")
        Spacer(Modifier.height(6.dp))
        NeuSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(15.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = null,
                        tint = IshaAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Composio key",
                        color = IshaInk,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = "ck_… (Connect) from connect.composio.dev, or ak_… (project key) from dashboard.composio.dev. Add it later in Settings if you prefer.",
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                SecretField(
                    value = composioKey,
                    onValueChange = { composioKey = it },
                    placeholder = "ck_… or ak_…",
                    visible = showComposio,
                    onToggle = { showComposio = !showComposio }
                )
            }
        }

        error?.let { message ->
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(IshaDanger.copy(alpha = 0.10f))
                    .padding(11.dp)
            ) {
                Text(
                    text = message,
                    color = IshaDanger,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        NeuButton(
            text = "Start using isha",
            onClick = { start() },
            accent = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        NeuButton(
            text = "No key? Use isha offline",
            onClick = {
                container.settings.setBrainMode(BrainMode.OFFLINE)
                container.settings.setOnboarded(true)
                onOffline()
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "By starting, you agree that requests go to Groq and Google Gemini and, if configured, Composio. There is no analytics and no isha account.",
            color = IshaMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IshaCreamDeep.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(IshaSurfaceHi.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = IshaAccent,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = IshaInk,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SecretField(
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

private fun openBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
