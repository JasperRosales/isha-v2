package dev.jasper.ishav2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jasper.ishav2.ui.IshaNav
import dev.jasper.ishav2.ui.splash.IshaSplash
import dev.jasper.ishav2.ui.splash.SPLASH_INTRO_MS
import dev.jasper.ishav2.ui.theme.IshaTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val openInbox = mutableStateOf(false)
    private val openChat = mutableStateOf(false)
    private val openModels = mutableStateOf(false)
    private val openBudget = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as IshaApp).container
        handleIntent(intent)
        requestNotificationPermission()

        applySystemBars(dark = container.settings.current.darkTheme)
        // Cold starts get the animated greeting; rotations and deep links don't.
        val coldStart = savedInstanceState == null &&
            intent?.hasExtra(EXTRA_OPEN_CHAT) != true &&
            intent?.hasExtra(EXTRA_OPEN_INBOX) != true &&
            intent?.hasExtra(EXTRA_OPEN_MODELS) != true &&
            intent?.hasExtra(EXTRA_OPEN_BUDGET) != true

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            var showSplash by rememberSaveable { mutableStateOf(coldStart) }
            LaunchedEffect(settings.darkTheme) {
                applySystemBars(dark = settings.darkTheme)
            }
            LaunchedEffect(Unit) {
                if (showSplash) {
                    delay(SPLASH_MS)
                    showSplash = false
                }
            }
            IshaTheme(darkTheme = settings.darkTheme) {
                Box(Modifier.fillMaxSize()) {
                    IshaNav(
                        container = container,
                        openInbox = openInbox.value,
                        onInboxOpened = { openInbox.value = false },
                        openChat = openChat.value,
                        onChatOpened = { openChat.value = false },
                        openModels = openModels.value,
                        onModelsOpened = { openModels.value = false },
                        openBudget = openBudget.value,
                        onBudgetOpened = { openBudget.value = false }
                    )
                    AnimatedVisibility(
                        visible = showSplash,
                        enter = androidx.compose.animation.EnterTransition.None,
                        exit = fadeOut(tween(380)) + scaleOut(tween(380), targetScale = 1.06f)
                    ) {
                        IshaSplash()
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
        private const val REQUEST_NOTIFICATIONS = 1001
        private const val SPLASH_MS = SPLASH_INTRO_MS + 450L
    }
}
