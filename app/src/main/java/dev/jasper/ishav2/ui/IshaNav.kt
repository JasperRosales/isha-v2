package dev.jasper.ishav2.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
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
import dev.jasper.ishav2.ui.deadlines.DeadlinesScreen
import dev.jasper.ishav2.ui.deadlines.DeadlinesViewModel
import dev.jasper.ishav2.ui.inbox.InboxScreen
import dev.jasper.ishav2.ui.mail.MailDigestScreen
import dev.jasper.ishav2.ui.exam.ExamViewModel
import dev.jasper.ishav2.ui.exam.ExamsScreen
import dev.jasper.ishav2.ui.mail.MailDigestViewModel
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
private const val ROUTE_DEADLINES = "deadlines"
private const val ROUTE_MAIL = "mail"
private const val ROUTE_EXAMS = "exams"

/** True once the current screen has finished its enter transition. */
private val NavHostController.settled: Boolean
    get() = currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

/**
 * Back that can never empty the stack. An unguarded popBackStack() on a
 * double-tapped back arrow (or mid-transition) also pops the chat screen,
 * leaving an empty NavHost: a blank screen in the background color.
 */
private fun NavHostController.back() {
    if (!settled || previousBackStackEntry == null) return
    popBackStack()
}

/** Ignores taps while a transition is running, and never stacks duplicates. */
private fun NavHostController.go(route: String) {
    if (!settled) return
    navigate(route) { launchSingleTop = true }
}

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
    onBudgetOpened: () -> Unit = {},
    openDeadlines: Boolean = false,
    onDeadlinesOpened: () -> Unit = {},
    openExams: Boolean = false,
    onExamsOpened: () -> Unit = {}
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

    LaunchedEffect(openDeadlines) {
        if (openDeadlines) {
            if (startRoute != ROUTE_ONBOARDING) {
                navController.navigate(ROUTE_DEADLINES) { launchSingleTop = true }
            }
            onDeadlinesOpened()
        }
    }

    LaunchedEffect(openExams) {
        if (openExams) {
            if (startRoute != ROUTE_ONBOARDING) {
                navController.navigate(ROUTE_EXAMS) { launchSingleTop = true }
            }
            onExamsOpened()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
        // The screen underneath stays fully drawn (no fade-out) while the new
        // one slides/fades in on top, so the bare background never flashes.
        enterTransition = { slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(260)) },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
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
                onOpenSettings = { navController.go(ROUTE_SETTINGS) },
                onOpenApps = { navController.go(ROUTE_APPS) },
                onOpenInbox = { navController.go(ROUTE_INBOX) },
                onOpenModels = { navController.go(ROUTE_MODELS) },
                onOpenBudget = { navController.go(ROUTE_BUDGET) },
                onOpenDeadlines = { navController.go(ROUTE_DEADLINES) },
                onOpenMail = { navController.go(ROUTE_MAIL) },
                onOpenExams = { navController.go(ROUTE_EXAMS) }
            )
        }

        composable(ROUTE_DEADLINES) {
            val vm: DeadlinesViewModel = viewModel(factory = DeadlinesViewModel.factory(container))
            DeadlinesScreen(
                vm = vm,
                onBack = { navController.back() },
                onOpenChat = {
                    navController.navigate(ROUTE_CHAT) {
                        popUpTo(ROUTE_CHAT) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(ROUTE_EXAMS) {
            val vm: ExamViewModel = viewModel(factory = ExamViewModel.factory(container))
            ExamsScreen(vm = vm, onBack = { navController.back() })
        }

        composable(ROUTE_MAIL) {
            val vm: MailDigestViewModel = viewModel(factory = MailDigestViewModel.factory(container))
            MailDigestScreen(vm = vm, onBack = { navController.back() })
        }

        composable(ROUTE_BUDGET) {
            val vm: BudgetViewModel = viewModel(factory = BudgetViewModel.factory(container))
            BudgetScreen(vm = vm, onBack = { navController.back() })
        }

        composable(ROUTE_MODELS) {
            val vm: ModelsViewModel = viewModel(factory = ModelsViewModel.factory(container))
            ModelsScreen(vm = vm, onBack = { navController.back() })
        }

        composable(ROUTE_SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
            SettingsScreen(
                vm = vm,
                onBack = { navController.back() },
                onOpenModels = { navController.go(ROUTE_MODELS) },
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
                onBack = { navController.back() },
                onOpenSettings = { navController.go(ROUTE_SETTINGS) }
            )
        }

        composable(ROUTE_INBOX) {
            val vm: InboxViewModel = viewModel(factory = InboxViewModel.factory(container))
            InboxScreen(vm = vm, onBack = { navController.back() })
        }
    }
}
