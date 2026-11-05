package dev.jasper.ishav2.widget

import dev.jasper.ishav2.data.BudgetData
import dev.jasper.ishav2.data.BudgetStore
import dev.jasper.ishav2.data.Deadline
import dev.jasper.ishav2.data.DeadlineGroup
import dev.jasper.ishav2.data.DeadlineStore
import dev.jasper.ishav2.data.formatMoney
import java.time.Instant
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the home-screen widget shows, worked out from data already on the phone. */
data class WidgetSummary(
    val dueTitle: String,
    val dueDetail: String,
    /** Unfinished items whose due time has passed. */
    val overdue: Int,
    val budgetValue: String,
    val budgetDetail: String,
    val overBudget: Boolean
)

fun widgetSummary(deadlines: List<Deadline>, budget: BudgetData, now: ZonedDateTime): WidgetSummary {
    val open = deadlines.filterNot { it.isDone }
    val overdue = open.count { DeadlineStore.groupOf(it, now) == DeadlineGroup.OVERDUE }
    val next = open.filter { it.dueAt >= now.toInstant().toEpochMilli() }.minByOrNull { it.dueAt }

    val (dueTitle, dueDetail) = when {
        next != null -> next.title to listOf(whenDue(next, now), next.course)
            .filter { it.isNotBlank() }.joinToString(" · ")
        overdue > 0 -> "Nothing else due" to "Catch up on what's overdue"
        else -> "Nothing due" to "You're all caught up"
    }

    val month = BudgetStore.summarize(budget, YearMonth.from(now))
    val left = month.leftInBudget
    val (value, detail, over) = when {
        left != null && left < 0 -> Triple(formatMoney(budget.currency, -left), "over budget this month", true)
        left != null -> Triple(formatMoney(budget.currency, left), "left this month", false)
        else -> Triple(formatMoney(budget.currency, month.expense), "spent this month", false)
    }
    return WidgetSummary(dueTitle, dueDetail, overdue, value, detail, over)
}

/** "Today 11:59 PM", "Tomorrow", "Fri 3:00 PM" within a week, otherwise "Nov 20". */
internal fun whenDue(item: Deadline, now: ZonedDateTime): String {
    val due = ZonedDateTime.ofInstant(Instant.ofEpochMilli(item.dueAt), now.zone)
    val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), due.toLocalDate())
    val day = when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days in 2..6 -> due.format(DateTimeFormatter.ofPattern("EEE", Locale.US))
        else -> due.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
    }
    val showTime = item.hasTime && days <= 6
    return if (showTime) "$day ${due.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))}" else day
}
