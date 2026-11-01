package dev.jasper.ishav2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

@Serializable
enum class TxType { EXPENSE, INCOME }

@Serializable
data class Transaction(
    val id: String = UUID.randomUUID().toString().take(8),
    val type: TxType,
    val amount: Double,
    val category: String,
    val note: String = "",
    val at: Long = System.currentTimeMillis()
) {
    val date: LocalDate get() = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
}

@Serializable
data class BudgetData(
    val currency: String = "₱",
    val transactions: List<Transaction> = emptyList(),
    /** Monthly limit per expense category; [BudgetCategories.TOTAL] is the overall limit. */
    val budgets: Map<String, Double> = emptyMap()
)

data class CategorySpend(val category: String, val spent: Double, val limit: Double?) {
    val fraction: Float get() = if (limit != null && limit > 0) (spent / limit).toFloat() else 0f
    val over: Boolean get() = limit != null && spent > limit
}

data class MonthSummary(
    val month: YearMonth,
    val income: Double,
    val expense: Double,
    val totalLimit: Double?,
    val categories: List<CategorySpend>,
    val transactions: List<Transaction>
) {
    val balance: Double get() = income - expense
    val leftInBudget: Double? get() = totalLimit?.let { it - expense }
}

/** A budget crossing 80% or 100% because of a new expense. */
data class BudgetAlert(val category: String, val spent: Double, val limit: Double, val over: Boolean)

object BudgetCategories {
    const val TOTAL = "Total"

    val expense = listOf(
        "Food", "Transport", "Bills", "Shopping", "School",
        "Health", "Entertainment", "Personal", "Other"
    )
    val income = listOf("Allowance", "Salary", "Freelance", "Gift", "Other income")

    private val keywords: List<Pair<String, List<String>>> = listOf(
        "Food" to listOf(
            "food", "lunch", "dinner", "breakfast", "snack", "merienda", "coffee", "milk tea", "groceries",
            "grocery", "restaurant", "jollibee", "mcdo", "mcdonald", "kfc", "pizza", "eat", "meal", "drinks", "water", "rice"
        ),
        "Transport" to listOf(
            "transport", "fare", "jeep", "jeepney", "tricycle", "trike", "bus", "grab", "angkas", "joyride", "taxi",
            "uber", "gas", "fuel", "gasoline", "diesel", "parking", "toll", "train", "lrt", "mrt", "commute", "ride"
        ),
        "Bills" to listOf(
            "bill", "bills", "electric", "electricity", "meralco", "water bill", "internet", "wifi", "pldt", "globe",
            "smart", "load", "rent", "subscription", "netflix", "spotify", "phone plan"
        ),
        "School" to listOf(
            "school", "tuition", "book", "books", "notebook", "print", "printing", "photocopy", "xerox", "project",
            "supplies", "lab", "uniform", "thesis", "class"
        ),
        "Health" to listOf("health", "medicine", "meds", "doctor", "clinic", "pharmacy", "vitamins", "hospital", "dentist", "checkup"),
        "Entertainment" to listOf("movie", "movies", "cinema", "game", "games", "concert", "steam", "gaming", "fun", "outing"),
        "Shopping" to listOf("shopping", "clothes", "shirt", "shoes", "shopee", "lazada", "gadget", "online order", "mall"),
        "Personal" to listOf("haircut", "salon", "toiletries", "skincare", "laundry", "gift for", "personal")
    )

    /** Case-insensitive match against a known category name, or null. */
    fun canonical(name: String, type: TxType = TxType.EXPENSE): String? {
        val clean = name.trim()
        if (clean.equals(TOTAL, true) || clean.equals("overall", true) || clean.equals("monthly", true)) return TOTAL
        val pool = if (type == TxType.EXPENSE) expense else income
        return pool.firstOrNull { it.equals(clean, ignoreCase = true) }
            ?: pool.firstOrNull { it.lowercase().startsWith(clean.lowercase()) && clean.length >= 3 }
    }

    /** Best-guess expense category from free text ("grab to school" → Transport). */
    fun guess(text: String): String {
        val lower = " ${text.lowercase(Locale.ROOT)} "
        canonical(text)?.takeIf { it != TOTAL }?.let { return it }
        for ((category, words) in keywords) {
            if (words.any { word -> Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(lower) }) return category
        }
        return "Other"
    }

    fun guessIncome(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            "allowance" in lower || "baon" in lower -> "Allowance"
            "salary" in lower || "sweldo" in lower || "paycheck" in lower || "got paid" in lower -> "Salary"
            "freelance" in lower || "client" in lower || "commission" in lower || "project" in lower -> "Freelance"
            "gift" in lower || "birthday" in lower -> "Gift"
            else -> "Other income"
        }
    }
}

