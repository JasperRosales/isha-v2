package dev.jasper.ishav2.ui.apps

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.jasper.ishav2.data.ComposioKeyMode
import dev.jasper.ishav2.net.ConnectedAccount
import dev.jasper.ishav2.net.ToolkitInfo
import dev.jasper.ishav2.ui.components.IshaLogo
import dev.jasper.ishav2.ui.components.NeuButton
import dev.jasper.ishav2.ui.components.NeuIconButton
import dev.jasper.ishav2.ui.components.NeuInset
import dev.jasper.ishav2.ui.components.NeuSurface
import dev.jasper.ishav2.ui.components.SectionLabel
import dev.jasper.ishav2.ui.components.StatusChip
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaAccentSoft
import dev.jasper.ishav2.ui.theme.IshaCream
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaDanger
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaOnAccent
import dev.jasper.ishav2.ui.theme.IshaSuccess
import dev.jasper.ishav2.ui.theme.IshaSurfaceHi
import dev.jasper.ishav2.ui.theme.IshaWarn
import kotlinx.coroutines.launch

@Composable
fun AppsScreen(
    vm: AppsViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(link) {
        val url = link ?: return@LaunchedEffect
        openBrowser(context, url)
        vm.consumeLink()
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
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Apps & connections",
                            color = IshaInk,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Composio tools for isha",
                            color = IshaMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    NeuIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        onClick = vm::refresh,
                        size = 40.dp
                    )
                }
                HorizontalDivider(color = IshaInk.copy(alpha = 0.06f))
                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = IshaAccent,
                        trackColor = IshaCreamDeep
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            item {
                StatusCard(
                    configured = settings.hasComposioKey,
                    connected = status.connected,
                    connecting = status.connecting,
                    toolCount = status.toolCount,
                    error = status.error,
                    mode = settings.composioMode,
                    dashboard = { openBrowser(context, vm.openDashboardUrl()) },
                    onOpenSettings = onOpenSettings
                )
            }

            if (!settings.hasComposioKey) {
                item {
                    NeuSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(15.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Give isha hands",
                                color = IshaInk,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "A Composio key unlocks Gmail, Calendar, Drive, Notion, Slack, GitHub and hundreds more. isha searches their tools, reads schemas, then executes what you ask.",
                                color = IshaMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            NeuButton(
                                text = "Add key in Settings",
                                onClick = onOpenSettings,
                                accent = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else if (settings.composioMode == ComposioKeyMode.CONNECT) {
                item {
                    NeuSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(15.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Composio Connect mode",
                                color = IshaInk,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "This key uses the hosted MCP endpoint. Connect and remove apps in the Composio Connect dashboard. Each app you connect becomes a tool isha can call from chat.",
                                color = IshaMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NeuButton(
                                    text = "Open Connect",
                                    onClick = { openBrowser(context, "https://connect.composio.dev") },
                                    accent = true,
                                    modifier = Modifier.weight(1f)
                                )
                                NeuButton(
                                    text = "Refresh tools",
                                    onClick = vm::refresh,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            } else if (settings.composioMode == ComposioKeyMode.PROJECT) {
                item {
                    SectionLabel("Connected accounts")
                }
                if (accounts.isEmpty()) {
                    item {
                        Text(
                            text = "Nothing connected yet. Search below or open the Composio dashboard.",
                            color = IshaMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    items(accounts.size) { index ->
                        AccountRow(accounts[index])
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    SectionLabel("Find apps")
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeuInset(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = IshaMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                BasicTextField(
                                    value = query,
                                    onValueChange = { vm.search(it) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = IshaInk),
                                    cursorBrush = SolidColor(IshaAccent),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        Box {
                                            if (query.isBlank()) {
                                                Text(
                                                    text = "Search toolkits…",
                                                    color = IshaMuted,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            inner()
                                        }
                                    }
                                )
                            }
                        }
                        NeuIconButton(
                            icon = Icons.Rounded.Search,
                            contentDescription = "Search",
                            onClick = { vm.search(query) },
                            size = 44.dp
                        )
                    }
                }
                items(catalog.size) { index ->
                    CatalogRow(catalog[index], onConnect = { vm.connect(catalog[index].slug) })
                }
                if (query.isNotBlank() && catalog.isEmpty() && !loading) {
                    item {
                        Text(
                            text = "No toolkits found for \"$query\".",
                            color = IshaMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            if (status.error != null) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(IshaDanger.copy(alpha = 0.08f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = status.error.orEmpty(),
                            color = IshaDanger,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Composio runs connected actions in the cloud. Read each approval card before you tap Approve.",
                    color = IshaMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    configured: Boolean,
    connected: Boolean,
    connecting: Boolean,
    toolCount: Int,
    error: String?,
    mode: ComposioKeyMode,
    dashboard: () -> Unit,
    onOpenSettings: () -> Unit
) {
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(15.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Composio",
                        color = IshaInk,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = when (mode) {
                            ComposioKeyMode.CONNECT -> "Consumer key · Connect MCP"
                            ComposioKeyMode.PROJECT -> "Project key · REST sessions"
                            ComposioKeyMode.UNKNOWN -> "Unrecognised key"
                            ComposioKeyMode.NONE -> "Not configured"
                        },
                        color = IshaMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                StatusChip(
                    text = when {
                        connecting -> "connecting"
                        connected -> "$toolCount tools"
                        configured && error != null -> "error"
                        configured -> "offline"
                        else -> "not set"
                    },
                    color = when {
                        connected -> IshaSuccess
                        error != null -> IshaDanger
                        connecting -> IshaWarn
                        else -> IshaMuted
                    }
                )
            }
            if (connected) {
                Text(
                    text = "isha can search and execute the tools exposed by this connection. Consequential calls still go through your approval setting.",
                    color = IshaMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!configured) {
                NeuButton(
                    text = "Open Settings",
                    onClick = onOpenSettings,
                    accent = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuButton(
                        text = "Dashboard",
                        onClick = dashboard,
                        modifier = Modifier.weight(1f)
                    )
                    NeuButton(
                        text = "Open Settings",
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(account: ConnectedAccount) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IshaSurfaceHi.copy(alpha = 0.75f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(IshaAccentSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = account.toolkitName.take(1).uppercase(),
                color = IshaAccent,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = account.toolkitName,
                color = IshaInk,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = account.alias ?: account.status.lowercase(),
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (account.active) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = IshaSuccess,
                modifier = Modifier.size(18.dp)
            )
        } else {
            StatusChip(text = account.status.lowercase(), color = IshaWarn)
        }
    }
}

@Composable
private fun CatalogRow(toolkit: ToolkitInfo, onConnect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IshaSurfaceHi.copy(alpha = 0.65f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (toolkit.logo != null) {
            AsyncImage(
                model = toolkit.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(IshaCreamDeep)
                    .padding(4.dp)
            )
        } else {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(IshaCreamDeep),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = toolkit.name.take(1).uppercase(),
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = toolkit.name,
                color = IshaInk,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = toolkit.description.ifBlank { toolkit.slug },
                color = IshaMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(IshaAccent)
                .clickable(onClick = onConnect)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Connect",
                color = IshaOnAccent,
                style = MaterialTheme.typography.labelSmall
            )
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
