package dev.jasper.ishav2.agent

import dev.jasper.ishav2.data.BudgetAlert
import dev.jasper.ishav2.data.BudgetCategories
import dev.jasper.ishav2.data.BudgetStore
import dev.jasper.ishav2.data.Transaction
import dev.jasper.ishav2.data.TxType
import dev.jasper.ishav2.data.formatMoney
import dev.jasper.ishav2.util.Notifier
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** A budget request understood without any language model. */
sealed interface BudgetCommand {
    data class Add(
        val type: TxType,
        val amount: Double,
        val category: String,
        val note: String,
        val daysAgo: Int = 0
    ) : BudgetCommand

    data class SetBudget(val category: String, val amount: Double) : BudgetCommand
    data class Summary(val period: Period) : BudgetCommand
    data object UndoLast : BudgetCommand

    enum class Period { TODAY, WEEK, MONTH }
}

/**
 * Plain-English budget commands for offline use: "spent 150 on lunch",
 * "got 2k allowance", "set food budget to 3000", "how much is left?".
 * Deliberately conservative: anything it is not sure about returns null and
 * goes to the model instead.
 */
object BudgetParser {
    private val AMOUNT = Regex("""(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d+))?\s*(k\b)?""")
    private val EXPENSE_START = Regex(
        """^(i\s+)?(just\s+)?(spent|paid|bought|purchased|expense|gastos|nagastos)\b"""
    )
    private val INCOME_START = Regex(
        """^(i\s+)?(just\s+)?(received|earned|got\s+paid|got\s+(?=\d)|income|salary|allowance|sahod|sweldo)\b"""
    )
    private val SET_BUDGET = listOf(
        Regex("""set\s+(?:my\s+)?(?:the\s+)?([a-z ]+?)\s+budget\s+(?:to|at|for|=)?\s*(.+)"""),
        Regex("""(?:my\s+)?([a-z ]+?)\s+budget\s+(?:is|to|=|of)\s*(.+)"""),
        Regex("""budget\s+(?:of\s+)?(.+?)\s+(?:for|on)\s+([a-z ]+)$""")
    )
    private val UNDO = Regex("""^(undo|delete|remove|cancel)\s+(the\s+)?(last|latest|previous)\s*(expense|transaction|entry|one|income|record)?\b""")
    private val SUMMARY = Regex(
        """(how\s+much\s+(did\s+i|have\s+i|i)?\s*(spen[dt]|left|remaining|do\s+i\s+have\s+left|is\s+left))|(budget\s+(summary|status|report|overview))|(spending\s+(summary|report|so\s+far))|(^(show|check)\s+(me\s+)?(my\s+)?(budget|spending|expenses))|(^my\s+(budget|expenses|spending)\??$)|(where\s+did\s+my\s+money\s+go)"""
    )

    private val MONEY_WORDS = Regex(
        """[₱$€£¥]|\bphp\b|\bpesos?\b|\d\s*k\b|allowance|baon|salary|sahod|sweldo|income|paid|payment|\bfrom\b"""
    )
    private val TAGGED_AMOUNT = Regex(
        """(?:[₱$€£¥]|\bphp\b|\bp)\s*(\d[\d,]*(?:\.\d+)?\s*k?\b)|(\d[\d,]*(?:\.\d+)?\s*k?)\s*(?:pesos?|php)\b"""
    )

    fun parse(input: String): BudgetCommand? {
        val raw = input.lowercase(Locale.ROOT)
        val text = normalize(input)
        if (text.isBlank()) return null

        if (UNDO.containsMatchIn(text)) return BudgetCommand.UndoLast

        for ((index, pattern) in SET_BUDGET.withIndex()) {
            val match = pattern.find(text) ?: continue
            val (rawCategory, rawAmount) = if (index == 2) {
                match.groupValues[2] to match.groupValues[1]
            } else {
                match.groupValues[1] to match.groupValues[2]
            }
            val amount = amountIn(rawAmount) ?: continue
            val category = BudgetCategories.canonical(rawCategory.removePrefix("monthly ").trim())
                ?: if (rawCategory.trim() in setOf("monthly", "month", "my", "total", "overall", "the", "set", "set my")) BudgetCategories.TOTAL
                else continue
            return BudgetCommand.SetBudget(category, amount)
        }
        if (Regex("""^(?:my\s+)?(?:monthly\s+)?budget\s+(?:is|to|=)\s*(.+)""").find(text) != null) {
            val amount = amountIn(text) ?: return null
            return BudgetCommand.SetBudget(BudgetCategories.TOTAL, amount)
        }

        if (SUMMARY.containsMatchIn(text)) {
            val period = when {
                "today" in text -> BudgetCommand.Period.TODAY
                "week" in text -> BudgetCommand.Period.WEEK
                else -> BudgetCommand.Period.MONTH
            }
            return BudgetCommand.Summary(period)
        }

        val expense = EXPENSE_START.containsMatchIn(text)
        val income = !expense && INCOME_START.containsMatchIn(text)
        if (!expense && !income) return null
        // A bare "got 3 …" is only money with a currency, "pesos", "allowance"…
        if (income && Regex("""^(i\s+)?(just\s+)?got\s+\d""").containsMatchIn(text) && !MONEY_WORDS.containsMatchIn(raw)) {
            return null
        }
        val amount = moneyIn(raw, text) ?: return null
        if (amount <= 0) return null
        // "spent 2 hours on homework" is time, not money.
        if (Regex("""\d\s*(hours?|hrs?|minutes?|mins?|days?|weeks?|times?)\b""").containsMatchIn(text)) return null
        val daysAgo = if ("yesterday" in text) 1 else 0
        val note = noteFrom(text)

        return if (expense) {
            val explicit = Regex("""\b(?:under|category|as)\s+([a-z]+)""").find(text)
                ?.groupValues?.get(1)
                ?.let { BudgetCategories.canonical(it) }
                ?.takeIf { it != BudgetCategories.TOTAL }
            BudgetCommand.Add(TxType.EXPENSE, amount, explicit ?: BudgetCategories.guess(text), note, daysAgo)
        } else {
            BudgetCommand.Add(TxType.INCOME, amount, BudgetCategories.guessIncome(text), note, daysAgo)
        }
    }

