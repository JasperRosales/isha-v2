package dev.jasper.ishav2.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import dev.jasper.ishav2.data.BudgetCategories
import dev.jasper.ishav2.data.BudgetStore
import dev.jasper.ishav2.data.MemoryStore
import dev.jasper.ishav2.data.Transaction
import dev.jasper.ishav2.data.TxType
import dev.jasper.ishav2.data.formatMoney
import dev.jasper.ishav2.data.NotificationStore
import dev.jasper.ishav2.data.Reminder
import dev.jasper.ishav2.data.ReminderStore
import dev.jasper.ishav2.notify.ReminderReceiver
import dev.jasper.ishav2.net.RemoteTool
import dev.jasper.ishav2.net.WebTools
import dev.jasper.ishav2.util.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone

/** Tools that live on the phone itself. Composio handles the app integrations. */
class NativeTools(
    private val context: Context,
    private val notifications: NotificationStore,
    private val notifier: Notifier,
    private val memory: MemoryStore,
    private val reminders: ReminderStore,
    private val budget: BudgetStore,
    private val budgetAssistant: BudgetAssistant
) {
    fun declarations(): List<RemoteTool> = listOf(
        RemoteTool(
            name = "web_search",
            description = "Search the public web and return titles, URLs and snippets. Use before answering questions about current events or facts you are unsure of.",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("query", stringProperty("The search query."))
                },
                required = listOf("query")
            )
        ),
        RemoteTool(
            name = "read_url",
            description = "Open a public web page and return its readable text. Use after web_search to read a promising result.",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("url", stringProperty("The http(s) URL to read."))
                },
                required = listOf("url")
            )
        ),
        RemoteTool(
            name = "current_time",
            description = "Get the phone's current date, time, timezone and epoch. Use whenever a task depends on \"now\".",
            inputSchema = objectSchema(buildJsonObject { }, emptyList())
        ),
        RemoteTool(
            name = "notify_user",
            description = "Send the user a local notification and add it to their isha inbox. Use for reminders or anything worth surfacing outside this chat.",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("title", stringProperty("Short notification title."))
                    put("message", stringProperty("Notification body."))
                    put("level", stringProperty("One of: info, success, warn."))
                },
                required = listOf("message")
            )
        ),
        RemoteTool(
            name = "open_url",
            description = "Open a web link in the user's browser or matching app.",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("url", stringProperty("http(s) URL to open."))
                },
                required = listOf("url")
            )
        ),
        RemoteTool(
            name = "share_text",
            description = "Open Android's share sheet with some text, so the user can send it to any app.",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("text", stringProperty("Text to share."))
                    put("title", stringProperty("Optional chooser title."))
                },
                required = listOf("text")
            )
        ),
        RemoteTool(
            name = "set_reminder",
            description = "Schedule a push notification reminder at a specific time. It fires even if the app is closed. Prefer this over set_alarm for anything that isn't a wake-up alarm.",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("message", stringProperty("What to remind the user about, written to them (e.g. \"Submit the lab report\")."))
                    put("at", stringProperty("When, as ISO-8601 local date-time like 2026-11-02T15:30 (device timezone) or with an offset. Use this OR in_minutes."))
                    put("in_minutes", intProperty("Minutes from now. Use this OR at."))
                    put("title", stringProperty("Optional short title. Defaults to \"Reminder\"."))
                },
                required = listOf("message")
            )
        ),
        RemoteTool(
            name = "list_reminders",
            description = "List the reminders that are still scheduled.",
            inputSchema = objectSchema(buildJsonObject { }, emptyList())
        ),
        RemoteTool(
            name = "cancel_reminder",
            description = "Cancel a scheduled reminder by its id (from list_reminders or set_reminder).",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("id", stringProperty("Reminder id."))
                },
                required = listOf("id")
            )
        ),
        RemoteTool(
            name = "remember_fact",
            description = "Save a lasting fact about the user to long-term memory (preferences, names of people, routines, school/work details). Keep it one short sentence in third person, e.g. \"Prefers meetings after 10am\".",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("fact", stringProperty("The fact to remember."))
                },
                required = listOf("fact")
            )
        ),
        RemoteTool(
            name = "forget_fact",
            description = "Remove facts from long-term memory, by id or by a phrase they contain. Use when the user says something is no longer true or asks you to forget it.",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("query", stringProperty("Fact id, or words contained in the fact."))
                },
                required = listOf("query")
            )
        ),
        RemoteTool(
            name = "add_transaction",
            description = "Log money the user spent or received in their on-phone budget. Use whenever they mention a purchase, bill, fare, allowance, salary, etc. Pick the closest category.",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("type", enumProperty("expense or income.", listOf("expense", "income")))
                    put("amount", numberProperty("Positive amount in the user's currency."))
                    put("category", enumProperty(
                        "Category. Expenses: ${BudgetCategories.expense.joinToString()}. Income: ${BudgetCategories.income.joinToString()}.",
                        BudgetCategories.expense + BudgetCategories.income
                    ))
                    put("note", stringProperty("Short description, e.g. \"lunch at Jollibee\"."))
                    put("date", stringProperty("Optional ISO date (YYYY-MM-DD) if not today."))
                },
                required = listOf("type", "amount", "category")
            )
        ),
        RemoteTool(
            name = "budget_summary",
            description = "Get the user's budget for a month: total spent, income, monthly budget and per-category spending vs limits.",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("month", stringProperty("Optional YYYY-MM. Defaults to the current month."))
                },
                required = emptyList()
            )
        ),
        RemoteTool(
            name = "list_transactions",
            description = "List logged transactions (newest first), optionally for a month and/or category. Includes ids for delete_transaction.",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("month", stringProperty("Optional YYYY-MM. Defaults to the current month."))
                    put("category", stringProperty("Optional category filter."))
                    put("limit", intProperty("Max items, default 30."))
                },
                required = emptyList()
            )
        ),
        RemoteTool(
            name = "set_budget",
            description = "Set a monthly spending limit for a category, or the overall monthly budget with category \"Total\". Amount 0 removes the limit.",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("category", stringProperty("An expense category, or \"Total\" for the overall budget."))
                    put("amount", numberProperty("Monthly limit."))
                },
                required = listOf("category", "amount")
            )
        ),
        RemoteTool(
            name = "delete_transaction",
            description = "Delete a logged transaction by id (from list_transactions or add_transaction).",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("id", stringProperty("Transaction id."))
                },
                required = listOf("id")
            )
        ),
        RemoteTool(
            name = "set_alarm",
            description = "Open the clock app with an alarm pre-filled. The user confirms it in the clock app.",
            inputSchema = objectSchema(
                properties = buildJsonObject {
                    put("hour", intProperty("Hour in 24-hour time (0-23)."))
                    put("minute", intProperty("Minute (0-59)."))
                    put("label", stringProperty("Optional alarm label."))
                },
                required = listOf("hour", "minute")
            )
        )
    )

    suspend fun execute(name: String, args: JsonObject): String = when (name) {
        "web_search" -> WebTools.search(args.text("query"))
        "read_url" -> WebTools.read(args.text("url"))
        "current_time" -> currentTime()
        "notify_user" -> notifyUser(args)
        "open_url" -> openUrl(args.text("url"))
        "share_text" -> shareText(args.text("text"), args.text("title"))
        "set_alarm" -> setAlarm(args)
        "set_reminder" -> setReminder(args)
        "list_reminders" -> listReminders()
        "cancel_reminder" -> cancelReminder(args.text("id"))
        "remember_fact" -> rememberFact(args.text("fact"))
        "forget_fact" -> forgetFact(args.text("query"))
        "add_transaction" -> addTransaction(args)
        "budget_summary" -> budgetSummary(args.text("month"))
        "list_transactions" -> listTransactions(args)
        "set_budget" -> setBudget(args)
        "delete_transaction" -> deleteTransaction(args.text("id"))
        else -> throw IllegalArgumentException("Unknown native tool: $name")
    }

    private suspend fun notifyUser(args: JsonObject): String {
        val title = args.text("title").ifBlank { "isha" }
        val level = args.text("level").ifBlank { "info" }
        val item = notifications.add(
            title = title,
            message = args.text("message"),
            level = level,
            source = "isha"
        )
        notifier.post(item)
        return buildJsonObject {
            put("ok", true)
            put("id", item.id)
            put("posted", notifier.canPost())
        }.toString()
    }

    private suspend fun setReminder(args: JsonObject): String {
        val message = args.text("message").trim()
        if (message.isBlank()) throw IllegalArgumentException("A reminder needs a message.")
        val minutes = (args["in_minutes"] as? JsonPrimitive)?.intOrNull
        val at = when {
            args.text("at").isNotBlank() -> parseWhen(args.text("at"))
            minutes != null && minutes > 0 -> System.currentTimeMillis() + minutes * 60_000L
            else -> throw IllegalArgumentException("Give either 'at' or 'in_minutes'.")
        }
        if (at <= System.currentTimeMillis()) {
            throw IllegalArgumentException("That time is already in the past. Check the date and try again.")
        }
        val reminder = Reminder(
            title = args.text("title").ifBlank { "Reminder" },
            message = message,
            at = at
        )
        reminders.add(reminder)
        ReminderReceiver.schedule(context, reminder)
        return buildJsonObject {
            put("ok", true)
            put("id", reminder.id)
            put("fires_at", describeTime(at))
            put("notifications_allowed", notifier.canPost())
        }.toString()
    }

    private fun listReminders(): String = buildJsonObject {
        put("reminders", buildJsonArray {
            reminders.upcoming().forEach { reminder ->
                add(buildJsonObject {
                    put("id", reminder.id)
                    put("title", reminder.title)
                    put("message", reminder.message)
                    put("at", describeTime(reminder.at))
                })
            }
        })
    }.toString()

    private suspend fun cancelReminder(id: String): String {
        val removed = reminders.remove(id.trim())
        if (removed != null) ReminderReceiver.cancel(context, removed.id)
        return buildJsonObject {
            put("ok", removed != null)
            if (removed == null) put("error", "No reminder with id $id.")
        }.toString()
    }

    private suspend fun rememberFact(fact: String): String {
        if (fact.isBlank()) throw IllegalArgumentException("Nothing to remember.")
        val saved = memory.remember(fact)
        return buildJsonObject {
            put("ok", true)
            put("id", saved.id)
        }.toString()
    }

    private suspend fun forgetFact(query: String): String {
        val removed = memory.forget(query)
        return buildJsonObject {
            put("ok", removed > 0)
            put("removed", removed)
        }.toString()
    }

    private suspend fun addTransaction(args: JsonObject): String {
        val type = if (args.text("type").equals("income", ignoreCase = true)) TxType.INCOME else TxType.EXPENSE
        val amount = args.number("amount")
        if (amount <= 0) throw IllegalArgumentException("Amount must be positive.")
        val note = args.text("note")
        val category = BudgetCategories.canonical(args.text("category"), type)
            ?.takeIf { it != BudgetCategories.TOTAL }
            ?: if (type == TxType.EXPENSE) BudgetCategories.guess("${args.text("category")} $note")
            else BudgetCategories.guessIncome("${args.text("category")} $note")
        val at = args.text("date").takeIf { it.isNotBlank() }?.let { value ->
            runCatching {
                LocalDate.parse(value.take(10)).atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
        } ?: System.currentTimeMillis()
        val tx = Transaction(type = type, amount = amount, category = category, note = note, at = at)
        val alerts = budget.add(tx)
        budgetAssistant.postAlerts(alerts)
        val summary = budget.summary(YearMonth.from(tx.date))
        val cat = summary.categories.firstOrNull { it.category == category }
        return buildJsonObject {
            put("ok", true)
            put("id", tx.id)
            put("logged", "${formatMoney(budget.currency, amount)} ${type.name.lowercase()} · $category")
            put("month_spent", formatMoney(budget.currency, summary.expense))
            summary.totalLimit?.let { put("month_budget_left", formatMoney(budget.currency, it - summary.expense)) }
            cat?.limit?.let { put("category_left", formatMoney(budget.currency, it - cat.spent)) }
            if (alerts.isNotEmpty()) put("alert", alerts.joinToString { "${it.category} ${if (it.over) "over budget" else "at 80%+"}" })
        }.toString()
    }

    private fun budgetSummary(month: String): String {
        val ym = parseMonth(month)
        val s = budget.summary(ym)
        val c = budget.currency
        return buildJsonObject {
            put("month", ym.toString())
            put("currency", c)
            put("spent", formatMoney(c, s.expense))
            put("income", formatMoney(c, s.income))
            put("balance", formatMoney(c, s.balance))
            s.totalLimit?.let {
                put("monthly_budget", formatMoney(c, it))
                put("left", formatMoney(c, it - s.expense))
            }
            put("categories", buildJsonArray {
                s.categories.forEach { cat ->
                    add(buildJsonObject {
                        put("category", cat.category)
                        put("spent", formatMoney(c, cat.spent))
                        cat.limit?.let { limit ->
                            put("limit", formatMoney(c, limit))
                            put("over", cat.over)
                        }
                    })
                }
            })
            put("transaction_count", s.transactions.size)
            val all = budget.allTime()
            put("all_time_income", formatMoney(c, all.income))
            put("all_time_spent", formatMoney(c, all.expense))
            put("all_time_balance", formatMoney(c, all.balance))
        }.toString()
    }

    private fun listTransactions(args: JsonObject): String {
        val s = budget.summary(parseMonth(args.text("month")))
        val category = args.text("category")
        val limit = ((args["limit"] as? JsonPrimitive)?.intOrNull ?: 30).coerceIn(1, 100)
        val c = budget.currency
        return buildJsonObject {
            put("transactions", buildJsonArray {
                s.transactions
                    .filter { category.isBlank() || it.category.equals(category, ignoreCase = true) }
                    .take(limit)
                    .forEach { tx ->
                        add(buildJsonObject {
                            put("id", tx.id)
                            put("date", tx.date.toString())
                            put("type", tx.type.name.lowercase())
                            put("amount", formatMoney(c, tx.amount))
                            put("category", tx.category)
                            if (tx.note.isNotBlank()) put("note", tx.note)
                        })
                    }
            })
        }.toString()
    }

    private suspend fun setBudget(args: JsonObject): String {
        val category = BudgetCategories.canonical(args.text("category"))
            ?: throw IllegalArgumentException("Unknown category. Use one of: ${BudgetCategories.expense.joinToString()} or Total.")
        val amount = args.number("amount")
        budget.setBudget(category, amount)
        return buildJsonObject {
            put("ok", true)
            put("category", category)
            put("monthly_limit", if (amount > 0) formatMoney(budget.currency, amount) else "removed")
        }.toString()
    }

    private suspend fun deleteTransaction(id: String): String {
        val removed = budget.delete(id.trim())
        return buildJsonObject {
            put("ok", removed != null)
            if (removed == null) put("error", "No transaction with id $id.")
        }.toString()
    }

    private fun parseMonth(value: String): YearMonth =
        runCatching { YearMonth.parse(value.trim().take(7)) }.getOrDefault(YearMonth.now())

    private fun parseWhen(value: String): Long {
        val text = value.trim()
        runCatching { return OffsetDateTime.parse(text).toInstant().toEpochMilli() }
        runCatching { return ZonedDateTime.parse(text).toInstant().toEpochMilli() }
        runCatching {
            return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        runCatching {
            val time = LocalTime.parse(text)
            var candidate = LocalDate.now().atTime(time).atZone(ZoneId.systemDefault())
            if (candidate.toInstant().toEpochMilli() <= System.currentTimeMillis()) {
                candidate = candidate.plusDays(1)
            }
            return candidate.toInstant().toEpochMilli()
        }
        throw IllegalArgumentException("Could not read the time \"$value\". Use ISO-8601 like 2026-11-02T15:30.")
    }

    private fun describeTime(epochMillis: Long): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm"))

    private fun currentTime(): String {
        val now = ZonedDateTime.now()
        return buildJsonObject {
            put("iso", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            put("date", now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")))
            put("time", now.format(DateTimeFormatter.ofPattern("HH:mm")))
            put("timezone", TimeZone.getDefault().id)
            put("epochMillis", System.currentTimeMillis())
        }.toString()
    }

    private suspend fun openUrl(url: String): String = withContext(Dispatchers.Main) {
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") {
            throw IllegalArgumentException("Only http(s) links can be opened.")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        buildJsonObject { put("ok", true); put("opened", url) }.toString()
    }

    private suspend fun shareText(text: String, title: String): String = withContext(Dispatchers.Main) {
        if (text.isBlank()) throw IllegalArgumentException("Nothing to share.")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, title.ifBlank { "Share with" })
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        buildJsonObject { put("ok", true) }.toString()
    }

    private suspend fun setAlarm(args: JsonObject): String = withContext(Dispatchers.Main) {
        val hour = args.int("hour").coerceIn(0, 23)
        val minute = args.int("minute").coerceIn(0, 59)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, args.text("label").ifBlank { "isha" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        buildJsonObject {
            put("ok", true)
            put("hour", hour)
            put("minute", minute)
            put("note", "The clock app is open for the user to confirm.")
        }.toString()
    }

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.number(key: String): Double {
        val primitive = this[key] as? JsonPrimitive ?: return 0.0
        return primitive.doubleOrNull
            ?: primitive.contentOrNull?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
            ?: 0.0
    }

    private fun numberProperty(description: String): JsonObject = buildJsonObject {
        put("type", "number")
        put("description", description)
    }

    private fun enumProperty(description: String, values: List<String>): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
        put("enum", buildJsonArray { values.forEach { add(it) } })
    }

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun stringProperty(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun intProperty(description: String): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    private fun objectSchema(properties: JsonObject, required: List<String>): JsonObject =
        buildJsonObject {
            put("type", "object")
            put("properties", properties)
            put("required", buildJsonArray { required.forEach { add(it) } })
        }
}
