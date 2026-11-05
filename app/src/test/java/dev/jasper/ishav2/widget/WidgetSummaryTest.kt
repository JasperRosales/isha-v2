package dev.jasper.ishav2.widget

import dev.jasper.ishav2.data.BudgetCategories
import dev.jasper.ishav2.data.BudgetData
import dev.jasper.ishav2.data.Deadline
import dev.jasper.ishav2.data.Transaction
import dev.jasper.ishav2.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetSummaryTest {
    private val zone = ZoneId.systemDefault()
    // Thursday, Nov 5 2026, 10:00
    private val now = ZonedDateTime.of(2026, 11, 5, 10, 0, 0, 0, zone)
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun picksSoonestUnfinishedDeadline() {
        val items = listOf(
            Deadline(title = "Essay", dueAt = at(9, 23, 59), course = "English"),
            Deadline(title = "Lab report", dueAt = at(5, 23, 59), course = "Physics"),
            Deadline(title = "Done quiz", dueAt = at(5, 12), done = true),
            Deadline(title = "Late worksheet", dueAt = at(4, 8))
        )
        val s = widgetSummary(items, BudgetData(), now)
        assertEquals("Lab report", s.dueTitle)
        assertEquals("Today 11:59 PM · Physics", s.dueDetail)
        assertEquals(1, s.overdue)
    }

    @Test
    fun describesWhenDue() {
        assertEquals("Tomorrow 8:00 AM", whenDue(Deadline(title = "a", dueAt = at(6, 8)), now))
        assertEquals("Mon 9:00 AM", whenDue(Deadline(title = "a", dueAt = at(9, 9)), now))
        assertEquals("Nov 20", whenDue(Deadline(title = "a", dueAt = at(20, 9)), now))
        assertEquals("Tomorrow", whenDue(Deadline(title = "a", dueAt = at(6, 23, 59), hasTime = false), now))
    }

    @Test
    fun emptyDeadlines() {
        val s = widgetSummary(emptyList(), BudgetData(), now)
        assertEquals("Nothing due", s.dueTitle)
        assertEquals(0, s.overdue)
    }

    @Test
    fun budgetLeftWhenLimitSet() {
        val budget = BudgetData(
            transactions = listOf(
                Transaction(type = TxType.EXPENSE, amount = 150.0, category = "Food", at = at(3, 12)),
                // Last month: not counted.
                Transaction(
                    type = TxType.EXPENSE, amount = 999.0, category = "Food",
                    at = ZonedDateTime.of(2026, 10, 30, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
                )
            ),
            budgets = mapOf(BudgetCategories.TOTAL to 3000.0)
        )
        val s = widgetSummary(emptyList(), budget, now)
        assertEquals("₱2,850", s.budgetValue)
        assertEquals("left this month", s.budgetDetail)
        assertFalse(s.overBudget)
    }

    @Test
    fun overBudgetAndNoLimit() {
        val tx = listOf(Transaction(type = TxType.EXPENSE, amount = 500.0, category = "Food", at = at(2, 9)))
        val over = widgetSummary(emptyList(), BudgetData(transactions = tx, budgets = mapOf(BudgetCategories.TOTAL to 400.0)), now)
        assertEquals("₱100", over.budgetValue)
        assertTrue(over.overBudget)
        val noLimit = widgetSummary(emptyList(), BudgetData(transactions = tx), now)
        assertEquals("₱500", noLimit.budgetValue)
        assertEquals("spent this month", noLimit.budgetDetail)
    }
}