fun formatMoney(currency: String, amount: Double): String {
    val whole = amount % 1.0 == 0.0
    val number = if (whole) String.format(Locale.US, "%,.0f", amount) else String.format(Locale.US, "%,.2f", amount)
    return "$currency$number"
}

/**
 * Personal budget: transactions, monthly limits and the currency symbol, kept
 * as one JSON file on the phone. Works with no network at all.
 */
class BudgetStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val _data = MutableStateFlow(read())
    val data: StateFlow<BudgetData> = _data.asStateFlow()

    val currency: String get() = _data.value.currency

    /** Adds a transaction and returns any budget thresholds it just crossed. */
    suspend fun add(transaction: Transaction): List<BudgetAlert> {
        val before = _data.value
        val after = update { it.copy(transactions = it.transactions + transaction) }
        return if (transaction.type == TxType.EXPENSE) crossedThresholds(before, after, transaction) else emptyList()
    }

    suspend fun delete(id: String): Transaction? {
        val found = _data.value.transactions.firstOrNull { it.id == id } ?: return null
        update { it.copy(transactions = it.transactions.filterNot { tx -> tx.id == id }) }
        return found
    }

    suspend fun deleteLast(): Transaction? {
        val last = _data.value.transactions.maxByOrNull { it.at } ?: return null
        return delete(last.id)
    }

    /** Sets a monthly limit; zero or less removes it. */
    suspend fun setBudget(category: String, amount: Double) {
        update {
            val next = if (amount <= 0) it.budgets - category else it.budgets + (category to amount)
            it.copy(budgets = next)
        }
    }

    suspend fun setCurrency(symbol: String) {
        val clean = symbol.trim().take(4).ifBlank { "₱" }
        update { it.copy(currency = clean) }
    }

    suspend fun clear() {
        update { BudgetData(currency = it.currency) }
    }

    fun summary(month: YearMonth = YearMonth.now()): MonthSummary = summarize(_data.value, month)

    /** Spending between two dates inclusive, for "today" / "this week" questions. */
    fun spentBetween(from: LocalDate, to: LocalDate): Pair<Double, List<Transaction>> {
        val list = _data.value.transactions.filter {
            it.type == TxType.EXPENSE && !it.date.isBefore(from) && !it.date.isAfter(to)
        }
        return list.sumOf { it.amount } to list.sortedByDescending { it.at }
    }

    private suspend fun update(transform: (BudgetData) -> BudgetData): BudgetData = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(_data.value)
            write(next)
            _data.value = next
            next
        }
    }

    private fun read(): BudgetData {
        if (!file.exists()) return BudgetData()
        return runCatching { json.decodeFromString<BudgetData>(file.readText()) }.getOrDefault(BudgetData())
    }

    private fun write(data: BudgetData) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(data))
        if (!temp.renameTo(file)) {
            file.writeText(json.encodeToString(data))
            temp.delete()
        }
    }

    companion object {
        fun summarize(data: BudgetData, month: YearMonth): MonthSummary {
            val inMonth = data.transactions.filter { YearMonth.from(it.date) == month }
            val expenses = inMonth.filter { it.type == TxType.EXPENSE }
            val byCategory = expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
            val names = (byCategory.keys + data.budgets.keys.filter { it != BudgetCategories.TOTAL }).distinct()
            val categories = names.map { CategorySpend(it, byCategory[it] ?: 0.0, data.budgets[it]) }
                .sortedWith(compareByDescending<CategorySpend> { it.spent }.thenBy { it.category })
            return MonthSummary(
                month = month,
                income = inMonth.filter { it.type == TxType.INCOME }.sumOf { it.amount },
                expense = expenses.sumOf { it.amount },
                totalLimit = data.budgets[BudgetCategories.TOTAL],
                categories = categories,
                transactions = inMonth.sortedByDescending { it.at }
            )
        }

        fun crossedThresholds(before: BudgetData, after: BudgetData, tx: Transaction): List<BudgetAlert> {
            val month = YearMonth.from(tx.date)
            val old = summarize(before, month)
            val new = summarize(after, month)
            val alerts = mutableListOf<BudgetAlert>()
            fun check(category: String, was: Double, now: Double, limit: Double?) {
                if (limit == null || limit <= 0) return
                when {
                    was <= limit && now > limit -> alerts += BudgetAlert(category, now, limit, over = true)
                    was < limit * WARN_AT && now >= limit * WARN_AT && now <= limit ->
                        alerts += BudgetAlert(category, now, limit, over = false)
                }
            }
            val oldCat = old.categories.firstOrNull { it.category == tx.category }?.spent ?: 0.0
            val newCat = new.categories.firstOrNull { it.category == tx.category }
            check(tx.category, oldCat, newCat?.spent ?: 0.0, newCat?.limit)
            check(BudgetCategories.TOTAL, old.expense, new.expense, new.totalLimit)
            return alerts
        }

        private const val WARN_AT = 0.8
    }
}
