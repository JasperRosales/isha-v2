package dev.jasper.ishav2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jasper.ishav2.agent.LaunchRequest
import dev.jasper.ishav2.agent.SharedDraft
import dev.jasper.ishav2.data.Part
import dev.jasper.ishav2.ui.IshaNav
import dev.jasper.ishav2.ui.splash.IshaSplash
import dev.jasper.ishav2.ui.theme.IshaTheme
import dev.jasper.ishav2.util.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val openInbox = mutableStateOf(false)
    private val openChat = mutableStateOf(false)
    private val openModels = mutableStateOf(false)
    private val openBudget = mutableStateOf(false)
    private val openDeadlines = mutableStateOf(false)
    private val openExams = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as IshaApp).container
        handleIntent(intent)
        // A rotation re-delivers the same intent; only import a share once.
        if (savedInstanceState == null) {
            handleShare(intent)
            handleAction(intent)
        }

        applySystemBars(dark = container.settings.current.darkTheme)
        // Load the last chat while the splash plays, so it's on screen the moment the
        // app appears instead of a frame or two later. (Idempotent: the chat screen
        // calls it again.)
        lifecycleScope.launch(Dispatchers.IO) { container.chat.openInitial() }
        // Cold starts get the animated greeting; rotations and deep links don't.
        val coldStart = savedInstanceState == null &&
            intent?.hasExtra(EXTRA_OPEN_CHAT) != true &&
            intent?.hasExtra(EXTRA_OPEN_INBOX) != true &&
            intent?.hasExtra(EXTRA_OPEN_MODELS) != true &&
            intent?.hasExtra(EXTRA_OPEN_BUDGET) != true &&
            intent?.action !in LAUNCH_ACTIONS &&
            !isShare(intent)

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            var showSplash by rememberSaveable { mutableStateOf(coldStart) }
            // The app is composed only once the logo intro has played, so start-up work
            // can't steal frames from the animation.
            var appReady by rememberSaveable { mutableStateOf(!coldStart) }
            LaunchedEffect(settings.darkTheme) {
                applySystemBars(dark = settings.darkTheme)
            }
            LaunchedEffect(showSplash) {
                // Ask after the splash so the dialog doesn't cover the animation.
                if (!showSplash) requestNotificationPermission()
            }
            IshaTheme(darkTheme = settings.darkTheme) {
                Box(Modifier.fillMaxSize()) {
                    if (appReady) IshaNav(
                        container = container,
                        openInbox = openInbox.value,
                        onInboxOpened = { openInbox.value = false },
                        openChat = openChat.value,
                        onChatOpened = { openChat.value = false },
                        openModels = openModels.value,
                        onModelsOpened = { openModels.value = false },
                        openBudget = openBudget.value,
                        onBudgetOpened = { openBudget.value = false },
                        openDeadlines = openDeadlines.value,
                        onDeadlinesOpened = { openDeadlines.value = false },
                        openExams = openExams.value,
                        onExamsOpened = { openExams.value = false }
                    )
                    AnimatedVisibility(
                        visible = showSplash,
                        enter = androidx.compose.animation.EnterTransition.None,
                        exit = fadeOut(tween(450, easing = FastOutSlowInEasing)) +
                            scaleOut(tween(450, easing = FastOutSlowInEasing), targetScale = 1.04f)
                    ) {
                        IshaSplash(
                            onIntroDone = { appReady = true },
                            onFinished = { showSplash = false }
                        )
                    }
                }
            }
        }
    }

    private fun applySystemBars(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = if (dark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
            navigationBarStyle = if (dark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        handleShare(intent)
        handleAction(intent)
    }

    /** Widget buttons, the Quick Settings tile and launcher shortcuts. */
    private fun handleAction(intent: Intent?) {
        val chat = (application as IshaApp).container.chat
        when (intent?.action) {
            ACTION_TALK -> {
                chat.launch(LaunchRequest.TALK)
                openChat.value = true
            }
            ACTION_NEW_CHAT -> {
                chat.launch(LaunchRequest.TYPE)
                openChat.value = true
            }
            ACTION_OPEN_DUE -> openDeadlines.value = true
            ACTION_OPEN_EXAMS -> openExams.value = true
            ACTION_OPEN_BUDGET -> openBudget.value = true
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_OPEN_INBOX, false)) openInbox.value = true
        if (intent.getBooleanExtra(EXTRA_OPEN_MODELS, false)) openModels.value = true
        if (intent.getBooleanExtra(EXTRA_OPEN_BUDGET, false)) openBudget.value = true
        if (intent.getBooleanExtra(EXTRA_OPEN_CHAT, false)) {
            intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { id ->
                (application as IshaApp).container.chat.openChat(id)
            }
            openChat.value = true
        }
    }

    private fun isShare(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE

    /**
     * Text, links, images and files shared from another app's share sheet. The files are
     * copied into isha's storage right away, while this activity still holds the read
     * grant, then land in a fresh chat's composer for the user to add a question.
     */
    private fun handleShare(intent: Intent?) {
        if (!isShare(intent)) return
        intent!!
        val text = listOfNotNull(
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        ).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
        val uris = sharedUris(intent).take(MAX_SHARED_FILES)
        if (text.isEmpty() && uris.isEmpty()) return

        val chat = (application as IshaApp).container.chat
        openChat.value = true
        lifecycleScope.launch {
            val parts = mutableListOf<Part>()
            for (uri in uris) {
                runCatching { FileStore.importUri(this@MainActivity, uri) }
                    .onSuccess { parts += it }
                    // A toast, since the chat screen may not be listening for events yet.
                    .onFailure {
                        Toast.makeText(
                            this@MainActivity,
                            it.message ?: "Could not open what was shared.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            if (text.isNotEmpty() || parts.isNotEmpty()) chat.share(SharedDraft(text, parts))
        }
    }

    private fun sharedUris(intent: Intent): List<Uri> {
        val uris = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
        // Some apps only put the files in ClipData.
        val clip = intent.clipData
        val fromClip = if (clip == null) emptyList() else (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        return (uris + fromClip).distinct()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        }
    }

    companion object {
        const val EXTRA_OPEN_INBOX = "open_inbox"
        const val EXTRA_OPEN_CHAT = "open_chat"
        const val EXTRA_OPEN_MODELS = "open_models"
        const val EXTRA_OPEN_BUDGET = "open_budget"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val ACTION_TALK = "dev.jasper.ishav2.action.TALK"
        const val ACTION_NEW_CHAT = "dev.jasper.ishav2.action.NEW_CHAT"
        const val ACTION_OPEN_DUE = "dev.jasper.ishav2.action.OPEN_DUE"
        const val ACTION_OPEN_BUDGET = "dev.jasper.ishav2.action.OPEN_BUDGET"
        const val ACTION_OPEN_EXAMS = "dev.jasper.ishav2.action.OPEN_EXAMS"
        private val LAUNCH_ACTIONS = setOf(ACTION_TALK, ACTION_NEW_CHAT, ACTION_OPEN_DUE, ACTION_OPEN_BUDGET, ACTION_OPEN_EXAMS)

        /** Opens isha for [action] (one of the ACTION_ constants), or normally when null. */
        fun actionIntent(context: android.content.Context, action: String?): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(action ?: Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        private const val REQUEST_NOTIFICATIONS = 1001
        private const val MAX_SHARED_FILES = 6
    }
}