    private fun normalize(input: String): String = input.lowercase(Locale.ROOT)
        .replace(Regex("""[₱$€£¥]|\bphp\b|\bpesos?\b|\bp(?=\d)"""), " ")
        .replace(Regex("""[!?.]+$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /** The currency-tagged number if there is one ("₱900"), else the largest ("bought 3 shirts for 900"). */
    private fun moneyIn(raw: String, text: String): Double? {
        TAGGED_AMOUNT.find(raw)?.let { match ->
            val tagged = match.groupValues[1].ifEmpty { match.groupValues[2] }
            amountIn(tagged)?.let { return it }
        }
        return AMOUNT.findAll(text).mapNotNull { amountIn(it.value) }.maxOrNull()
    }

    fun amountIn(text: String): Double? {
        val match = AMOUNT.find(text) ?: return null
        val whole = match.groupValues[1].replace(",", "")
        val fraction = match.groupValues[2]
        var value = (if (fraction.isNotEmpty()) "$whole.$fraction" else whole).toDoubleOrNull() ?: return null
        if (match.groupValues[3].isNotEmpty()) value *= 1000
        return value
    }

    private fun noteFrom(text: String): String {
        val match = Regex("""\b(?:on|for|at|from)\s+(.+)$""").find(text) ?: return ""
        return match.groupValues[1]
            .replace(AMOUNT, "")
            .replace(Regex("""\b(yesterday|today|under\s+\w+)\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(60)
    }
}

/**
 * Executes budget requests on the phone and writes the reply. Used for the
 * offline path (no network, or no model at all) and for budget alerts.
 */
class BudgetAssistant(
    private val store: BudgetStore,
    private val notifier: Notifier
) {
    /** Handles [text] if it is a budget command; returns isha's reply, or null. */
    suspend fun reply(text: String): String? {
        val command = BudgetParser.parse(text) ?: return null
        return execute(command)
    }

    suspend fun execute(command: BudgetCommand): String {
        val c = store.currency
        return when (command) {
            is BudgetCommand.Add -> {
                val date = LocalDate.now().minusDays(command.daysAgo.toLong())
                val at = date.atTime(java.time.LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val tx = Transaction(
                    type = command.type,
                    amount = command.amount,
                    category = command.category,
                    note = command.note,
                    at = at
                )
                val alerts = store.add(tx)
                postAlerts(alerts)
                val summary = store.summary()
                buildString {
                    if (command.type == TxType.EXPENSE) {
                        append("Logged **${formatMoney(c, tx.amount)}** for ${tx.category}")
                    } else {
                        append("Added **${formatMoney(c, tx.amount)}** income (${tx.category})")
                    }
                    if (tx.note.isNotBlank()) append(" · _${tx.note}_")
                    if (command.daysAgo == 1) append(" (yesterday)")
                    append(". ✅\n\n")
                    val cat = summary.categories.firstOrNull { it.category == tx.category }
                    if (command.type == TxType.EXPENSE && cat?.limit != null) {
                        val left = cat.limit - cat.spent
                        append(
                            if (left >= 0) "${tx.category}: ${formatMoney(c, left)} left of ${formatMoney(c, cat.limit)} this month."
                            else "⚠️ ${tx.category} is over budget by ${formatMoney(c, -left)} this month."
                        )
                        append("\n")
                    }
                    append(monthLine(summary.expense, summary.totalLimit, summary.income))
                    append("\n\n_Say \"undo last\" if that's wrong._")
                }
            }
            is BudgetCommand.SetBudget -> {
                store.setBudget(command.category, command.amount)
                val label = if (command.category == BudgetCategories.TOTAL) "Your monthly budget" else "${command.category} budget"
                val summary = store.summary()
                val spent = if (command.category == BudgetCategories.TOTAL) summary.expense
                else summary.categories.firstOrNull { it.category == command.category }?.spent ?: 0.0
                "$label is now **${formatMoney(c, command.amount)}** a month. " +
                    "You've used ${formatMoney(c, spent)} so far (${percent(spent, command.amount)})."
            }
            is BudgetCommand.Summary -> summaryText(command.period)
            BudgetCommand.UndoLast -> {
                val removed = store.deleteLast()
                if (removed == null) "There's nothing to undo yet."
                else "Removed ${formatMoney(c, removed.amount)} (${removed.category}${if (removed.note.isNotBlank()) " · ${removed.note}" else ""}). ↩️"
            }
        }
    }

    fun summaryText(period: BudgetCommand.Period): String {
        val c = store.currency
        val today = LocalDate.now()
        return when (period) {
            BudgetCommand.Period.TODAY, BudgetCommand.Period.WEEK -> {
                val from = if (period == BudgetCommand.Period.TODAY) today
                else today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val (spent, list) = store.spentBetween(from, today)
                val label = if (period == BudgetCommand.Period.TODAY) "today" else "this week"
                buildString {
                    append("You've spent **${formatMoney(c, spent)}** $label.")
                    if (list.isNotEmpty()) {
                        append("\n\n")
                        list.take(8).forEach { tx ->
                            append("- ${formatMoney(c, tx.amount)} · ${tx.category}")
                            if (tx.note.isNotBlank()) append(" · ${tx.note}")
                            append("\n")
                        }
                    }
                }.trim()
            }
            BudgetCommand.Period.MONTH -> {
                val s = store.summary()
                val month = s.month.format(DateTimeFormatter.ofPattern("MMMM"))
                buildString {
                    append("**$month so far**\n\n")
                    append(monthLine(s.expense, s.totalLimit, s.income))
                    if (s.categories.isNotEmpty()) {
                        append("\n\n")
                        s.categories.filter { it.spent > 0 || it.limit != null }.take(8).forEach { cat ->
                            append("- **${cat.category}**: ${formatMoney(c, cat.spent)}")
                            if (cat.limit != null) {
                                append(" of ${formatMoney(c, cat.limit)}")
                                if (cat.over) append(" ⚠️ over")
                            }
                            append("\n")
                        }
                    } else {
                        append("\n\nNo expenses logged yet. Try \"spent 120 on lunch\".")
                    }
                }.trim()
            }
        }
    }

    /** A compact snapshot for the offline model's prompt, so it can reason about money. */
    fun snapshotForPrompt(): String? {
        val s = store.summary()
        if (s.transactions.isEmpty() && s.totalLimit == null && s.categories.isEmpty()) return null
        val c = store.currency
        return buildString {
            append("Budget this month: spent ${formatMoney(c, s.expense)}, income ${formatMoney(c, s.income)}")
            s.totalLimit?.let { append(", monthly budget ${formatMoney(c, it)} (${formatMoney(c, it - s.expense)} left)") }
            append(".")
            val top = s.categories.filter { it.spent > 0 }.take(5)
            if (top.isNotEmpty()) {
                append(" By category: ")
                append(top.joinToString("; ") { cat ->
                    "${cat.category} ${formatMoney(c, cat.spent)}" + (cat.limit?.let { " of ${formatMoney(c, it)}" } ?: "")
                })
                append(".")
            }
        }
    }

    fun postAlerts(alerts: List<BudgetAlert>) {
        val c = store.currency
        for (alert in alerts) {
            val name = if (alert.category == BudgetCategories.TOTAL) "Monthly budget" else "${alert.category} budget"
            if (alert.over) {
                notifier.postBudgetAlert(
                    "$name exceeded",
                    "You've spent ${formatMoney(c, alert.spent)} of ${formatMoney(c, alert.limit)} — over by ${formatMoney(c, alert.spent - alert.limit)}."
                )
            } else {
                notifier.postBudgetAlert(
                    "$name at ${percent(alert.spent, alert.limit)}",
                    "${formatMoney(c, alert.limit - alert.spent)} left of ${formatMoney(c, alert.limit)} this month."
                )
            }
        }
    }

    private fun monthLine(expense: Double, limit: Double?, income: Double): String {
        val c = store.currency
        return buildString {
            append("This month: **${formatMoney(c, expense)}** spent")
            if (limit != null) {
                val left = limit - expense
                append(
                    if (left >= 0) " · ${formatMoney(c, left)} left of ${formatMoney(c, limit)}"
                    else " · ⚠️ ${formatMoney(c, -left)} over your ${formatMoney(c, limit)} budget"
                )
            }
            if (income > 0) append(" · ${formatMoney(c, income)} in")
            append(".")
        }
    }

    private fun percent(part: Double, whole: Double): String =
        if (whole <= 0) "0%" else "${((part / whole) * 100).toInt()}%"
}
