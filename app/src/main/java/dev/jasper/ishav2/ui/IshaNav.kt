package dev.jasper.ishav2.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.jasper.ishav2.agent.AppContainer
import dev.jasper.ishav2.ui.apps.AppsScreen
import dev.jasper.ishav2.ui.apps.AppsViewModel
import dev.jasper.ishav2.ui.budget.BudgetScreen
import dev.jasper.ishav2.ui.budget.BudgetViewModel
import dev.jasper.ishav2.ui.chat.ChatScreen
import dev.jasper.ishav2.ui.chat.ChatViewModel
import dev.jasper.ishav2.ui.inbox.InboxScreen
import dev.jasper.ishav2.ui.inbox.InboxViewModel
import dev.jasper.ishav2.ui.models.ModelsScreen
import dev.jasper.ishav2.ui.models.ModelsViewModel
import dev.jasper.ishav2.ui.onboarding.OnboardingScreen
import dev.jasper.ishav2.ui.settings.SettingsScreen
import dev.jasper.ishav2.ui.settings.SettingsViewModel

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_CHAT = "chat"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_APPS = "apps"
private const val ROUTE_INBOX = "inbox"
private const val ROUTE_MODELS = "models"
private const val ROUTE_BUDGET = "budget"

@Composable
fun IshaNav(
    container: AppContainer,
    openInbox: Boolean,
    onInboxOpened: () -> Unit,
    openChat: Boolean,
    onChatOpened: () -> Unit,
    openModels: Boolean = false,
    onModelsOpened: () -> Unit = {},
    openBudget: Boolean = false,
    onBudgetOpened: () -> Unit = {}
) {
    val navController = rememberNavController()
    val startRoute = remember {
        val current = container.settings.current
        if (current.canChat || current.onboarded) ROUTE_CHAT else ROUTE_ONBOARDING
    }

    LaunchedEffect(openInbox) {
        if (openInbox) {
            if (startRoute != ROUTE_ONBOARDING) {
                navController.navigate(ROUTE_INBOX) { launchSingleTop = true }
            }
            onInboxOpened()
        }
    }

    LaunchedEffect(openChat) {
        if (openChat) {
            if (startRoute != ROUTE_ONBOARDING) {
                navController.navigate(ROUTE_CHAT) { launchSingleTop = true }
            }
            onChatOpened()
        }
    }

    LaunchedEffect(openModels) {
        if (openModels) {
            navController.navigate(ROUTE_MODELS) { launchSingleTop = true }
            onModelsOpened()
        }
    }

    LaunchedEffect(openBudget) {
        if (openBudget) {
            navController.navigate(ROUTE_BUDGET) { launchSingleTop = true }
            onBudgetOpened()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
        enterTransition = { slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(260)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(240)) }
    ) {
        composable(ROUTE_ONBOARDING) {
            OnboardingScreen(
                container = container,
                onDone = {
                    navController.navigate(ROUTE_CHAT) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOffline = {
                    navController.navigate(ROUTE_CHAT) {
                        popUpTo(0) { inclusive = true }
                    }
                    navController.navigate(ROUTE_MODELS)
                }
            )
        }

        composable(ROUTE_CHAT) {
            val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(container))
            ChatScreen(
                vm = vm,
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenApps = { navController.navigate(ROUTE_APPS) },
                onOpenInbox = { navController.navigate(ROUTE_INBOX) },
                onOpenModels = { navController.navigate(ROUTE_MODELS) },
                onOpenBudget = { navController.navigate(ROUTE_BUDGET) }
            )
        }

        composable(ROUTE_BUDGET) {
            val vm: BudgetViewModel = viewModel(factory = BudgetViewModel.factory(container))
            BudgetScreen(vm = vm, onBack = { navController.popBackStack() })
        }

        composable(ROUTE_MODELS) {
            val vm: ModelsViewModel = viewModel(factory = ModelsViewModel.factory(container))
            ModelsScreen(vm = vm, onBack = { navController.popBackStack() })
        }

        composable(ROUTE_SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
            SettingsScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onOpenModels = { navController.navigate(ROUTE_MODELS) },
                onCleared = {
                    navController.navigate(ROUTE_ONBOARDING) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(ROUTE_APPS) {
            val vm: AppsViewModel = viewModel(factory = AppsViewModel.factory(container))
            AppsScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
            )
        }

        composable(ROUTE_INBOX) {
            val vm: InboxViewModel = viewModel(factory = InboxViewModel.factory(container))
            InboxScreen(vm = vm, onBack = { navController.popBackStack() })
        }
    }
}
