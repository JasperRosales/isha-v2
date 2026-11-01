package dev.jasper.ishav2.ui.budget

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jasper.ishav2.data.BudgetCategories
import dev.jasper.ishav2.data.CategorySpend
import dev.jasper.ishav2.data.MonthSummary
import dev.jasper.ishav2.data.Transaction
import dev.jasper.ishav2.data.TxType
import dev.jasper.ishav2.data.formatMoney
import dev.jasper.ishav2.ui.components.IshaLogo
import dev.jasper.ishav2.ui.components.NeuIconButton
import dev.jasper.ishav2.ui.components.NeuInset
import dev.jasper.ishav2.ui.components.NeuSurface
import dev.jasper.ishav2.ui.components.SectionLabel
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaAccentSoft
import dev.jasper.ishav2.ui.theme.IshaCream
import dev.jasper.ishav2.ui.theme.IshaCreamDeep
import dev.jasper.ishav2.ui.theme.IshaDanger
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import dev.jasper.ishav2.ui.theme.IshaOnAccent
import dev.jasper.ishav2.ui.theme.IshaWarn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Budget tracking that lives entirely on the phone. Headline numbers are stat
 * tiles; category limits are single-hue meters. Warning/over states are the
 * only colored fills, and they always carry an icon and a text label.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetScreen(vm: BudgetViewModel, onBack: () -> Unit) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val currency = data.currency
    val snackbar = remember { SnackbarHostState() }

    var adding by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Transaction?>(null) }
    var pickingCurrency by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = IshaCream,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                containerColor = IshaAccent,
                contentColor = IshaOnAccent,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add") },
                modifier = Modifier.navigationBarsPadding()
            )
        },
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
                        Text(text = "Budget", color = IshaInk, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Saved on this phone · works offline",
                            color = IshaMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(IshaAccentSoft)
                            .clickable { pickingCurrency = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = currency, color = IshaInk, style = MaterialTheme.typography.titleMedium)
                    }
                }
                HorizontalDivider(color = IshaInk.copy(alpha = 0.06f))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                MonthSwitcher(
                    month = month,
                    canGoForward = month < YearMonth.now(),
                    onPrevious = vm::previousMonth,
                    onNext = vm::nextMonth
                )
            }
            item {
                HeroCard(
                    summary = summary,
                    currency = currency,
                    onSetBudget = { editingBudget = BudgetCategories.TOTAL }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Income", formatMoney(currency, summary.income), Modifier.weight(1f))
                    StatTile(
                        "Net",
                        (if (summary.balance < 0) "−" else "") + formatMoney(currency, kotlin.math.abs(summary.balance)),
                        Modifier.weight(1f)
                    )
                }
            }

            item { SectionLabel("By category · tap to set a limit") }
            item {
                NeuSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Column {
                        val shown = summary.categories.map { it.category }.toSet()
                        summary.categories.forEach { category ->
                            CategoryRow(category, currency, onClick = { editingBudget = category.category })
                        }
                        val unused = BudgetCategories.expense.filterNot { it in shown }
                        if (unused.isNotEmpty()) {
                            if (summary.categories.isNotEmpty()) {
                                HorizontalDivider(
                                    color = IshaInk.copy(alpha = 0.06f),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                )
                            }
                            FlowRow(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                unused.forEach { name ->
                                    Chip(text = "+ $name", selected = false, onClick = { editingBudget = name })
                                }
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Transactions · long-press to delete") }
            if (summary.transactions.isEmpty()) {
                item {
                    Text(
                        text = "Nothing logged for ${month.format(DateTimeFormatter.ofPattern("MMMM"))} yet. " +
                            "Tap Add, or just tell isha \"spent 150 on lunch\" — it works offline too.",
                        color = IshaMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                    )
                }
            }
            val byDay = summary.transactions.groupBy { it.date }
            byDay.forEach { (day, list) ->
                item(key = "day-$day") {
                    Row(
                        Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dayLabel(day),
                            color = IshaMuted,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        val spent = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
                        if (spent > 0) {
                            Text(
                                text = "−${formatMoney(currency, spent)}",
                                color = IshaMuted,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                items(list, key = { it.id }) { tx ->
                    TransactionRow(
                        tx = tx,
                        currency = currency,
                        onLongPress = { deleting = tx },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    if (adding) {
        AddTransactionDialog(
            currency = currency,
            onDismiss = { adding = false },
            onSave = { type, amount, category, note, daysAgo ->
                vm.add(type, amount, category, note, daysAgo)
                adding = false
            }
        )
    }
    editingBudget?.let { category ->
        SetBudgetDialog(
            category = category,
            currency = currency,
            current = data.budgets[category],
            onDismiss = { editingBudget = null },
            onSave = { amount ->
                vm.setBudget(category, amount)
                editingBudget = null
            }
        )
    }
    deleting?.let { tx ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = IshaCream,
            title = { Text("Delete this entry?", color = IshaInk) },
            text = {
                Text(
                    "${formatMoney(currency, tx.amount)} · ${tx.category}${if (tx.note.isNotBlank()) " · ${tx.note}" else ""}",
                    color = IshaMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(tx)
                    deleting = null
                }) { Text("Delete", color = IshaDanger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
    if (pickingCurrency) {
        CurrencyDialog(
            current = currency,
            onDismiss = { pickingCurrency = false },
            onPick = {
                vm.setCurrency(it)
                pickingCurrency = false
            }
        )
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NeuIconButton(icon = Icons.Rounded.ChevronLeft, contentDescription = "Previous month", onClick = onPrevious, size = 36.dp)
        Text(
            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            color = IshaInk,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        NeuIconButton(
            icon = Icons.Rounded.ChevronRight,
            contentDescription = "Next month",
            onClick = onNext,
            tint = if (canGoForward) IshaInk else IshaMuted.copy(alpha = 0.4f),
            size = 36.dp
        )
    }
}

@Composable
private fun HeroCard(summary: MonthSummary, currency: String, onSetBudget: () -> Unit) {
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp)
    ) {
        Column {
            Text(text = "Spent", color = IshaMuted, style = MaterialTheme.typography.labelMedium)
            Text(
                text = formatMoney(currency, summary.expense),
                color = IshaInk,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(10.dp))
            val limit = summary.totalLimit
            if (limit != null && limit > 0) {
                val fraction = (summary.expense / limit).toFloat()
                Meter(fraction)
                Spacer(Modifier.height(7.dp))
                StateLine(
                    fraction = fraction,
                    normal = "${formatMoney(currency, limit - summary.expense)} left of ${formatMoney(currency, limit)}",
                    overText = "Over by ${formatMoney(currency, summary.expense - limit)} · budget ${formatMoney(currency, limit)}",
                    onClick = onSetBudget
                )
            } else {
                Text(
                    text = "Set a monthly budget →",
                    color = IshaAccent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSetBudget)
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    NeuSurface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 12.dp)
    ) {
        Column {
            Text(text = label, color = IshaMuted, style = MaterialTheme.typography.labelMedium)
            Text(
                text = value,
                color = IshaInk,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryRow(category: CategorySpend, currency: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = category.category,
                color = IshaInk,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatMoney(currency, category.spent) +
                    (category.limit?.let { " / ${formatMoney(currency, it)}" } ?: ""),
                color = IshaInk,
                style = MaterialTheme.typography.labelLarge
            )
        }
        if (category.limit != null) {
            Spacer(Modifier.height(6.dp))
            Meter(category.fraction)
            if (category.fraction >= WARN_AT) {
                Spacer(Modifier.height(4.dp))
                StateLine(
                    fraction = category.fraction,
                    normal = "",
                    overText = "Over by ${formatMoney(currency, category.spent - category.limit)}",
                    onClick = null
                )
            }
        } else {
            Text(text = "No limit · tap to set one", color = IshaMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Single-hue meter; the warning and over states change the fill and always come with [StateLine]. */
@Composable
private fun Meter(fraction: Float) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(450), label = "meter")
    val fill = when {
        fraction > 1f -> IshaDanger
        fraction >= WARN_AT -> IshaWarn
        else -> IshaAccent
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(IshaCreamDeep)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(CircleShape)
                .background(fill)
        )
    }
}

@Composable
private fun StateLine(fraction: Float, normal: String, overText: String, onClick: (() -> Unit)?) {
    val (icon, color, text) = when {
        fraction > 1f -> Triple(Icons.Rounded.ErrorOutline, IshaDanger, overText)
        fraction >= WARN_AT -> Triple(
            Icons.Rounded.WarningAmber,
            IshaWarn,
            "${(fraction * 100).toInt()}% used" + if (normal.isNotBlank()) " · $normal" else ""
        )
        else -> Triple(null, IshaMuted, normal)
    }
    if (text.isBlank()) return
    Row(
        Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text = text, color = if (icon != null) IshaInk else IshaMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(tx: Transaction, currency: String, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (tx.type == TxType.INCOME) IshaAccentSoft else IshaCreamDeep),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = categoryEmoji(tx.category),
                fontSize = 17.sp
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(text = tx.category, color = IshaInk, style = MaterialTheme.typography.labelLarge)
            if (tx.note.isNotBlank()) {
                Text(
                    text = tx.note,
                    color = IshaMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = (if (tx.type == TxType.INCOME) "+" else "−") + formatMoney(currency, tx.amount),
            color = IshaInk,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTransactionDialog(
    currency: String,
    onDismiss: () -> Unit,
    onSave: (TxType, Double, String, String, Int) -> Unit
) {
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var daysAgo by remember { mutableIntStateOf(0) }
    val value = amount.replace(",", "").toDoubleOrNull() ?: 0.0
    val categories = if (type == TxType.EXPENSE) BudgetCategories.expense else BudgetCategories.income
    val chosen = category?.takeIf { it in categories }
        ?: if (type == TxType.EXPENSE) BudgetCategories.guess(note) else BudgetCategories.guessIncome(note)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IshaCream,
        title = { Text("New entry", color = IshaInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Expense", type == TxType.EXPENSE) { type = TxType.EXPENSE }
                    Chip("Income", type == TxType.INCOME) { type = TxType.INCOME }
                }
                Field(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    placeholder = "${currency}0",
                    keyboard = KeyboardType.Decimal,
                    large = true
                )
                Field(value = note, onValueChange = { note = it }, placeholder = "What was it for? (optional)")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { name -> Chip(name, name == chosen) { category = name } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Today", daysAgo == 0) { daysAgo = 0 }
                    Chip("Yesterday", daysAgo == 1) { daysAgo = 1 }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(type, value, chosen, note, daysAgo) },
                enabled = value > 0
            ) { Text("Save", color = if (value > 0) IshaAccent else IshaMuted) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SetBudgetDialog(
    category: String,
    currency: String,
    current: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var amount by remember { mutableStateOf(current?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    val value = amount.replace(",", "").toDoubleOrNull() ?: 0.0
    val title = if (category == BudgetCategories.TOTAL) "Monthly budget" else "$category budget"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IshaCream,
        title = { Text(title, color = IshaInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "How much can you spend each month? isha will notify you at 80% and when you go over.",
                    color = IshaMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Field(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    placeholder = "${currency}0",
                    keyboard = KeyboardType.Decimal,
                    large = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value > 0) {
                Text("Save", color = if (value > 0) IshaAccent else IshaMuted)
            }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onSave(0.0) }) { Text("Remove", color = IshaDanger) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencyDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IshaCream,
        title = { Text("Currency symbol", color = IshaInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("₱", "$", "€", "£", "¥", "₹", "₩", "RM").forEach { symbol ->
                        Chip(symbol, symbol == current) { onPick(symbol) }
                    }
                }
                Field(value = custom, onValueChange = { custom = it.take(4) }, placeholder = "Or type one")
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(custom) }, enabled = custom.isNotBlank()) { Text("Use", color = IshaAccent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
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
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    large: Boolean = false
) {
    val style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium
    NeuInset(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style.copy(color = IshaInk),
            cursorBrush = SolidColor(IshaAccent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(text = placeholder, color = IshaMuted, style = style)
                    inner()
                }
            }
        )
    }
}

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}

private fun categoryEmoji(category: String): String = when (category) {
    "Food" -> "🍜"
    "Transport" -> "🚌"
    "Bills" -> "💡"
    "Shopping" -> "🛍️"
    "School" -> "📚"
    "Health" -> "💊"
    "Entertainment" -> "🎬"
    "Personal" -> "✨"
    "Allowance" -> "💰"
    "Salary" -> "💼"
    "Freelance" -> "🧑‍💻"
    "Gift" -> "🎁"
    else -> "🪙"
}

private const val WARN_AT = 0.8f
